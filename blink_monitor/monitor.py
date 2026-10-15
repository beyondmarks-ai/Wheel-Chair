"""Local ESP32-CAM eye-gesture prototype using pretrained MediaPipe landmarks."""
import argparse
import asyncio
import json
from pathlib import Path
import threading
import time
import urllib.request

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

from gestures import BlinkGesture
from outbox import enqueue, flush, pending
from speaker import SpeakerLink

ROOT = Path(__file__).resolve().parent


class Camera:
    def __init__(self, url):
        self.url = url
        self.frame = None
        self.received = 0.0
        self.sequence = 0
        self.lock = threading.Lock()
        self.stop = threading.Event()
        self.error = "Waiting for camera"
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()

    def read(self):
        while not self.stop.is_set():
            try:
                with urllib.request.urlopen(self.url, timeout=2) as response:
                    buffer = bytearray()
                    while not self.stop.is_set():
                        # Consume available bytes without waiting to fill a block.
                        block = response.read1(8192)
                        if not block:
                            raise ConnectionError("Camera stream ended")
                        buffer.extend(block)
                        while True:
                            start = buffer.find(b"\xff\xd8")
                            end = buffer.find(b"\xff\xd9", start + 2) if start >= 0 else -1
                            if end < 0:
                                break
                            jpeg = bytes(buffer[start:end + 2])
                            del buffer[:end + 2]
                            frame = cv2.imdecode(np.frombuffer(jpeg, np.uint8), cv2.IMREAD_COLOR)
                            if frame is not None:
                                with self.lock:
                                    self.frame, self.received = frame, time.monotonic()
                                    self.sequence += 1
                                    self.error = ""
                        if len(buffer) > 2_000_000:
                            raise ValueError("No complete camera frame")
            except Exception as error:
                self.error = type(error).__name__
                self.stop.wait(1)

    def latest(self):
        with self.lock:
            return self.sequence, self.received, self.frame


