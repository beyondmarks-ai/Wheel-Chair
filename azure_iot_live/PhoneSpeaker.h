#pragma once
#include <BluetoothSerial.h>
#include <cstring>
#include <cctype>
#include "DfPlayerAudio.h"

// Local, paired Bluetooth SPP link, independent of the camera Wi-Fi and Azure.
// No persisted or reconnect-time playback queue. Only the sensor loop calls audio.
class PhoneSpeaker {
 public:
  explicit PhoneSpeaker(DfPlayerAudio& output) : audio(output) {}
  void setFaceAudioReady(bool value) { faceAudioReady = value; }
  void setHealthReporter(void (*reporter)(BluetoothSerial&)) { healthReporter = reporter; }
  void begin() {
    link.enableSSP(false, false); // Controller has no pairing keyboard.
    ready = link.begin("Wheelchair-Speaker", false, true);
    Serial.println(ready ? "PHONE Bluetooth ready: Wheelchair-Speaker" : "PHONE Bluetooth failed");
  }
  void status(const char* value) {
    if (!activeId[0]) return;
    respond(activeId, value);
    if (strcmp(value, "PLAY_REQUESTED") != 0) activeId[0] = 0;
  }
  void poll() {
    if (!ready) return;
    bool connected = link.hasClient();
    if (!connected) {
      used = 0; overflow = false; handshaken = false;
      for (int n = 0; n < 64 && link.available(); ++n) link.read();
      if (activeId[0]) { activeId[0] = 0; audio.stop(); }
      return;
    }
    if (used && uint32_t(millis() - lastByte) > 1000) { used = 0; overflow = false; }
    for (int n = 0; n < 64 && link.available(); ++n) {
      char c = link.read(); lastByte = millis();
      if (c == '\r') continue;
      if (c == '\n') {
        line[used] = 0;
        if (!overflow) command();
        used = 0; overflow = false;
      } else if (used < sizeof(line) - 1) line[used++] = c;
      else overflow = true;
    }
  }
 private:
  BluetoothSerial link;
  DfPlayerAudio& audio;
  bool ready = false, overflow = false, handshaken = false;
  bool faceAudioReady = false;
  void (*healthReporter)(BluetoothSerial&) = nullptr;
  char line[64] = {}, activeId[37] = {}, seen[16][37] = {};
  size_t used = 0, nextSeen = 0;
  uint32_t lastByte = 0;
  static bool validId(const char* id) {
    if (strlen(id) != 36) return false;
    for (int i = 0; i < 36; ++i) {
      bool dash = i == 8 || i == 13 || i == 18 || i == 23;
      if (dash ? id[i] != '-' : !isxdigit(static_cast<unsigned char>(id[i]))) return false;
    }
    return true;
  }
  void respond(const char* id, const char* value) {
    Serial.printf("PHONE speaker %s\n", value);
    if (link.hasClient()) link.printf("SPEAKER %s %s\n", id, value);
  }
  void command() {
    if (strcmp(line, "HELLO") == 0) {
      handshaken = true; link.println("WHEELCHAIR_SPEAKER 1"); return;
    }
    if (!handshaken) return;
    if (strcmp(line, "CARE HELLO") == 0) { link.println("CARE_MESSAGES 1 READY"); return; }
    if (strcmp(line, "HEALTH?") == 0) {
      if (healthReporter) healthReporter(link);
      return;
    }
    if (strcmp(line, "FACE HELLO") == 0) {
      link.println(faceAudioReady ? "FACE_MESSAGES 1 READY" : "FACE_MESSAGES 1 NOT_READY"); return;
    }
    if (strcmp(line, "STOP") == 0) { if (activeId[0]) audio.stop(); return; }
    uint16_t track = 1;
    const char* id = nullptr;
    if (strncmp(line, "SOS ", 4) == 0) id = line + 4;
    else if (strlen(line) == 45 && strncmp(line, "PHRASE ", 7) == 0 &&
             line[7] >= '3' && line[7] <= '5' && line[8] == ' ') {
      track = line[7] - '0'; id = line + 9;
    }
    else if (strncmp(line, "REMIND ", 7) == 0) {
      const char* p = line + 7; uint16_t value = 0; int digits = 0;
      while (*p >= '0' && *p <= '9' && digits < 4) { value = value * 10 + (*p++ - '0'); ++digits; }
      if (*p != ' ' || digits < 3 || line[7] == '0' || value < 101 || value > 2999) return;
      track = value; id = p + 1;
    }
    if (!id || !validId(id)) return;
    for (auto& old : seen) if (strcmp(old, id) == 0) { respond(id, "DUPLICATE"); return; }
    strcpy(seen[nextSeen++ % 16], id);
    if (track >= 3 && track <= 5 && !faceAudioReady) { respond(id, "NOT_READY"); return; }
    if (audio.busy() && (track != 1 || !audio.nonEmergencyActive())) { respond(id, "BUSY"); return; }
    // Stop motion before assigning the phone ID, so its STOPPED callback cannot
    // be mistaken for the new emergency's result.
    if (audio.nonEmergencyActive()) audio.stop();
    strcpy(activeId, id);
    respond(id, "CHECKING");
    if (track == 1) audio.test(30); else if (track >= 101) audio.reminder(track); else audio.phrase(track);
  }
};
