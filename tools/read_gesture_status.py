"""Collect 12 seconds of only the running app's gesture diagnostics."""
import subprocess
ADB = r"C:\Users\rkmai\AppData\Local\Android\Sdk\platform-tools\adb.exe"
device = [ADB, "-s", "9625097008000EB"]
pid = subprocess.check_output(device + ["shell", "pidof", "org.wheelchair.blink"], text=True).strip()
if not pid:
    raise SystemExit("Blink Assist is not running")
process = subprocess.Popen(device + ["logcat", "--pid=" + pid, "-T", "1", "-s",
    "WheelchairContour:I", "WheelchairFace:I", "WheelchairBlink:I"], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
try:
    output, _ = process.communicate(timeout=12)
except subprocess.TimeoutExpired:
    process.terminate()
    output, _ = process.communicate(timeout=5)
print(output.decode("utf-8", errors="replace"))
