#include <Arduino.h>
#include <Wire.h>
#include <DHT.h>

// ASSUMED wiring: change these GPIO numbers to match your actual connections.
// SDA/SCL are shared by the MPU-6050 and MAX30102.
constexpr uint8_t SDA_PIN = 21;
constexpr uint8_t SCL_PIN = 22;
constexpr uint8_t DHT_PIN = 4;
// User confirmed TRIG=5 and ECHO=18 on 2026-10-07.
constexpr uint8_t TRIG_PIN = 5;
constexpr uint8_t ECHO_PIN = 18;
#define DHT_TYPE DHT11  // Change to DHT22 if appropriate.

// Disconnect wheelchair motor power before uploading/running this sketch.
// HC-SR04 Echo is normally 5 V. Use a divider/level shifter to ESP32's 3.3 V.
// This sketch replaces the existing firmware and does not control motors.
DHT dht(DHT_PIN, DHT_TYPE);

bool respondsAt(uint8_t address) {
  Wire.beginTransmission(address);
  return Wire.endTransmission() == 0;
}

bool readRegister(uint8_t address, uint8_t reg, uint8_t &value) {
  Wire.beginTransmission(address);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(address, static_cast<uint8_t>(1)) != 1) return false;
  value = Wire.read();
  return true;
}

void scanI2C() {
  Serial.println("I2C address scan:");
  unsigned count = 0;
  for (uint8_t address = 0x08; address <= 0x77; ++address) {
    if (respondsAt(address)) {
      Serial.printf("  ACK at 0x%02X\n", address);
      ++count;
    }
  }
  if (count == 0) Serial.println("  No responses. Check power, ground, SDA/SCL pins and pull-ups.");
}

void testMPU6050() {
  bool acknowledged = false;
  for (uint8_t address : {uint8_t(0x68), uint8_t(0x69)}) {
    if (!respondsAt(address)) continue;
    acknowledged = true;
    uint8_t id = 0;
    if (!readRegister(address, 0x75, id)) {
      Serial.printf("[FAIL] MPU-6050 at 0x%02X: cannot read WHO_AM_I.\n", address);
    } else if (id == 0x68) {
      // WHO_AM_I stays 0x68 even when AD0 selects bus address 0x69.
      Serial.printf("[PASS] MPU-6050 responds at 0x%02X, WHO_AM_I = 0x68.\n", address);
    } else if (id == 0x70 || id == 0x71 || id == 0x73) {
      Serial.printf("[PASS] MPU-family IMU responds at 0x%02X, WHO_AM_I = 0x%02X (likely MPU-6500/9250 family).\n",
                    address, id);
    } else {
      Serial.printf("[UNKNOWN] I2C device at 0x%02X: ID 0x%02X is not MPU-6050's expected ID.\n", address, id);
    }
  }
  if (!acknowledged) Serial.println("[NO RESPONSE] MPU-6050: neither 0x68 nor 0x69 acknowledged.");
}

void testMAX30102() {
  constexpr uint8_t address = 0x57;
  if (!respondsAt(address)) {
    Serial.println("[NO RESPONSE] MAX30102: no acknowledgement at 0x57.");
    return;
  }
  uint8_t id = 0;
  if (!readRegister(address, 0xFF, id)) {
    Serial.println("[FAIL] MAX30102: device acknowledged, but part ID could not be read.");
  } else if (id == 0x15) {
    Serial.println("[PASS] MAX30102-compatible sensor responds at 0x57, part ID = 0x15.");
    Serial.println("       This ID is shared by some MAX3010x models; pulse/SpO2 performance is not tested.");
  } else {
    Serial.printf("[UNKNOWN] Device at 0x57: unexpected part ID 0x%02X.\n", id);
  }
}

void testDHT() {
  float humidity = dht.readHumidity();
  float temperature = dht.readTemperature();
  if (isnan(humidity) || isnan(temperature)) {
    Serial.printf("[NO VALID DATA] DHT on GPIO %u: check pin, DHT type, power and data pull-up.\n", DHT_PIN);
    return;
  }
  Serial.printf("[PASS] DHT: %.1f C, %.1f %% humidity.\n", temperature, humidity);
}

void testUltrasonic() {
  if (digitalRead(ECHO_PIN) == HIGH) {
    Serial.println("[FAIL] HC-SR04: Echo already HIGH before trigger; check wiring/voltage.");
    return;
  }
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long duration = pulseIn(ECHO_PIN, HIGH, 30000UL);
  if (duration == 0) {
    Serial.println("[NO ECHO] HC-SR04: check pins/wiring and place a flat object 10-100 cm away.");
    Serial.println("          No echo alone does not prove the sensor is disconnected.");
    return;
  }
  float distanceCm = duration * 0.0343f / 2.0f;
  if (distanceCm < 2.0f || distanceCm > 400.0f) {
    Serial.printf("[CHECK] HC-SR04: pulse received, but %.1f cm is outside nominal range.\n", distanceCm);
  } else {
    Serial.printf("[PASS] HC-SR04: echo received, estimated distance %.1f cm.\n", distanceCm);
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\nESP32 sensor connection test");
  Serial.printf("ASSUMED GPIOs: SDA=%u SCL=%u DHT=%u TRIG=%u ECHO=%u\n",
                SDA_PIN, SCL_PIN, DHT_PIN, TRIG_PIN, ECHO_PIN);
  Serial.println("No response can mean wrong wiring/pins, missing power, or a faulty/missing sensor.");
  Serial.println("PASS confirms communication only, not full sensor accuracy or wheelchair safety.");
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  Wire.setTimeOut(25);
  dht.begin();
  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT); // External divider supplies the pull-down.
  delay(2000);
  scanI2C();
}

void loop() {
  Serial.println("\n--- Sensor checks ---");
  testMPU6050();
  testMAX30102();
  testDHT();
  testUltrasonic();
  delay(3000);
}
