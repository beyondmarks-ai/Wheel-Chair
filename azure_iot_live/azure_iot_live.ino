// Copyright (c) Microsoft Corporation. All rights reserved.
// SPDX-License-Identifier: MIT

/*
 * This is an Arduino-based Azure IoT Hub sample for ESPRESSIF ESP32 boards.
 * It uses our Azure Embedded SDK for C to help interact with Azure IoT.
 * For reference, please visit https://github.com/azure/azure-sdk-for-c.
 *
 * To connect and work with Azure IoT Hub you need an MQTT client, connecting, subscribing
 * and publishing to specific topics to use the messaging features of the hub.
 * Our azure-sdk-for-c is an MQTT client support library, helping composing and parsing the
 * MQTT topic names and messages exchanged with the Azure IoT Hub.
 *
 * This sample performs the following tasks:
 * - Synchronize the device clock with a NTP server;
 * - Initialize our "az_iot_hub_client" (struct for data, part of our azure-sdk-for-c);
 * - Initialize the MQTT client (here we use ESPRESSIF's esp_mqtt_client, which also handle the tcp
 * connection and TLS);
 * - Connect the MQTT client (using server-certificate validation, SAS-tokens for client
 * authentication);
 * - Periodically send telemetry data to the Azure IoT Hub.
 *
 * To properly connect to your Azure IoT Hub, please fill the information in the `iot_configs.h`
 * file.
 */

// C99 libraries
#include <cstdlib>
#include <string.h>
#include <time.h>
#include <atomic>

// Libraries for MQTT client and WiFi connection
#include <WiFi.h>
#include <mqtt_client.h>
#include <Wire.h>
#include <DHT.h>
#include <MAX30105.h>
#include <spo2_algorithm.h>
#include <ArduinoJson.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

// Azure IoT SDK for C includes
#include <az_core.h>
#include <az_iot.h>
#include <azure_ca.h>

// Additional sample headers
#include "AzIoTSasToken.h"
#include "SerialLogger.h"
#include "iot_configs.h"
#include "UltrasonicSensor.h"
#include "DfPlayerAudio.h"
#include "PhoneSpeaker.h"
#include "MotionDetector.h"
#include <Preferences.h>

// When developing for your own Arduino-based platform,
// please follow the format '(ard;<platform>)'.
#define AZURE_SDK_CLIENT_USER_AGENT "c%2F" AZ_SDK_VERSION_STRING "(ard;esp32)"

// Utility macros and defines
#define sizeofarray(a) (sizeof(a) / sizeof(a[0]))
#define NTP_SERVERS "pool.ntp.org", "time.nist.gov"
#define MQTT_QOS1 1
#define DO_NOT_RETAIN_MSG 0
#define SAS_TOKEN_DURATION_IN_MINUTES 60
#define UNIX_TIME_NOV_13_2017 1510592825

#define PST_TIME_ZONE -8
#define PST_TIME_ZONE_DAYLIGHT_SAVINGS_DIFF 1

#define GMT_OFFSET_SECS (PST_TIME_ZONE * 3600)
#define GMT_OFFSET_SECS_DST ((PST_TIME_ZONE + PST_TIME_ZONE_DAYLIGHT_SAVINGS_DIFF) * 3600)

// Translate iot_configs.h defines into variables used by the sample
static const char* ssid = IOT_CONFIG_WIFI_SSID;
static const char* password = IOT_CONFIG_WIFI_PASSWORD;
static const char* host = IOT_CONFIG_IOTHUB_FQDN;
static const char* mqtt_broker_uri = "mqtts://" IOT_CONFIG_IOTHUB_FQDN;
static const char* device_id = IOT_CONFIG_DEVICE_ID;
static const int mqtt_port = AZ_IOT_DEFAULT_MQTT_CONNECT_PORT;

// Memory allocated for the sample's variables and structures.
static esp_mqtt_client_handle_t mqtt_client;
static az_iot_hub_client client;

static char mqtt_client_id[128];
static char mqtt_username[256];
static char mqtt_password[200];
static uint8_t sas_signature_buffer[256];
static unsigned long next_telemetry_send_time_ms = 0;
static char telemetry_topic[192];
static uint32_t telemetry_send_count = 0;
static String telemetry_payload = "{}";
static std::atomic<bool> mqttConnected{false};

// Connected prototype sensors.
constexpr uint8_t SDA_PIN = 21;
constexpr uint8_t SCL_PIN = 22;
constexpr uint8_t DHT_PIN = 4;
constexpr uint8_t IMU_ADDRESS = 0x68;
constexpr uint32_t FINGER_IR_THRESHOLD = 10000;
#define DHT_TYPE DHT11

