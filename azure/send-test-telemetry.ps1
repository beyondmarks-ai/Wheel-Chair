param(
    [string]$HubName = "wheelchair-iot-7bc2336b",
    [string]$DeviceId = "wheelchair-esp32-01",
    [string]$PayloadPath = "$PSScriptRoot\sample-telemetry.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $PayloadPath)) {
    throw "Telemetry payload not found: $PayloadPath"
}

az iot device send-d2c-message `
    --hub-name $HubName `
    --device-id $DeviceId `
    --data-file-path $PayloadPath `
    --properties '$.ct=application/json;$.ce=utf-8' `
    --output none

if ($LASTEXITCODE -ne 0) {
    throw "Azure IoT telemetry send failed with exit code $LASTEXITCODE."
}

Write-Host "Telemetry sent to $HubName from $DeviceId."
