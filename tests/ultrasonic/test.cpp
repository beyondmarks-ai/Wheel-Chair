#include <cassert>
#include <cmath>
#include <cstring>
#include <iostream>
#include "../../azure_iot_live/UltrasonicSensor.h"

uint32_t fakeNow = 0;
bool echoHigh = false;
unsigned triggers = 0;
void (*edgeCallback)(void*) = nullptr;
void* edgeArgument = nullptr;
void reset() { fakeNow = 100000; echoHigh = false; triggers = 0; }
void edge(bool high, uint32_t advance) {
  fakeNow += advance; echoHigh = high; edgeCallback(edgeArgument);
}
void echo(uint32_t pulse) { edge(true, 200); edge(false, pulse); }

int main() {
  { // Approximately 30 cm, followed by a missing echo: no stale distance.
    reset(); UltrasonicSensor s; s.begin(); s.poll(); assert(triggers == 1);
    echo(1750); s.poll(); assert(s.usable()); assert(std::abs(s.distanceCm() - 30.0125f) < .01f);
    fakeNow = 200000; s.poll(); assert(triggers == 2);
    fakeNow += 30001; s.poll(); assert(!s.usable()); assert(!std::strcmp(s.status(), "no_echo"));
  }
  { // Reject an echo that's already high before triggering.
    reset(); UltrasonicSensor s; s.begin(); echoHigh = true; s.poll();
    assert(triggers == 0 && !s.usable()); assert(!std::strcmp(s.status(), "echo_stuck_high"));
  }
  { // A short electrical spike and beyond-range pulse are not obstacles.
    reset(); UltrasonicSensor s; s.begin(); s.poll(); echo(20); s.poll(); assert(!s.usable());
    fakeNow = 200000; s.poll(); echo(25000); s.poll(); assert(!s.usable());
  }
  { // No service for half a second must invalidate the last measured distance.
    reset(); UltrasonicSensor s; s.begin(); s.poll(); echo(1750); s.poll();
    fakeNow += 500000; assert(!s.usable()); assert(!std::strcmp(s.status(), "stale"));
  }
  { // Ignore late edges arriving after a timed-out trigger.
    reset(); UltrasonicSensor s; s.begin(); s.poll(); fakeNow += 30001; s.poll();
    echo(1750); s.poll(); assert(!s.usable());
  }
  { // Cancel before a DHT read masks interrupts; don't count delayed edges.
    reset(); UltrasonicSensor s; s.begin(); s.poll(); edge(true, 200);
    s.cancelPending(); edge(false, 1750); s.poll(); assert(!s.usable());
    fakeNow = 200000; s.poll(); echo(1750); s.poll(); assert(s.usable());
  }
  { // micros() rollover during the echo must preserve pulse width.
    reset(); fakeNow = UINT32_MAX - 500; UltrasonicSensor s; s.begin(); s.poll();
    echo(1750); s.poll(); assert(s.usable()); assert(s.pulseUs() == 1750);
  }
  { // An old completed interrupt must not revive stale data after a stall.
    reset(); UltrasonicSensor s; s.begin(); s.poll(); echo(1750);
    fakeNow += 500000; s.poll(); assert(!s.usable());
  }
  std::cout << "PASS: 8 ultrasonic timing scenarios\n";
}
