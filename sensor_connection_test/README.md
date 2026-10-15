# ESP32 sensor connection test

Open `sensor_connection_test.ino` in Arduino IDE. Select the matching ESP32 board
(a typical ESP32-WROOM development board can use **ESP32 Dev Module**) and the
current USB serial port. The last Windows check detected **COM9**, not COM8.

## Before uploading

Disconnect wheelchair motor power. Uploading replaces the controller's existing
program; this sketch does not preserve its wheelchair functions. GPIO assignments
below are assumptions, not connections determined from the photo. Verify or edit
them before running: output on a wrongly assigned GPIO could affect another module.

| Signal | Assumed ESP32 GPIO |
| --- | --- |
| MAX30102 and MPU-6050 SDA | 21 |
| MAX30102 and MPU-6050 SCL | 22 |
| DHT data | 4 |
| HC-SR04 Trigger (user confirmed) | 5 |
| HC-SR04 Echo (user confirmed) | 18 |

All modules need a common ground and the correct supply for their specific
breakout board. I2C pull-ups must go to 3.3 V. A bare DHT usually needs a
4.7–10 kOhm data pull-up to 3.3 V; modules may already include one.

A standard HC-SR04 uses a 5 V supply and its Echo output must be reduced to
3.3 V before reaching the ESP32. For example: Echo → 1 kOhm → GPIO18, with
2 kOhm from GPIO18 to ground. Do not connect its 5 V Echo directly to the ESP32.

## Run

1. Install **DHT sensor library by Adafruit**, including its **Adafruit Unified
   Sensor** dependency, using Arduino IDE's Library Manager. `Wire` comes with
   the ESP32 board package.
2. Set the GPIO constants at the top of the sketch to match the wiring.
   The default sensor type is DHT11; change `DHT_TYPE` if yours is DHT22.
3. Upload, then open Serial Monitor at **115200 baud**.
4. Place a flat object about 10–100 cm in front of the ultrasonic sensor.

The sketch scans I2C once at startup and checks all four sensors every three
seconds. MPU-6050 and MAX30102 checks read identification registers. The MAX ID
0x15 is shared by some MAX3010x variants and cannot distinguish every model.
DHT checks require a valid temperature/humidity response, and HC-SR04 checks
require an echo pulse. Missing responses do not conclusively prove disconnection:
wrong pins, bad power, a wrong DHT type or an unsuitable ultrasonic target can
cause the same result. These checks do not validate pulse/SpO2 accuracy, motion
readings, calibration, or wheelchair operation.

CLI build (ESP32 board package and libraries must already be installed):

```powershell
arduino-cli compile --fqbn esp32:esp32:esp32 D:\Wheelchair\sensor_connection_test
```
