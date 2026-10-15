param(
    [string]$HubName = "wheelchair-iot-7bc2336b",
    [string]$DeviceId = "wheelchair-esp32-01",
    [int]$TimeoutSeconds = 0
)

$ErrorActionPreference = "Stop"

az iot hub monitor-events `
    --hub-name $HubName `
    --device-id $DeviceId `
    --timeout $TimeoutSeconds `
    --properties anno sys

if ($LASTEXITCODE -ne 0) {
    throw "Azure IoT event monitor failed with exit code $LASTEXITCODE."
}