def run(args):
    options = vision.FaceLandmarkerOptions(
        base_options=python.BaseOptions(model_asset_path=str(ROOT / "models" / "face_landmarker.task")),
        running_mode=vision.RunningMode.VIDEO, num_faces=2,
        min_face_detection_confidence=0.6, min_face_presence_confidence=0.6,
        min_tracking_confidence=0.6, output_face_blendshapes=True)
    gesture = BlinkGesture(mode=args.gesture, hold_seconds=args.hold_seconds)
    speaker = SpeakerLink(args.speaker_port, args.speaker_volume) if args.speaker_port else None
    camera = Camera(args.url)
    started = time.monotonic()
    previous_sequence, timestamp_ms = -1, 0
    previous_center = None
    next_report = 0.0
    status, left, right, face_count = "Waiting for camera", None, None, 0
    frames, face_frames, events = 0, 0, 0
    last_event = "No test gesture yet"
    last_event_time = 0.0
    last_pose = None
    max_left, max_right = 0.0, 0.0
    next_frame_log = 0.0
    last_blink_count = 0
    sample_log = (ROOT / "local" / "eye-samples.jsonl").open("w", encoding="utf-8")
    try:
        with vision.FaceLandmarker.create_from_options(options) as model:
            while not args.duration or time.monotonic() - started < args.duration:
                now = time.monotonic()
                if speaker:
                    speaker.poll()
                sequence, received, frame = camera.latest()
                fresh = frame is not None and now - received < 0.5
                if not fresh:
                    gesture.update(now, valid=False)
                    previous_center = None
                    status = "Camera unavailable - gesture cancelled"
                    left = right = None
                    face_count = 0
                elif sequence != previous_sequence:
                    previous_sequence = sequence
                    frames += 1
                    timestamp_ms = max(timestamp_ms + 1, int((now - started) * 1000))
                    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                    result = model.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), timestamp_ms)
                    face_count = len(result.face_landmarks)
                    valid, left, right = False, None, None
                    status = "Show exactly one face to camera"
                    if face_count == 1 and result.face_blendshapes:
                        points = result.face_landmarks[0]
                        scores = {item.category_name: item.score for item in result.face_blendshapes[0]}
                        left, right = scores.get("eyeBlinkLeft"), scores.get("eyeBlinkRight")
                        xs, ys = [p.x for p in points], [p.y for p in points]
                        center = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
                        face_width = (max(xs) - min(xs)) * frame.shape[1]
                        eye_span = abs(points[33].x - points[263].x) * frame.shape[1]
                        jumped = previous_center is not None and np.linalg.norm(np.subtract(center, previous_center)) > 0.15
                        valid = left is not None and right is not None and face_width >= 65 and eye_span >= 25 and not jumped
                        previous_center = center
                        last_pose = points
                        status = "Tracking eyes" if valid else "Move closer; keep face steady and eyes visible"
                        if valid:
                            face_frames += 1
                            max_left, max_right = max(max_left, left), max(max_right, right)
                    else:
                        previous_center, last_pose = None, None
                    event = gesture.update(received, left, right, valid)
                    if now >= next_frame_log:
                        next_frame_log = now + 0.05
                        sample_log.write(json.dumps({"seconds": round(received - started, 3),
                            "left": left, "right": right, "valid": valid,
                            "state": gesture.state, "blinks": len(gesture.blinks),
                            "feedback": gesture.feedback}) + "\n")
                    if gesture.completed_blinks != last_blink_count:
                        last_blink_count = gesture.completed_blinks
                        print(f"BLINK COUNTED {len(gesture.blinks)}/3 duration={gesture.closed_duration:.3f}s", flush=True)
                    if event:
                        saved = enqueue("blink_sos_test", {**event, "source": "camera_eye_landmarks"})
                        print("TEST GESTURE QUEUED " + json.dumps(saved), flush=True)
                        if speaker:
                            speaker.trigger(saved["eventId"])
                        events += 1
                        last_event = speaker.status if speaker else "TEST gesture saved - no caregiver notified"
                        last_event_time = now
                    if args.preview and last_pose and valid:
                        frame = frame.copy()
                        for index in (33, 133, 159, 145, 362, 263, 386, 374):
                            point = last_pose[index]
                            cv2.circle(frame, (int(point.x * frame.shape[1]), int(point.y * frame.shape[0])), 1, (0, 255, 0), -1)
                if now >= next_report:
                    next_report = now + 1
                    report = {"status": status, "state": gesture.state, "faces": face_count,
                              "eyeBlinkLeft": None if left is None else round(left, 3),
                              "eyeBlinkRight": None if right is None else round(right, 3),
                              "processedFrames": frames, "completedBlinks": gesture.completed_blinks,
                              "sequenceBlinks": len(gesture.blinks), "feedback": gesture.feedback,
                              "testEvents": events,
                              "speakerStatus": speaker.status if speaker else "Disabled",
                              "speakerCommands": speaker.requests if speaker else 0,
                              "speakerPlaybackRequests": speaker.playback_requests if speaker else 0}
                    print(json.dumps(report), flush=True)
                    (ROOT / "local" / "status.json").write_text(json.dumps(report), encoding="utf-8")
                if args.preview:
                    canvas = np.zeros((800, 800, 3), dtype=np.uint8)
                    if frame is not None:
                        canvas[:600] = cv2.resize(frame, (800, 600))
                    phase = {"WAIT_OPEN": "Look at camera with eyes open", "READY": "Ready",
                             "CLOSED": "Eyes closed", "CONFIRM_OPEN": "Keep eyes open to confirm",
                             "COOLDOWN": "Waiting before next gesture"}[gesture.state]
                    progress = f"Blinks: {len(gesture.blinks)}/3" if args.gesture == "triple_blink" else "Long eye closure"
                    lines = ["EYE GESTURE PROTOTYPE - Q to stop", status,
                             f"{phase} | {progress} | Tests: {events}",
                             speaker.status if speaker and speaker.requests else gesture.feedback,
                             last_event if now - last_event_time < 10 else f"Close both eyes {args.hold_seconds:g} seconds, then reopen" if args.gesture == "long_close" else "Blink both eyes 3 times, then keep them open"]
                    for index, text in enumerate(lines):
                        cv2.putText(canvas, text, (12, 627 + index * 32), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 220, 255), 1, cv2.LINE_AA)
                    cv2.imshow("Wheelchair blink test", canvas)
                    if cv2.waitKey(1) & 0xff in (ord("q"), 27):
                        break
                else:
                    time.sleep(0.01)
    finally:
        if speaker:
            speaker.close()
        sample_log.close()
        camera.stop.set()
        camera.thread.join(timeout=3)
        if args.preview:
            cv2.destroyAllWindows()
        summary = {"processedFrames": frames, "usableFaceFrames": face_frames, "testEvents": events,
                   "completedBlinks": gesture.completed_blinks,
                   "maxEyeBlinkLeft": round(max_left, 3), "maxEyeBlinkRight": round(max_right, 3),
                   "speakerCommands": speaker.requests if speaker else 0,
                   "speakerPlaybackRequests": speaker.playback_requests if speaker else 0,
                   "pendingAzureEvents": len(pending())}
        (ROOT / "local" / "last-test.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
        print("TEST SUMMARY " + json.dumps(summary), flush=True)
    if args.flush:
        asyncio.run(flush())


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://192.168.4.1:81/stream")
    parser.add_argument("--gesture", choices=("long_close", "triple_blink"), default="triple_blink")
    parser.add_argument("--hold-seconds", type=float, default=2.0)
    parser.add_argument("--duration", type=float, default=0, help="Seconds; zero means run until stopped")
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--flush", action="store_true", help="Upload queued test events after capture stops")
    parser.add_argument("--speaker-port", help="ESP32 USB serial port, for example COM9")
    parser.add_argument("--speaker-volume", type=int, default=30)
    args = parser.parse_args()
    if not 1.5 <= args.hold_seconds <= 4:
        parser.error("Hold duration must be between 1.5 and 4 seconds")
    if not 1 <= args.speaker_volume <= 30:
        parser.error("Speaker volume must be 1-30")
    (ROOT / "local").mkdir(exist_ok=True)
    run(args)