DHT dht(DHT_PIN, DHT_TYPE);
UltrasonicSensor ultrasonic;
HardwareSerial dfPlayerSerial(2);
DfPlayerAudio speaker(dfPlayerSerial);
PhoneSpeaker phoneSpeaker(speaker);
MotionDetector motionDetector;
static bool imuConfigured = false, imuReadingValid = false;
static bool motionEnabled = true, motionAudioReady = false;
static uint32_t nextImuReadMs = 0, lastImuReadMs = 0;
static uint8_t imuReadFailures = 0;
static float imuAx = 0, imuAy = 0, imuAz = 0, imuGx = 0, imuGy = 0, imuGz = 0;
static float ambientTemperatureC = NAN;
static float ambientHumidityPercent = NAN;
static uint32_t nextDhtReadMs = 2500;
static uint32_t lastDhtReadingMs = 0;
static bool dhtReadAttempted = false;
MAX30105 particleSensor;
hd44780_I2Cexp lcd;
static bool maxPresent = false;
static bool lcdReady = false;
static int lcdInitStatus = -1;
static uint8_t lcdVerifiedCharacters = 0;
static uint8_t imuWhoAmI = 0;
static uint32_t irBuffer[100] = {};
static uint32_t redBuffer[100] = {};
static size_t opticalSampleCount = 0;
static uint32_t latestIr = 0;
static uint32_t latestRed = 0;
static int32_t latestHeartRate = 0;
static int32_t latestSpo2 = 0;
static int8_t heartRateValid = 0;
static int8_t spo2Valid = 0;
static int32_t rawHeartRate = -1, rawSpo2 = -1;
static int32_t heartRateHistory[3] = {}, spo2History[3] = {};
static uint8_t heartRateHistoryCount = 0, spo2HistoryCount = 0;
static uint8_t opticalLedDrive = 60;
static uint32_t lastOpticalMs = 0;
static uint32_t lastResultMs = 0;
static uint32_t lastPollMs = 0;
static uint32_t sampleFaults = 0;
static uint32_t sampleTotal = 0;
static uint32_t windowStartMs = 0;
static uint32_t irRange = 0;
static uint8_t fifoOverflowRaw = 0;
static bool fingerPresent = false;
static bool opticalSaturated = false;
static unsigned long nextLcdUpdateMs = 0;
static String previousLcdLine1;
static String previousLcdLine2;

#define INCOMING_DATA_BUFFER_SIZE 128
static char incoming_data[INCOMING_DATA_BUFFER_SIZE];

static bool readI2cRegisters(uint8_t address, uint8_t reg, uint8_t* output, size_t length)
{
  Wire.beginTransmission(address);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(address, static_cast<uint8_t>(length)) != length) return false;
  for (size_t index = 0; index < length; ++index) output[index] = Wire.read();
  return true;
}

static bool writeImuRegister(uint8_t reg, uint8_t value)
{
  Wire.beginTransmission(IMU_ADDRESS); Wire.write(reg); Wire.write(value);
  if (Wire.endTransmission() != 0) return false;
  uint8_t actual = 0;
  return readI2cRegisters(IMU_ADDRESS, reg, &actual, 1) && actual == value;
}

static bool configureMotionSensor()
{
  imuReadingValid = false; motionDetector.reset(); imuWhoAmI = 0;
  if (!readI2cRegisters(IMU_ADDRESS, 0x75, &imuWhoAmI, 1)) return false;
  if (imuWhoAmI != 0x70 && imuWhoAmI != 0x71 && imuWhoAmI != 0x73 && imuWhoAmI != 0x68) return false;
  // MPU6500 (observed WHO_AM_I 0x70), compatible MPU9250/9255/6050 variants.
  // PLL clock, all axes awake, +/-2 g, +/-250 degrees/s, 20 Hz low-pass,
  // hardware samples at 100 Hz; main loop reads latest samples at 50 Hz.
  if (!writeImuRegister(0x6B, 1) || !writeImuRegister(0x6C, 0) ||
      !writeImuRegister(0x1A, 4) || !writeImuRegister(0x1B, 0) ||
      !writeImuRegister(0x1C, 0) || !writeImuRegister(0x19, 9)) return false;
  if (imuWhoAmI != 0x68 && !writeImuRegister(0x1D, 4)) return false;
  imuReadFailures = 0;
  Serial.printf("MOTION IMU configured id=0x%02X; keep still for 2 seconds\n", imuWhoAmI);
  return true;
}

static void setMotionAudioReady(bool ready)
{
  Preferences settings;
  bool saved = settings.begin("wheelchair", false);
  if (saved) { saved = settings.putBool("motionaudio", ready) == 1; settings.end(); }
  if (saved) motionAudioReady = ready;
  if (!motionAudioReady && speaker.motionActive()) speaker.stop();
  Serial.printf("MOTION audio ready=%d saved=%d; track=/mp3/0002.mp3\n", motionAudioReady, saved);
}

static void setFaceAudioReady(bool ready)
{
  Preferences settings;
  bool saved = settings.begin("wheelchair", false);
  if (saved) { saved = settings.putBool("faceaudio", ready) == 1; settings.end(); }
  if (saved) phoneSpeaker.setFaceAudioReady(ready);
  Serial.printf("FACE audio ready requested=%d saved=%d; tracks=/mp3/0003.mp3 through 0005.mp3\n", ready, saved);
}

static void collectMotionReading()
{
  uint32_t now = millis();
  if (int32_t(now - nextImuReadMs) < 0) return;
  nextImuReadMs = now + 20;
  if (!imuConfigured) {
    imuConfigured = configureMotionSensor();
    nextImuReadMs = millis() + (imuConfigured ? 100 : 2000);
    return;
  }
  uint8_t sample[14] = {};
  if (!readI2cRegisters(IMU_ADDRESS, 0x3B, sample, sizeof(sample))) {
    imuReadingValid = false; motionDetector.reset();
    if (++imuReadFailures >= 5) { imuConfigured = false; nextImuReadMs = millis() + 2000; }
    return;
  }
  imuReadFailures = 0;
  auto value = [&sample](uint8_t i) { return int16_t((uint16_t(sample[i]) << 8) | sample[i + 1]); };
  imuAx=value(0)/16384.0f; imuAy=value(2)/16384.0f; imuAz=value(4)/16384.0f;
  imuGx=value(8)/131.0f; imuGy=value(10)/131.0f; imuGz=value(12)/131.0f;
  imuReadingValid = (imuAx*imuAx + imuAy*imuAy + imuAz*imuAz) >= .01f;
  lastImuReadMs = millis();
  if (!motionEnabled) { motionDetector.reset(); return; }
  if (motionDetector.update(lastImuReadMs, imuAx,imuAy,imuAz,imuGx,imuGy,imuGz,imuReadingValid)) {
    Serial.printf("MOTION detected event=%lu linear_g=%.3f rotation_dps=%.1f\n",
        (unsigned long)motionDetector.events(), motionDetector.linearG(), motionDetector.rotationDps());
    if (!motionAudioReady) Serial.println("MOTION voice disabled: copy /mp3/0002.mp3, then send MOTION AUDIO READY");
    else if (!speaker.motion()) Serial.println("MOTION speaker busy; motion announcement skipped (no queue)");
  }
}

