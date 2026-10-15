#pragma once
#include <string>
#include <cstdio>
#include "Arduino.h"
class BluetoothSerial {
 public:
  static BluetoothSerial* instance;
  bool connected = true;
  std::deque<char> incoming;
  std::string output;
  BluetoothSerial() { instance = this; }
  void enableSSP(bool, bool) {}
  bool begin(const char*, bool, bool) { return true; }
  bool hasClient() { return connected; }
  int available() { return incoming.size(); }
  char read() { char c = incoming.front(); incoming.pop_front(); return c; }
  void println(const char* s) { output += s; output += '\n'; }
  template<typename... Args> void printf(const char* format, Args... args) {
    char line[200]; std::snprintf(line, sizeof(line), format, args...); output += line;
  }
};
