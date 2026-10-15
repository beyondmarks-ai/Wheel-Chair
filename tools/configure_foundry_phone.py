"""Install a function-scoped invocation key into this debug phone's private settings.
No Azure model keys enter the APK. No secrets are printed or stored on the computer.
"""
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

ADB = r"C:\Users\rkmai\AppData\Local\Android\Sdk\platform-tools\adb.exe"
SERIAL = "9625097008000EB"
PACKAGE = "org.wheelchair.blink"
AZ = shutil.which("az")

def key():
    value = json.loads(subprocess.check_output([AZ, "functionapp", "function", "keys", "set",
        "-g", "rg-wheelchair-prototype", "-n", "wheelchair-gestures-7bc2336b",
        "--function-name", "gesture", "--key-name", "vivo-trial", "-o", "json"], timeout=120))
    # CLI 2.87's typed keys-list command loses this response; use CLI ARM REST.
    url = "https://management.azure.com/subscriptions/617db570-27f6-43a2-8d98-7b067bc2336b/resourceGroups/rg-wheelchair-prototype/providers/Microsoft.Web/sites/wheelchair-gestures-7bc2336b/functions/gesture/listkeys?api-version=2022-03-01"
    value = json.loads(subprocess.check_output([AZ, "rest", "--method", "post", "--url", url], timeout=120))
    result = value.get("vivo-trial")
    if not isinstance(result, str) or len(result) < 20:
        raise RuntimeError("Function key unavailable")
    return result

def adb(*args, **kwargs):
    return subprocess.check_output([ADB, "-s", SERIAL, *args], timeout=30, **kwargs)

def main():
    invocation_key = key()
    adb("shell", "am", "force-stop", PACKAGE)
    root = ET.fromstring(adb("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/settings.xml"))
    for name, kind, value in [("foundryFunctionKey", "string", invocation_key),
                               ("useFoundryGestures", "boolean", "true"),
                               ("faceMessagesEnabled", "boolean", "true")]:
        for node in list(root):
            if node.get("name") == name:
                root.remove(node)
        node = ET.SubElement(root, kind, name=name)
        if kind == "boolean":
            node.set("value", value)
        else:
            node.text = value
    data = ET.tostring(root, encoding="utf-8", xml_declaration=True)
    # Fixed remote command; no credentials are interpolated into shell code or command lines.
    adb("shell", "run-as", PACKAGE, "sh", "-c", "'cat > shared_prefs/settings.xml'", input=data)
    check = ET.fromstring(adb("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/settings.xml"))
    assert check.find("./string[@name='foundryFunctionKey']").text == invocation_key
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    print("Function-scoped key verified in app-private settings; Azure trial selected. No secrets printed.")

if __name__ == "__main__":
    main()