static void initializeSensors()
{
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  Wire.setTimeOut(25);
  dht.begin();
  ultrasonic.begin();
  speaker.begin();

  lcdInitStatus = lcd.begin(16, 2);
  lcdReady = lcdInitStatus == 0;
  if (lcdReady)
  {
    lcd.backlight();
    lcd.display();
    lcd.noCursor();
    lcd.noBlink();
    lcd.clear();
    // Read back both display-memory rows to distinguish a communication
    // problem from invisible text caused by contrast or panel power.
    const char* lcdTestRows[] = { "LCD test 01234567", "ABCDEFGHIJKLMNOP" };
    for (uint8_t row = 0; row < 2; ++row)
    {
      lcd.setCursor(0, row);
      lcd.print(lcdTestRows[row]);
    }
    for (uint8_t row = 0; row < 2; ++row)
    {
      lcd.setCursor(0, row);
      for (uint8_t col = 0; col < 16; ++col)
        if (lcd.read() == lcdTestRows[row][col]) ++lcdVerifiedCharacters;
    }
    Serial.printf("LCD init=%d RAM matched=%u/32\n", lcdInitStatus, lcdVerifiedCharacters);
    lcd.clear();
    lcd.print("Wheelchair IoT");
    lcd.setCursor(0, 1);
    lcd.print("Starting...");
  }
  else Logger.Error("LCD not detected");

  // The LCD backpack shares this bus and is most reliable at the standard 100 kHz rate.
  maxPresent = particleSensor.begin(Wire, I2C_SPEED_STANDARD);
  if (maxPresent)
  {
    // 4-sample averaging yields the 25 paired samples/s required by Maxim's algorithm.
    // Lower LED current and wider ADC range prevent finger-on clipping.
    particleSensor.setup(opticalLedDrive, 4, 2, 100, 411, 16384);
    Logger.Info("MAX30102 initialized");
  }
  else Logger.Error("MAX30102 not detected");

  imuConfigured = configureMotionSensor();
  nextImuReadMs = millis() + (imuConfigured ? 100 : 2000);
  Logger.Info("IMU WHO_AM_I: 0x" + String(imuWhoAmI, HEX));
}

static String fitLcdLine(String value)
{
  if (value.length() > 16) value.remove(16);
  while (value.length() < 16) value += ' ';
  return value;
}

static bool opticalUsable()
{
  return maxPresent && fingerPresent && !opticalSaturated &&
      millis() - lastOpticalMs < 500 && millis() - lastResultMs < 2500;
}

static bool usableHeartRate()
{
  return opticalUsable() && heartRateValid && latestHeartRate > 20 && latestHeartRate < 240;
}

static bool usableSpo2()
{
  return opticalUsable() && spo2Valid && latestSpo2 > 0 && latestSpo2 <= 100;
}

static bool usableAmbientReading()
{
  return dhtReadAttempted && !isnan(ambientTemperatureC) &&
      !isnan(ambientHumidityPercent) && millis() - lastDhtReadingMs < 6000;
}

static void collectAmbientReading()
{
  if (static_cast<int32_t>(millis() - nextDhtReadMs) < 0) return;
  nextDhtReadMs = millis() + 2500; // Respect the DHT library's 2-second minimum.
  dhtReadAttempted = true;
  ultrasonic.cancelPending();
  ambientHumidityPercent = dht.readHumidity();
  ambientTemperatureC = dht.readTemperature(); // Uses the same cached DHT frame.
  lastDhtReadingMs = millis();
  if (usableAmbientReading())
    Serial.printf("DHT room=%.1fC humidity=%.1f%%\n", ambientTemperatureC, ambientHumidityPercent);
  else Serial.println("DHT read failed; check sensor on GPIO4");
}

// Short, requested-only phone snapshot. Reuses exactly the LCD/Azure validity
// checks; no fake zero readings or stale last-heart-rate values are exported.
static void reportPhoneHealth(BluetoothSerial& link)
{
  bool ambient = usableAmbientReading();
  bool motionFresh = imuReadingValid && uint32_t(millis()-lastImuReadMs) <= 150;
  String line = "HEALTH 1 " + String(millis());
  line += " " + (usableHeartRate() ? String(latestHeartRate) : String("-"));
  line += " " + (usableSpo2() ? String(latestSpo2) : String("-"));
  line += " " + (ambient ? String(ambientTemperatureC,1) : String("-"));
  line += " " + (ambient ? String(ambientHumidityPercent,1) : String("-"));
  line += " " + (ultrasonic.usable() ? String(ultrasonic.distanceCm(),1) : String("-"));
  line += " " + (motionFresh ? String(motionDetector.moving() ? "1" : "0") : String("-"));
  line += " " + String(fingerPresent && uint32_t(millis()-lastOpticalMs)<500 ? "1" : "0");
  line += " " + String(motionDetector.events());
  link.println(line.c_str());
}

