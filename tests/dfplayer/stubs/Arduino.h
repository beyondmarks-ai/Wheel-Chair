#pragma once
#include <cstdint>
#include <cstddef>
#include <vector>
#include <deque>
#define SERIAL_8N1 0
extern uint32_t fakeNow;
inline uint32_t millis() { return fakeNow; }
inline int digitalRead(int) { return 1; }
class HardwareSerial {
 public:
  std::deque<uint8_t> incoming;
  std::vector<std::vector<uint8_t>> sent;
  void begin(int, int, int, int) {}
  int available() { return incoming.size(); }
  int read() { auto v = incoming.front(); incoming.pop_front(); return v; }
  size_t write(const uint8_t* p, size_t n) { sent.emplace_back(p, p+n); return n; }
  void println(const char*) {}
  template<typename... T> void printf(const char*, T...) {}
};
extern HardwareSerial Serial;
