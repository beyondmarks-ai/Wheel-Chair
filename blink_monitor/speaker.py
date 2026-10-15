"""Immediate, non-queued speaker commands over the ESP32's USB serial port."""
from collections import deque
import json
import time
import serial


class SpeakerLink:
    def __init__(self, port, volume=30, connection=None, clock=time.monotonic):
        if not 1 <= volume <= 30:
            raise ValueError("Speaker volume must be 1-30")
        self.clock, self.volume = clock, volume
        self.connection = connection
        if connection is None:
            self.connection = serial.Serial()
            self.connection.port = port
            self.connection.baudrate = 115200
            self.connection.timeout = 0
            self.connection.write_timeout = 0.2
            self.connection.dtr = False
            self.connection.rts = False
            self.connection.open()
        self.buffer = bytearray()
        self.active = False
        self.deadline = 0.0
        self.closed = False
        self.seen = deque(maxlen=256)
        self.requests = 0
        self.playback_requests = 0
        self.event_id = None
        self.status = f"Controller connected on {port}; waiting for three blinks"

    def trigger(self, event_id):
        if event_id in self.seen:
            return False
        self.seen.append(event_id)
        if self.closed:
            self.status = "Speaker link disconnected; test not sent"
            return False
        if self.active:
            self.status = "Speaker busy; new gesture not queued"
            return False
        try:
            command = f"AUDIO TEST {self.volume}\n".encode("ascii")
            if self.connection.write(command) != len(command):
                raise OSError("Incomplete serial write")
        except (OSError, serial.SerialException):
            self.close()
            self.status = "Speaker serial write failed; no automatic retry"
            return False
        self.active = True
        self.event_id = event_id
        self.deadline = self.clock() + 10
        self.requests += 1
        self.status = "Three blinks: speaker command sent"
        print("SPEAKER COMMAND " + json.dumps({"eventId": event_id, "volume": self.volume}), flush=True)
        return True

    def poll(self):
        if self.closed:
            return
        try:
            self.buffer.extend(self.connection.read(min(self.connection.in_waiting, 8192)))
        except (OSError, serial.SerialException):
            self.close()
            self.status = "Controller disconnected; no automatic replay"
            return
        while b"\n" in self.buffer:
            raw, _, rest = self.buffer.partition(b"\n")
            self.buffer = bytearray(rest)
            line = raw.decode("utf-8", errors="replace").strip()
            if line.startswith("AUDIO "):
                print("SPEAKER REPLY " + json.dumps({"eventId": self.event_id, "message": line}), flush=True)
                if self.active and line.startswith("AUDIO playback requested:"):
                    self.playback_requests += 1
                    self.status = f"Emergency playback requested ({self.volume}/30)"
                elif "no expected DFPlayer reply" in line or "playback cancelled" in line or "module error=" in line:
                    self.active = False
                    self.status = "Speaker error; check DFPlayer wiring/card"
                elif "playback finished" in line:
                    self.active = False
                    self.status = "DFPlayer reports playback finished"
                elif "stopped after 5s" in line or line == "AUDIO test stopped":
                    self.active = False
                    self.status = "Speaker test ended; ready for next gesture"
                elif "test already running" in line:
                    self.status = "Controller reports speaker busy"
            elif line.startswith("rst:") and self.active:
                self.active = False
                self.status = "Controller restarted; previous command not replayed"
        if len(self.buffer) > 4096:
            self.buffer.clear()
        if self.active and self.clock() >= self.deadline:
            self.active = False
            self.status = "Speaker response timed out; check controller"

    def close(self):
        if self.closed:
            return
        try:
            if self.active:
                self.connection.write(b"AUDIO STOP\n")
        except (OSError, serial.SerialException):
            pass
        finally:
            self.connection.close()
            self.closed = True
            self.active = False
