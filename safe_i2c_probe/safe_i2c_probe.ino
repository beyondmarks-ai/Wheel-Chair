#include <Arduino.h>
#include <Wire.h>

constexpr uint8_t SDA_PIN = 21;
constexpr uint8_t SCL_PIN = 22;

bool readRegister(uint8_t address, uint8_t reg, uint8_t &value) {
  Wire.beginTransmission(address);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(address, static_cast<uint8_t>(1)) != 1) return false;
  value = Wire.read();
  return true;
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("SAFE I2C SENSOR PROBE | SDA=21 SCL=22");
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  Wire.setTimeOut(25);

  unsigned count = 0;
  for (uint8_t address = 0x08; address <= 0x77; ++address) {
    Wire.beginTransmission(address);
    if (Wire.endTransmission() == 0) {
      Serial.printf("ACK 0x%02X\n", address);
      ++count;
    }
  }
  Serial.printf("I2C DEVICE COUNT: %u\n", count);

  uint8_t id = 0;
  if (readRegister(0x57, 0xFF, id)) {
    Serial.printf("MAX3010x: PRESENT, PART_ID=0x%02X\n", id);
  } else {
    Serial.println("MAX3010x: NO RESPONSE");
  }

  bool mpuFound = false;
  for (uint8_t address : {uint8_t(0x68), uint8_t(0x69)}) {
    if (readRegister(address, 0x75, id)) {
      Serial.printf("MPU-6050: PRESENT AT 0x%02X, WHO_AM_I=0x%02X\n", address, id);
      mpuFound = true;
    }
  }
  if (!mpuFound) Serial.println("MPU-6050: NO RESPONSE");
  Serial.println("PROBE COMPLETE");
}

void loop() { delay(1000); }
