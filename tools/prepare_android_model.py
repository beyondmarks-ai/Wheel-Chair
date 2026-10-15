"""Fetch the versioned MediaPipe face model and verify it before packaging."""
import hashlib
from pathlib import Path
import urllib.request

URL = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
SHA256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
DEST = Path(__file__).resolve().parents[1] / "android_phone/app/src/main/assets/face_landmarker.task"


def main():
    if DEST.exists() and hashlib.sha256(DEST.read_bytes()).hexdigest() == SHA256:
        print("MediaPipe face model verified.")
        return
    with urllib.request.urlopen(URL, timeout=90) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise RuntimeError("Downloaded model checksum mismatch; model was not installed")
    DEST.parent.mkdir(parents=True, exist_ok=True)
    pending = DEST.with_suffix(".download")
    pending.write_bytes(data)
    pending.replace(DEST)
    print("MediaPipe face model downloaded and verified.")


if __name__ == "__main__":
    main()
