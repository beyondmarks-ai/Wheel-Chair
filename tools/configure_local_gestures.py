"""Select the local contour tracker on the connected test phone; preserve other settings."""
import subprocess
import xml.etree.ElementTree as ET

ADB = r"C:\Users\rkmai\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "9625097008000EB"
PACKAGE = "org.wheelchair.blink"
def adb(*args, **kwargs):
    return subprocess.check_output([ADB, "-s", DEVICE, *args], timeout=30, **kwargs)

adb("shell", "am", "force-stop", PACKAGE)
root = ET.fromstring(adb("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/settings.xml"))
for name, value in [("useFoundryGestures", "false"), ("useMlKitContours", "true"),
                    ("faceMessagesEnabled", "true"), ("quickFaceMessages", "true")]:
    for node in list(root):
        if node.get("name") == name:
            root.remove(node)
    ET.SubElement(root, "boolean", name=name, value=value)
adb("shell", "run-as", PACKAGE, "sh", "-c", "'cat > shared_prefs/settings.xml'",
    input=ET.tostring(root, encoding="utf-8", xml_declaration=True))
adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
print("Local ML Kit selected, quick confirmation enabled, Azure inference off. Other settings preserved.")
