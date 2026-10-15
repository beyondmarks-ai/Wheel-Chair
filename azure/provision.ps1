param(
    [string]$ResourceGroup = "rg-wheelchair-prototype",
    [string]$Location = "centralindia",
    [string]$HubName = "wheelchair-iot-7bc2336b",
    [string]$DeviceId = "wheelchair-esp32-01"
)

$ErrorActionPreference = "Stop"

az account show --output none
if ($LASTEXITCODE -ne 0) {
    throw "Azure CLI is not signed in. Run az login first."
}

az provider register --namespace Microsoft.Devices --wait
if ($LASTEXITCODE -ne 0) {
    throw "Microsoft.Devices provider registration failed."
}

az group show --name $ResourceGroup --output none 2>$null
if ($LASTEXITCODE -ne 0) {
    az group create `
        --name $ResourceGroup `
        --location $Location `
        --tags project=paralysis-wheelchair environment=prototype managedBy=azure-cli `
        --output none
}

az iot hub show --name $HubName --resource-group $ResourceGroup --output none 2>$null
if ($LASTEXITCODE -ne 0) {
    az iot hub create `
        --name $HubName `
        --resource-group $ResourceGroup `
        --location $Location `
        --sku F1 `
        --partition-count 2 `
        --tags project=paralysis-wheelchair environment=prototype `
        --output none
}

az iot hub device-identity show `
    --hub-name $HubName `
    --device-id $DeviceId `
    --resource-group $ResourceGroup `
    --output none 2>$null
if ($LASTEXITCODE -ne 0) {
    az iot hub device-identity create `
        --hub-name $HubName `
        --device-id $DeviceId `
        --resource-group $ResourceGroup `
        --output none
}

Write-Host "Azure IoT resources are ready."
Write-Host "Hub: $HubName"
Write-Host "Device: $DeviceId"
