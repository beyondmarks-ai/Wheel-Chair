"""Bounded deployed-proxy test; uses the phone's private invocation key in memory."""
import base64
import json
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Users\rkmai\AppData\Local\Android\Sdk\platform-tools\adb.exe"
raw = subprocess.check_output([ADB, "-s", "9625097008000EB", "exec-out", "run-as",
    "org.wheelchair.blink", "cat", "shared_prefs/settings.xml"])
key = ET.fromstring(raw).find("./string[@name='foundryFunctionKey']").text
image = base64.b64encode(Path("tmp/foundry/mouth-trial.jpg").read_bytes()).decode()
request = urllib.request.Request("https://wheelchair-gestures-7bc2336b.azurewebsites.net/api/gesture",
    data=json.dumps({"neutral": image, "current": image}).encode(),
    headers={"Content-Type": "application/json", "x-functions-key": key})
started = time.monotonic()
with urllib.request.urlopen(request, timeout=25) as response:
    result = json.load(response)
report = {"test": "identical reference and current: no gesture change", "latency_ms": round((time.monotonic()-started)*1000), "result": result}
Path("tmp/foundry/proxy-test.json").write_text(json.dumps(report, indent=2))
print(json.dumps(report))