static void updateLcd()
{
  if (!lcdReady || static_cast<int32_t>(millis() - nextLcdUpdateMs) < 0) return;
  nextLcdUpdateMs = millis() + 500;

  String line1;
  String line2;
  if (!maxPresent)
  {
    line1 = "MAX30102 ERROR";
    line2 = "Check connection";
  }
  else if (millis() - lastOpticalMs >= 500)
  {
    line1 = "Sensor no data";
    line2 = "Check connection";
  }
  else if (opticalSaturated)
  {
    line1 = "Signal too high";
    line2 = "Adjusting light";
  }
  else if (!fingerPresent)
  {
    if (usableAmbientReading())
    {
      line1 = "Room: " + String(ambientTemperatureC, 1) + " C";
      line2 = "Humidity: " + String(ambientHumidityPercent, 0) + "%";
    }
    else
    {
      line1 = dhtReadAttempted ? "DHT read error" : "DHT starting...";
      line2 = dhtReadAttempted ? "Check DHT wiring" : "Please wait";
    }
  }
  else if (!usableHeartRate() && !usableSpo2())
  {
    line1 = "Finger detected";
    line2 = "Hold still...";
  }
  else
  {
    line1 = usableHeartRate() ? "HR: " + String(latestHeartRate) + " bpm" : "HR: reading...";
    line2 = usableSpo2() ? "SpO2: " + String(latestSpo2) + "%" : "SpO2: reading...";
  }

  line1 = fitLcdLine(line1);
  line2 = fitLcdLine(line2);
  if (line1 == previousLcdLine1 && line2 == previousLcdLine2) return;
  lcd.setCursor(0, 0);
  lcd.print(line1);
  lcd.setCursor(0, 1);
  lcd.print(line2);
  Serial.printf("LCD [%s] [%s]\n", line1.c_str(), line2.c_str());
  previousLcdLine1 = line1;
  previousLcdLine2 = line2;
}

static void resetOpticalWindow()
{
  opticalSampleCount = 0;
  heartRateValid = spo2Valid = 0;
  heartRateHistoryCount = spo2HistoryCount = 0;
  rawHeartRate = rawSpo2 = -1;
  lastResultMs = 0;
  irRange = 0;
}

// Require three consecutive plausible estimates to agree. A rejected window
// invalidates the displayed value immediately; never smooth artifacts into a BPM.
static bool consistentEstimate(int32_t candidate, bool valid, int32_t* history,
    uint8_t& count, bool isHeartRate, int32_t& result)
{
  if (!valid || (isHeartRate ? (candidate <= 20 || candidate >= 240)
                            : (candidate <= 0 || candidate > 100)))
  {
    count = 0;
    return false;
  }
  if (count == 3) { history[0] = history[1]; history[1] = history[2]; count = 2; }
  history[count++] = candidate;
  if (count < 3) return false;
  int32_t lo = min(history[0], min(history[1], history[2]));
  int32_t hi = max(history[0], max(history[1], history[2]));
  int32_t median = history[0] + history[1] + history[2] - lo - hi;
  int32_t tolerance = isHeartRate ? max(int32_t(8), median / 10) : 3;
  if (hi - lo > tolerance) return false;
  result = median;
  return true;
}

