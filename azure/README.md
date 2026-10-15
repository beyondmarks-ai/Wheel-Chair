# Azure IoT foundation

The paralysis-wheelchair prototype uses an Azure IoT Hub for secure telemetry ingestion.
Motor control, obstacle stopping, emergency stop, and other safety functions must remain
on the ESP32 so they continue working without internet access.

## Provisioned resources

| Resource | Value |
| --- | --- |
| Resource group | `rg-wheelchair-prototype` |
| Region | Central India |
| IoT Hub | `wheelchair-iot-7bc2336b` |
| Tier | F1 Free |
| Device identity | `wheelchair-esp32-01` |
| IoT endpoint | `wheelchair-iot-7bc2336b.azure-devices.net` |

The IoT Hub requires TLS 1.2. The device identity uses symmetric-key authentication.
Do not commit its connection string or key.

## Test with Azure CLI

Open one PowerShell window and monitor incoming events:

```powershell
.\azure\monitor-telemetry.ps1
```

Open another PowerShell window and send the sample wheelchair packet:

```powershell
.\azure\send-test-telemetry.ps1
```

The live ESP32 firmware is in `azure_iot_live/`. It publishes DHT11 temperature and
humidity, MAX30102 optical readings and calculated vitals when valid, and MPU-family
accelerometer readings every five seconds. Ultrasonic distance remains disabled until
the sensor's missing echo is corrected. These prototype values are not medical-grade
measurements.

## Recreate the cloud resources

The provisioning script is idempotent: it keeps existing resources and creates only
missing ones.

```powershell
.\azure\provision.ps1
```

## Next integration step

Connect the ESP32 to Wi-Fi and publish the same JSON schema over MQTT/TLS. Keep the
device key in an untracked `azure/device-config.h` file or use device provisioning for
larger deployments. Raspberry Pi camera processing can remain local and send only
alerts or derived results to Azure.
