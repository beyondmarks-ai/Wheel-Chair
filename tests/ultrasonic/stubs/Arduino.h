#pragma once
#include <cstdint>
#define ARDUINO_ISR_ATTR
#define LOW 0
#define HIGH 1
#define OUTPUT 1
#define INPUT 0
#define INPUT_PULLDOWN 2
#define CHANGE 3
using portMUX_TYPE = int;
#define portMUX_INITIALIZER_UNLOCKED 0
#define portENTER_CRITICAL(x) ((void)(x))
#define portEXIT_CRITICAL(x) ((void)(x))
#define portENTER_CRITICAL_ISR(x) ((void)(x))
#define portEXIT_CRITICAL_ISR(x) ((void)(x))
extern uint32_t fakeNow;
extern bool echoHigh;
extern unsigned triggers;
extern void (*edgeCallback)(void*);
extern void* edgeArgument;
inline uint32_t micros() { return fakeNow; }
inline void delayMicroseconds(uint32_t us) { fakeNow += us; }
inline void pinMode(int, int) {}
inline int digitalRead(int) { return echoHigh; }
inline void digitalWrite(int pin, int value) { if (pin == 5 && value == HIGH) ++triggers; }
inline void attachInterruptArg(int, void (*callback)(void*), void* arg, int) {
  edgeCallback = callback; edgeArgument = arg;
}