static void collectOpticalSamples()
{
  if (!maxPresent || millis() - lastPollMs < 5) return;
  uint32_t pollGap = millis() - lastPollMs;
  lastPollMs = millis();
  // Read the hardware FIFO directly: SparkFun's four-entry software buffer can
  // overwrite a backlog. Each six-byte read is one synchronized red/IR pair.
  uint8_t pointers[3];
  if (!readI2cRegisters(0x57, 0x04, pointers, 3))
  {
    ++sampleFaults;
    resetOpticalWindow();
    return;
  }
  fifoOverflowRaw = pointers[1] & 31;
  uint8_t pending = (pointers[0] - pointers[2]) & 31;
  // This board's OVF register remains 31 immediately after clearFIFO(), even
  // with WR=RD=0. Use a strict polling deadline (< FIFO capacity at 25 Hz)
  // and actual backlog instead; retain the raw register for diagnostics.
  if (pollGap > 500 || pending >= 24)
  {
    static uint32_t lastFaultLog = 0;
    if (millis() - lastFaultLog > 1000) {
      Serial.printf("FIFO fault WR=%u OVF=%u RD=%u gap=%lu\n", pointers[0], pointers[1], pointers[2], (unsigned long)pollGap);
      lastFaultLog = millis();
    }
    ++sampleFaults;
    particleSensor.clearFIFO();
    resetOpticalWindow();
    return;
  }
  while (pending--)
  {
    uint8_t pair[6];
    if (!readI2cRegisters(0x57, 0x07, pair, 6))
    {
      ++sampleFaults;
      particleSensor.clearFIFO();
      resetOpticalWindow();
      return;
    }
    latestRed = ((uint32_t(pair[0]) << 16) | (uint32_t(pair[1]) << 8) | pair[2]) & 0x3FFFF;
    latestIr = ((uint32_t(pair[3]) << 16) | (uint32_t(pair[4]) << 8) | pair[5]) & 0x3FFFF;
    lastOpticalMs = millis();
    ++sampleTotal;
    fingerPresent = latestIr > FINGER_IR_THRESHOLD;
    opticalSaturated = latestIr >= 250000 || latestRed >= 250000;
    if (opticalSaturated)
    {
      resetOpticalWindow();
      if (opticalLedDrive > 2)
      {
        opticalLedDrive = max(2, int(opticalLedDrive / 2));
        particleSensor.setPulseAmplitudeRed(opticalLedDrive);
        particleSensor.setPulseAmplitudeIR(opticalLedDrive);
        particleSensor.clearFIFO();
      }
      return;
    }
    if (!fingerPresent)
    {
      resetOpticalWindow();
      continue;
    }
    if (opticalSampleCount == 0) windowStartMs = millis();
    redBuffer[opticalSampleCount] = latestRed;
    irBuffer[opticalSampleCount++] = latestIr;
    if (opticalSampleCount == 100)
    {
      uint32_t lo = irBuffer[0], hi = irBuffer[0];
      for (size_t i = 1; i < 100; ++i) { lo = min(lo, irBuffer[i]); hi = max(hi, irBuffer[i]); }
      irRange = hi - lo;
      uint32_t duration = millis() - windowStartMs;
      // Reject flat signals and windows with disrupted sampling; never retain old vitals.
      heartRateValid = spo2Valid = 0;
      int8_t rawHeartRateValid = 0, rawSpo2Valid = 0;
      rawHeartRate = rawSpo2 = -1;
      if (duration >= 3700 && duration <= 4300 && irRange >= 50)
      {
        maxim_heart_rate_and_oxygen_saturation(
            irBuffer, 100, redBuffer, &rawSpo2, &rawSpo2Valid, &rawHeartRate, &rawHeartRateValid);
        lastResultMs = millis();
      }
      else if (duration < 3700 || duration > 4300) ++sampleFaults;
      heartRateValid = consistentEstimate(rawHeartRate, rawHeartRateValid,
          heartRateHistory, heartRateHistoryCount, true, latestHeartRate);
      spo2Valid = consistentEstimate(rawSpo2, rawSpo2Valid,
          spo2History, spo2HistoryCount, false, latestSpo2);
      // Four-second window, refreshed every second.
      memmove(irBuffer, irBuffer + 25, 75 * sizeof(uint32_t));
      memmove(redBuffer, redBuffer + 25, 75 * sizeof(uint32_t));
      opticalSampleCount = 75;
      windowStartMs += 1000;
    }
  }
}

static void serviceAudioCommands()
{
  phoneSpeaker.poll();
  static char command[32];
  static size_t used = 0;
  static bool overflow = false;
  for (int count = 0; count < 64 && Serial.available(); ++count)
  {
    char value = Serial.read();
    if (value == '\r') continue;
    if (value == '\n')
    {
      command[used] = '\0';
      if (!overflow && strcmp(command, "AUDIO TEST") == 0) speaker.test();
      else if (!overflow && strncmp(command, "AUDIO TEST ", 11) == 0)
      {
        char* end;
        long volume = strtol(command + 11, &end, 10);
        if (end != command + 11 && *end == '\0' && volume >= 1 && volume <= 30)
          speaker.test(static_cast<uint8_t>(volume));
        else Serial.println("AUDIO use AUDIO TEST 1 through AUDIO TEST 30");
      }
      else if (!overflow && strcmp(command, "AUDIO STOP") == 0) speaker.stop();
      else if (!overflow && strcmp(command, "AUDIO MOTION TEST") == 0) {
        if (!motionAudioReady) Serial.println("MOTION voice disabled: copy /mp3/0002.mp3, then send MOTION AUDIO READY");
        else if (!speaker.motion()) Serial.println("MOTION speaker busy; test skipped");
      }
      else if (!overflow && strcmp(command, "MOTION AUDIO READY") == 0) setMotionAudioReady(true);
      else if (!overflow && strcmp(command, "MOTION AUDIO OFF") == 0) setMotionAudioReady(false);
      else if (!overflow && strcmp(command, "FACE AUDIO READY") == 0) setFaceAudioReady(true);
      else if (!overflow && strcmp(command, "FACE AUDIO OFF") == 0) setFaceAudioReady(false);
      else if (!overflow && strcmp(command, "MOTION OFF") == 0) {
        motionEnabled=false; motionDetector.reset();
        if (speaker.motionActive()) speaker.stop();
        Serial.println("MOTION detection disabled");
      }
      else if (!overflow && strcmp(command, "MOTION ON") == 0) {
        motionEnabled=true; motionDetector.reset();
        Serial.println("MOTION detection enabled; keep still for 2 seconds");
      }
      used = 0; overflow = false;
    }
    else if (used < sizeof(command) - 1) command[used++] = value;
    else overflow = true;
  }
  speaker.poll();
}

