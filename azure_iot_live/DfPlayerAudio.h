#pragma once
#include <Arduino.h>

// UART2: DFPlayer TX -> GPIO16 (RX2), DFPlayer RX -> GPIO17 (TX2).
// Nonblocking diagnostic commands keep the sensor loop responsive.
class DfPlayerAudio {
 public:
  explicit DfPlayerAudio(HardwareSerial& link) : uart(link) {}
  using Listener = void (*)(const char*);
  void setListener(Listener callback) { listener = callback; }
  bool busy() const { return phase != IDLE; }
  bool motionActive() const { return busy() && requestedTrack == 2; }
  bool nonEmergencyActive() const { return busy() && requestedTrack != 1; }
  void begin() { uart.begin(9600, SERIAL_8N1, 16, 17); }
  void test(uint8_t volume = 30) {
    if (volume < 1 || volume > 30) { Serial.println("AUDIO test volume must be 1-30"); return; }
    if (nonEmergencyActive()) stop(); // Emergency preempts any other message.
    start(1, volume);
  }
  bool motion(uint8_t volume = 30) {
    if (busy() || volume < 1 || volume > 30) return false;
    start(2, volume); return true;
  }
  bool phrase(uint8_t track) {
    if (busy() || track < 3 || track > 5) return false;
    start(track, 30); return true;
  }
  bool reminder(uint16_t track) {
    if (busy() || track < 101 || track > 2999) return false;
    start(track, 30); return true;
  }
 private:
  void start(uint16_t track, uint8_t volume) {
    if (phase != IDLE) { Serial.println("AUDIO test already running"); return; }
    if (volume < 1 || volume > 30) { Serial.println("AUDIO test volume must be 1-30"); return; }
    requestedVolume = volume;
    requestedTrack = track;
    for (int n = 0; n < 128 && uart.available(); ++n) uart.read();
    used = 0;
    queryBytes = receivedBytes; queryErrors = invalidFrames;
    phase = QUERY; deadline = millis() + 1500;
    send(0x43, 0); // Read volume: only a valid reply demonstrates connectivity.
    Serial.println("AUDIO querying DFPlayer on RX2=16 TX2=17");
  }
 public:
  void stop() {
    if (phase == PLAYING) send(0x16, 0);
    phase = IDLE;
    Serial.println("AUDIO test stopped");
    report("STOPPED");
  }
  void poll() {
    if (used && uint32_t(millis() - lastByte) > 200) used = 0;
    for (int n = 0; n < 64 && uart.available(); ++n) {
      uint8_t value = uart.read(); lastByte = millis();
      ++receivedBytes;
      if (used == 0 && value != 0x7e) continue;
      received[used++] = value;
      if (used == 10) {
        used = 0;
        uint16_t sum = 0;
        for (int i = 1; i <= 6; ++i) sum += received[i];
        uint16_t checksum = (uint16_t(received[7]) << 8) | received[8];
        if (received[1] == 0xff && received[2] == 6 && received[9] == 0xef &&
            checksum == uint16_t(0 - sum)) {
          reply(received[3], (uint16_t(received[5]) << 8) | received[6]);
        } else { ++invalidFrames; Serial.println("AUDIO invalid UART frame ignored"); }
      }
    }
    if (phase == IDLE || int32_t(millis() - deadline) < 0) return;
    switch (phase) {
      case SET_VOLUME:
        send(0x43, 0); phase = CHECK_VOLUME; deadline = millis() + 1500; break;
      case PLAY_DELAY:
        send(0x12, requestedTrack); phase = PLAYING;
        deadline = millis() + (requestedTrack >= 101 ? 20000 : (requestedTrack == 2 ? 7000 : 5000));
        Serial.printf("AUDIO playback requested: /mp3/%04u.mp3 volume=%u/30\n", requestedTrack, requestedVolume);
        report("PLAY_REQUESTED"); break;
      case PLAYING:
        send(0x16, 0); phase = IDLE;
        Serial.printf("AUDIO stopped after %us; playback completion not confirmed\n", requestedTrack >= 101 ? 20 : (requestedTrack == 2 ? 7 : 5));
        report("UNCONFIRMED"); break;
      default:
        phase = IDLE;
        Serial.printf("AUDIO UART diagnostic: rx_bytes=%lu invalid_frames=%lu rx16_level=%d tx17_level=%d\n",
            (unsigned long)(receivedBytes - queryBytes),
            (unsigned long)(invalidFrames - queryErrors), digitalRead(16), digitalRead(17));
        Serial.println("AUDIO no expected DFPlayer reply; playback not started");
        report("ERROR"); break;
    }
  }

 private:
  Listener listener = nullptr;
  void report(const char* status) { if (listener) listener(status); }
  enum Phase { IDLE, QUERY, SET_VOLUME, CHECK_VOLUME, PLAY_DELAY, PLAYING };
  HardwareSerial& uart;
  Phase phase = IDLE;
  uint32_t deadline = 0, lastByte = 0;
  uint32_t receivedBytes = 0, invalidFrames = 0, queryBytes = 0, queryErrors = 0;
  uint8_t received[10] = {}, used = 0;
  uint8_t requestedVolume = 30;
  uint16_t requestedTrack = 1;
  void send(uint8_t command, uint16_t value) {
    uint8_t frame[10] = {0x7e, 0xff, 6, command, 1,
        uint8_t(value >> 8), uint8_t(value), 0, 0, 0xef};
    uint16_t sum = 0;
    for (int i = 1; i <= 6; ++i) sum += frame[i];
    uint16_t check = uint16_t(0 - sum);
    frame[7] = check >> 8; frame[8] = check;
    uart.write(frame, sizeof(frame));
  }
  void reply(uint8_t command, uint16_t value) {
    Serial.printf("AUDIO reply command=0x%02X value=%u\n", command, value);
    if (phase == IDLE) return;
    if (command == 0x40) {
      if (phase == PLAYING) send(0x16, 0);
      phase = IDLE;
      Serial.printf("AUDIO module error=%u (5=file index, 6=file mismatch)\n", value);
      report("ERROR");
    } else if (command == 0x43 && phase == QUERY && value <= 30) {
      send(0x06, requestedVolume); phase = SET_VOLUME; deadline = millis() + 250;
      Serial.printf("AUDIO module responded; setting volume=%u/30\n", requestedVolume);
    } else if (command == 0x43 && phase == CHECK_VOLUME) {
      if (value == requestedVolume) { phase = PLAY_DELAY; deadline = millis() + 200; }
      else { phase = IDLE; Serial.println("AUDIO requested volume not confirmed; playback cancelled"); report("ERROR"); }
    } else if (command == 0x3d && phase == PLAYING && value == requestedTrack) {
      phase = IDLE;
      Serial.println("AUDIO module reports playback finished; audible sound needs user confirmation");
      report("FINISHED");
    }
  }
};
