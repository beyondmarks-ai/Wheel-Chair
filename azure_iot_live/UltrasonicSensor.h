#pragma once
#include <Arduino.h>
#include <driver/gpio.h>

// User-confirmed wiring: TRIG GPIO5, ECHO GPIO18. A standard HC-SR04 needs
// 5 V power and an external divider/level shifter on its ECHO output.
class UltrasonicSensor {
 public:
  static constexpr uint8_t TRIG = 5, ECHO = 18;
  void begin() {
    digitalWrite(TRIG, LOW);
    pinMode(TRIG, OUTPUT);
    // The external divider already pulls ECHO low. An internal pull-down
    // loads that divider and can reduce HIGH below the input threshold.
    pinMode(ECHO, INPUT);
    attachInterruptArg(ECHO, onEdge, this, CHANGE);
  }
  void poll() {
    const uint32_t now = micros();
    bool done = false;
    uint32_t pulse = 0, ended = 0;
    portENTER_CRITICAL(&lock);
    if (complete) {
      done = true; pulse = width; ended = finished;
      complete = false; armed = false;
    } else if (pending && uint32_t(now - triggered) >= 30000) {
      armed = false;
    }
    portEXIT_CRITICAL(&lock);
    if (done) {
      pending = false;
      lastPulse = pulse;
      lastFinished = ended;
      valid = pulse >= 117 && pulse <= 23323 && uint32_t(now - ended) < 500000;
      state = valid ? "echo" : "out_of_range_or_stale";
    } else if (pending && uint32_t(now - triggered) >= 30000) {
      pending = false; valid = false; lastPulse = 0; state = "no_echo";
    }
    if (pending || uint32_t(now - lastAttempt) < 100000) return;
    lastAttempt = now;
    if (digitalRead(ECHO) == HIGH) {
      valid = false; lastPulse = 0; state = "echo_stuck_high"; return;
    }
    portENTER_CRITICAL(&lock);
    sawRise = complete = false;
    armed = true;
    triggered = micros();
    portEXIT_CRITICAL(&lock);
    pending = true;
    digitalWrite(TRIG, HIGH);
    delayMicroseconds(10);
    digitalWrite(TRIG, LOW);
  }
  bool usable() const { return valid && uint32_t(micros() - lastFinished) < 500000; }
  void cancelPending() {
    // The DHT driver masks interrupts while reading; do not trust echo timing
    // across that interval. Retry on the next normal measurement interval.
    if (!pending) return;
    portENTER_CRITICAL(&lock);
    armed = complete = false;
    portEXIT_CRITICAL(&lock);
    pending = valid = false; lastPulse = 0; state = "sampling_interrupted";
  }
  float distanceCm() const { return lastPulse * 0.0343f / 2.0f; }
  const char* status() const { return valid && !usable() ? "stale" : state; }
  uint32_t pulseUs() const { return lastPulse; }

 private:
  portMUX_TYPE lock = portMUX_INITIALIZER_UNLOCKED;
  volatile bool armed = false, sawRise = false, complete = false;
  volatile uint32_t rise = 0, width = 0, finished = 0;
  bool pending = false, valid = false;
  uint32_t triggered = 0, lastAttempt = 0, lastFinished = 0, lastPulse = 0;
  const char* state = "starting";
  static void ARDUINO_ISR_ATTR onEdge(void* arg) {
    auto* self = static_cast<UltrasonicSensor*>(arg);
    const uint32_t now = micros();
    const bool high = gpio_get_level(static_cast<gpio_num_t>(ECHO));
    portENTER_CRITICAL_ISR(&self->lock);
    if (self->armed && uint32_t(now - self->triggered) <= 30000) {
      if (high && !self->sawRise) { self->rise = now; self->sawRise = true; }
      else if (!high && self->sawRise) {
        self->width = now - self->rise;
        self->finished = now;
        self->complete = true;
        self->armed = false;
      }
    }
    portEXIT_CRITICAL_ISR(&self->lock);
  }
};