static void serviceSensors()
{
  serviceAudioCommands();
  collectOpticalSamples();
  collectAmbientReading();
  collectOpticalSamples(); // Drain samples accumulated during the short DHT read.
  collectMotionReading();
  collectOpticalSamples();
  ultrasonic.poll();
  updateLcd();
  static uint32_t lastReport = 0, priorSamples = 0;
  static uint32_t lastLcdReport = 0;
  if (millis() - lastLcdReport >= 10000)
  {
    lastLcdReport = millis();
    Serial.printf("LCD init=%d RAM matched=%u/32 screen=[%s] [%s]\n",
        lcdInitStatus, lcdVerifiedCharacters, previousLcdLine1.c_str(), previousLcdLine2.c_str());
  }
  if (millis() - lastReport >= 1000)
  {
    Serial.printf("MOTION valid=%d calibrated=%d moving=%d enabled=%d audio_ready=%d ax=%.3f ay=%.3f az=%.3f gx=%.1f gy=%.1f gz=%.1f linear_g=%.3f rotation_dps=%.1f events=%lu\n",
        imuReadingValid && uint32_t(millis()-lastImuReadMs)<=150, motionDetector.calibrated(), motionDetector.moving(),
        motionEnabled, motionAudioReady, imuAx,imuAy,imuAz,imuGx,imuGy,imuGz,
        motionDetector.linearG(),motionDetector.rotationDps(),(unsigned long)motionDetector.events());
    Serial.printf("ULTRA trig=5 echo=18 valid=%d cm=%.1f pulse_us=%lu status=%s\n",
        ultrasonic.usable(), ultrasonic.usable() ? ultrasonic.distanceCm() : -1.0f,
        (unsigned long)ultrasonic.pulseUs(), ultrasonic.status());
    float rate = (sampleTotal - priorSamples) * 1000.0f / (millis() - lastReport);
    lastReport = millis(); priorSamples = sampleTotal;
    Serial.printf("OPT IR=%lu RED=%lu finger=%d clipped=%d LED=%u samples/s=%.1f range=%lu HR=%ld SpO2=%ld rawHR=%ld rawSpO2=%ld faults=%lu\n",
        (unsigned long)latestIr, (unsigned long)latestRed, fingerPresent, opticalSaturated,
        opticalLedDrive, rate, (unsigned long)irRange,
        (long)(usableHeartRate() ? latestHeartRate : -1),
        (long)(usableSpo2() ? latestSpo2 : -1), (long)rawHeartRate, (long)rawSpo2,
        (unsigned long)sampleFaults);
  }
}

// Auxiliary functions
#ifndef IOT_CONFIG_USE_X509_CERT
static AzIoTSasToken sasToken(
    &client,
    AZ_SPAN_FROM_STR(IOT_CONFIG_DEVICE_KEY),
    AZ_SPAN_FROM_BUFFER(sas_signature_buffer),
    AZ_SPAN_FROM_BUFFER(mqtt_password));
#endif // IOT_CONFIG_USE_X509_CERT

static void connectToWiFi()
{
  Logger.Info("Connecting to WIFI SSID " + String(ssid));

  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(100);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED)
  {
    serviceSensors();
    delay(5);
  }

  Serial.println("");

  Logger.Info("WiFi connected, IP address: " + WiFi.localIP().toString());
}

static void initializeTime()
{
  Logger.Info("Setting time using SNTP");

  configTime(GMT_OFFSET_SECS, GMT_OFFSET_SECS_DST, NTP_SERVERS);
  time_t now = time(NULL);
  while (now < UNIX_TIME_NOV_13_2017)
  {
    serviceSensors();
    delay(5);
    now = time(nullptr);
  }
  Serial.println("");
  Logger.Info("Time initialized!");
}

void receivedCallback(char* topic, byte* payload, unsigned int length)
{
  Logger.Info("Received [");
  Logger.Info(topic);
  Logger.Info("]: ");
  for (int i = 0; i < length; i++)
  {
    Serial.print((char)payload[i]);
  }
  Serial.println("");
}

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
static void mqtt_event_handler(void *handler_args, esp_event_base_t base, int32_t event_id, void *event_data)
{
  (void)handler_args;
  (void)base;
  (void)event_id;

  esp_mqtt_event_handle_t event = (esp_mqtt_event_handle_t)event_data;
#else // ESP_ARDUINO_VERSION_MAJOR
static esp_err_t mqtt_event_handler(esp_mqtt_event_handle_t event)
{
#endif // ESP_ARDUINO_VERSION_MAJOR
  switch (event->event_id)
  {
    int i, r;

    case MQTT_EVENT_ERROR:
      Logger.Info("MQTT event MQTT_EVENT_ERROR");
      break;
    case MQTT_EVENT_CONNECTED:
      mqttConnected = true;
      Logger.Info("MQTT event MQTT_EVENT_CONNECTED");

      r = esp_mqtt_client_subscribe(mqtt_client, AZ_IOT_HUB_CLIENT_C2D_SUBSCRIBE_TOPIC, 1);
      if (r == -1)
      {
        Logger.Error("Could not subscribe for cloud-to-device messages.");
      }
      else
      {
        Logger.Info("Subscribed for cloud-to-device messages; message id:" + String(r));
      }

      break;
    case MQTT_EVENT_DISCONNECTED:
      mqttConnected = false;
      Logger.Info("MQTT event MQTT_EVENT_DISCONNECTED");
      break;
    case MQTT_EVENT_SUBSCRIBED:
      Logger.Info("MQTT event MQTT_EVENT_SUBSCRIBED");
      break;
    case MQTT_EVENT_UNSUBSCRIBED:
      Logger.Info("MQTT event MQTT_EVENT_UNSUBSCRIBED");
      break;
    case MQTT_EVENT_PUBLISHED:
      Logger.Info("MQTT event MQTT_EVENT_PUBLISHED");
      break;
    case MQTT_EVENT_DATA:
      Logger.Info("MQTT event MQTT_EVENT_DATA");

      for (i = 0; i < (INCOMING_DATA_BUFFER_SIZE - 1) && i < event->topic_len; i++)
      {
        incoming_data[i] = event->topic[i];
      }
      incoming_data[i] = '\0';
      Logger.Info("Topic: " + String(incoming_data));

      for (i = 0; i < (INCOMING_DATA_BUFFER_SIZE - 1) && i < event->data_len; i++)
      {
        incoming_data[i] = event->data[i];
      }
      incoming_data[i] = '\0';
      Logger.Info("Data: " + String(incoming_data));

      break;
    case MQTT_EVENT_BEFORE_CONNECT:
      Logger.Info("MQTT event MQTT_EVENT_BEFORE_CONNECT");
      break;
    default:
      Logger.Error("MQTT event UNKNOWN");
      break;
  }

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
#else // ESP_ARDUINO_VERSION_MAJOR
  return ESP_OK;
#endif // ESP_ARDUINO_VERSION_MAJOR
}

static void initializeIoTHubClient()
{
  az_iot_hub_client_options options = az_iot_hub_client_options_default();
  options.user_agent = AZ_SPAN_FROM_STR(AZURE_SDK_CLIENT_USER_AGENT);

  if (az_result_failed(az_iot_hub_client_init(
          &client,
          az_span_create((uint8_t*)host, strlen(host)),
          az_span_create((uint8_t*)device_id, strlen(device_id)),
          &options)))
  {
    Logger.Error("Failed initializing Azure IoT Hub client");
    return;
  }

  size_t client_id_length;
  if (az_result_failed(az_iot_hub_client_get_client_id(
          &client, mqtt_client_id, sizeof(mqtt_client_id) - 1, &client_id_length)))
  {
    Logger.Error("Failed getting client id");
    return;
  }

  if (az_result_failed(az_iot_hub_client_get_user_name(
          &client, mqtt_username, sizeofarray(mqtt_username), NULL)))
  {
    Logger.Error("Failed to get MQTT clientId, return code");
    return;
  }

  Logger.Info("Client ID: " + String(mqtt_client_id));
  Logger.Info("Username: " + String(mqtt_username));
}

static int initializeMqttClient()
{
#ifndef IOT_CONFIG_USE_X509_CERT
  if (sasToken.Generate(SAS_TOKEN_DURATION_IN_MINUTES) != 0)
  {
    Logger.Error("Failed generating SAS token");
    return 1;
  }
#endif

  esp_mqtt_client_config_t mqtt_config;
  memset(&mqtt_config, 0, sizeof(mqtt_config));

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
    mqtt_config.broker.address.uri = mqtt_broker_uri;
    mqtt_config.broker.address.port = mqtt_port;
    mqtt_config.credentials.client_id = mqtt_client_id;
    mqtt_config.credentials.username = mqtt_username;

  #ifdef IOT_CONFIG_USE_X509_CERT
    LogInfo("MQTT client using X509 Certificate authentication");
    mqtt_config.credentials.authentication.certificate = IOT_CONFIG_DEVICE_CERT;
    mqtt_config.credentials.authentication.certificate_len = (size_t)sizeof(IOT_CONFIG_DEVICE_CERT);
    mqtt_config.credentials.authentication.key = IOT_CONFIG_DEVICE_CERT_PRIVATE_KEY;
    mqtt_config.credentials.authentication.key_len = (size_t)sizeof(IOT_CONFIG_DEVICE_CERT_PRIVATE_KEY);
  #else // Using SAS key
    mqtt_config.credentials.authentication.password = (const char*)az_span_ptr(sasToken.Get());
  #endif

    mqtt_config.session.keepalive = 30;
    mqtt_config.session.disable_clean_session = 0;
    mqtt_config.network.disable_auto_reconnect = false;
    mqtt_config.broker.verification.certificate = (const char*)ca_pem;
    mqtt_config.broker.verification.certificate_len = (size_t)ca_pem_len;
#else // ESP_ARDUINO_VERSION_MAJOR
  mqtt_config.uri = mqtt_broker_uri;
  mqtt_config.port = mqtt_port;
  mqtt_config.client_id = mqtt_client_id;
  mqtt_config.username = mqtt_username;

#ifdef IOT_CONFIG_USE_X509_CERT
  Logger.Info("MQTT client using X509 Certificate authentication");
  mqtt_config.client_cert_pem = IOT_CONFIG_DEVICE_CERT;
  mqtt_config.client_key_pem = IOT_CONFIG_DEVICE_CERT_PRIVATE_KEY;
#else // Using SAS key
  mqtt_config.password = (const char*)az_span_ptr(sasToken.Get());
#endif

  mqtt_config.keepalive = 30;
  mqtt_config.disable_clean_session = 0;
  mqtt_config.disable_auto_reconnect = false;
  mqtt_config.event_handle = mqtt_event_handler;
  mqtt_config.user_context = NULL;
  mqtt_config.cert_pem = (const char*)ca_pem;
#endif // ESP_ARDUINO_VERSION_MAJOR

  mqtt_client = esp_mqtt_client_init(&mqtt_config);

  if (mqtt_client == NULL)
  {
    Logger.Error("Failed creating mqtt client");
    return 1;
  }

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
    esp_mqtt_client_register_event(mqtt_client, MQTT_EVENT_ANY, mqtt_event_handler, NULL);
#endif // ESP_ARDUINO_VERSION_MAJOR

  esp_err_t start_result = esp_mqtt_client_start(mqtt_client);

  if (start_result != ESP_OK)
  {
    Logger.Error("Could not start mqtt client; error code:" + start_result);
    return 1;
  }
  else
  {
    Logger.Info("MQTT client started");
    return 0;
  }
}

/*
 * @brief           Gets the number of seconds since UNIX epoch until now.
 * @return uint32_t Number of seconds.
 */
static uint32_t getEpochTimeInSecs() { return (uint32_t)time(NULL); }

static void establishConnection()
{
  connectToWiFi();
  initializeTime();
  initializeIoTHubClient();
  (void)initializeMqttClient();
}

static void generateTelemetryPayload()
{
  JsonDocument document;
  document["deviceId"] = device_id;
  document["sequence"] = telemetry_send_count++;
  document["timestamp"] = getEpochTimeInSecs();

  document["dhtReadingValid"] = usableAmbientReading();
  if (usableAmbientReading())
  {
    document["temperatureC"] = roundf(ambientTemperatureC * 10.0f) / 10.0f;
    document["humidityPercent"] = roundf(ambientHumidityPercent * 10.0f) / 10.0f;
  }
  else
  {
    document["temperatureC"] = nullptr;
    document["humidityPercent"] = nullptr;
  }

  document["max30102Connected"] = maxPresent;
  document["fingerDetected"] = fingerPresent && millis() - lastOpticalMs < 500;
  document["opticalSaturated"] = opticalSaturated;
  document["opticalLedDrive"] = opticalLedDrive;
  document["sampleFaults"] = sampleFaults;
  document["fifoOverflowRaw"] = fifoOverflowRaw;
  document["irRange"] = irRange;
  document["irRaw"] = latestIr;
  document["redRaw"] = latestRed;
  if (usableHeartRate())
    document["heartRate"] = latestHeartRate;
  else document["heartRate"] = nullptr;
  if (usableSpo2())
    document["spo2"] = latestSpo2;
  else document["spo2"] = nullptr;

  document["imuConnected"] = imuConfigured;
  document["imuWhoAmI"] = imuWhoAmI;
  bool motionFresh = imuReadingValid && uint32_t(millis()-lastImuReadMs) <= 150;
  document["imuReadingValid"] = motionFresh;
  document["motionCalibrated"] = motionDetector.calibrated();
  document["motionDetected"] = motionFresh && motionDetector.moving();
  document["motionEvents"] = motionDetector.events();
  document["motionAudioReady"] = motionAudioReady;
  if (motionFresh)
  {
    document["accelXg"] = roundf(imuAx * 1000.0f) / 1000.0f;
    document["accelYg"] = roundf(imuAy * 1000.0f) / 1000.0f;
    document["accelZg"] = roundf(imuAz * 1000.0f) / 1000.0f;
    document["rotationDps"] = motionDetector.rotationDps();
  }

  // A fresh echo confirms a usable reading, not permanent physical presence.
  document["ultrasonicConnected"] = ultrasonic.usable();
  document["ultrasonicReadingValid"] = ultrasonic.usable();
  document["ultrasonicStatus"] = ultrasonic.status();
  if (ultrasonic.usable()) document["obstacleCm"] = roundf(ultrasonic.distanceCm() * 10.0f) / 10.0f;
  else document["obstacleCm"] = nullptr;
  document["sos"] = false;
  telemetry_payload = "";
  serializeJson(document, telemetry_payload);
}

static void sendTelemetry()
{
  if (!mqttConnected) return;
  Logger.Info("Sending telemetry ...");

  // The topic could be obtained just once during setup,
  // however if properties are used the topic need to be generated again to reflect the
  // current values of the properties.
  if (az_result_failed(az_iot_hub_client_telemetry_get_publish_topic(
          &client, NULL, telemetry_topic, sizeof(telemetry_topic), NULL)))
  {
    Logger.Error("Failed az_iot_hub_client_telemetry_get_publish_topic");
    return;
  }
  strncat(
      telemetry_topic,
      "$.ct=application%2Fjson&$.ce=utf-8",
      sizeof(telemetry_topic) - strlen(telemetry_topic) - 1);

  generateTelemetryPayload();

  if (esp_mqtt_client_enqueue(
          mqtt_client,
          telemetry_topic,
          (const char*)telemetry_payload.c_str(),
          telemetry_payload.length(),
          MQTT_QOS1,
          DO_NOT_RETAIN_MSG, false)
      < 0)
  {
    Logger.Error("Failed publishing");
  }
  else
  {
    Logger.Info("Telemetry queued");
  }
}

// Arduino setup and loop main functions.

void setup()
{
  initializeSensors();
  speaker.setListener([](const char* status) { phoneSpeaker.status(status); });
  phoneSpeaker.setHealthReporter(reportPhoneHealth);
  phoneSpeaker.begin();
  Preferences settings;
  if (settings.begin("wheelchair", false)) {
    motionAudioReady=settings.getBool("motionaudio", false);
    phoneSpeaker.setFaceAudioReady(settings.getBool("faceaudio", false));
    settings.end();
  }
  Serial.printf("MOTION voice ready=%d; track 2 requires /mp3/0002.mp3\n", motionAudioReady);
  establishConnection();
}

void loop()
{
  serviceSensors();
  if (WiFi.status() != WL_CONNECTED)
  {
    connectToWiFi();
  }
#ifndef IOT_CONFIG_USE_X509_CERT
  else if (sasToken.IsExpired())
  {
    Logger.Info("SAS token expired; reconnecting with a new one.");
    (void)esp_mqtt_client_destroy(mqtt_client);
    initializeMqttClient();
  }
#endif
  else if (millis() > next_telemetry_send_time_ms)
  {
    sendTelemetry();
    next_telemetry_send_time_ms = millis() + TELEMETRY_FREQUENCY_MILLISECS;
  }
}
