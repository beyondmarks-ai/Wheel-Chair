#include <cassert>
#include <iostream>
#include "../../azure_iot_live/DfPlayerAudio.h"
uint32_t fakeNow = 0;
HardwareSerial Serial;
void reply(HardwareSerial& uart, uint8_t command, uint16_t value, bool corrupt=false) {
  uint8_t frame[10] = {0x7e,0xff,6,command,0,uint8_t(value>>8),uint8_t(value),0,0,0xef};
  uint16_t sum=0;
  for (int i=1;i<=6;++i) sum+=frame[i];
  uint16_t checksum=uint16_t(0-sum);
  frame[7]=checksum>>8; frame[8]=checksum;
  if(corrupt) frame[8]^=1;
  for(auto v:frame) uart.incoming.push_back(v);
}
int count(HardwareSerial& uart, int cmd) {
  int n=0; for(auto& frame:uart.sent) if(frame[3]==cmd) ++n; return n;
}
void ready(HardwareSerial& uart, DfPlayerAudio& audio) {
  audio.begin(); audio.test(8); reply(uart,0x43,20); audio.poll();
  // Verify the low-volume command against a known protocol frame.
  const std::vector<uint8_t> expected={0x7e,0xff,6,6,1,0,8,0xfe,0xec,0xef};
  assert(uart.sent.back()==expected);
  fakeNow+=251; audio.poll(); reply(uart,0x43,8); audio.poll();
}
int main() {
  { HardwareSerial u; DfPlayerAudio a(u);assert(!a.reminder(100));assert(!a.reminder(3000));assert(a.reminder(2999));
    reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
    assert(u.sent.back()[3]==0x12 && u.sent.back()[5]==(2999>>8) && u.sent.back()[6]==(2999&255));
    fakeNow+=7000;a.poll();assert(a.busy());reply(u,0x3d,1);a.poll();assert(a.busy());
    fakeNow+=13001;a.poll();assert(!a.busy()&&count(u,0x16)==1);
  }
  { HardwareSerial u; DfPlayerAudio a(u);assert(a.reminder(101));
    reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
    reply(u,0x3d,101);a.poll();assert(!a.busy());
  }
  { HardwareSerial u; DfPlayerAudio a(u); a.begin(); a.test(); fakeNow+=1600; a.poll(); assert(count(u,0x12)==0); }
  { HardwareSerial u; DfPlayerAudio a(u); a.test(); reply(u,0x41,0); a.poll(); fakeNow+=1600; a.poll(); assert(count(u,0x06)==0 && count(u,0x12)==0); }
  { HardwareSerial u; DfPlayerAudio a(u); a.test(); reply(u,0x43,20,true); a.poll(); fakeNow+=1600; a.poll(); assert(count(u,0x06)==0); }
  { HardwareSerial u; DfPlayerAudio a(u); a.test(); reply(u,0x43,20); a.poll(); fakeNow+=251; a.poll(); reply(u,0x43,25); a.poll(); fakeNow+=2000; a.poll(); assert(count(u,0x12)==0); }
  { HardwareSerial u; DfPlayerAudio a(u); ready(u,a); fakeNow+=201; a.poll(); assert(count(u,0x12)==1); reply(u,0x3d,1); a.poll(); fakeNow+=6000; a.poll(); assert(count(u,0x12)==1 && count(u,0x16)==0); }
  { HardwareSerial u; DfPlayerAudio a(u); ready(u,a); fakeNow+=201; a.poll(); fakeNow+=5001; a.poll(); assert(count(u,0x16)==1); }
  { HardwareSerial u; DfPlayerAudio a(u); ready(u,a); a.stop(); fakeNow+=6000; a.poll(); assert(count(u,0x12)==0); }
  { HardwareSerial u; DfPlayerAudio a(u); ready(u,a); fakeNow+=201; a.poll(); reply(u,0x40,6); a.poll(); assert(count(u,0x16)==1); }
  { HardwareSerial u; DfPlayerAudio a(u); a.test(); reply(u,0x43,8); a.poll(); assert(u.sent.back()[6]==30); fakeNow+=251; a.poll(); reply(u,0x43,30); a.poll(); fakeNow+=201; a.poll(); assert(count(u,0x12)==1); }
  { HardwareSerial u; DfPlayerAudio a(u); a.test(31); assert(u.sent.empty()); }
  { HardwareSerial u; DfPlayerAudio a(u); assert(a.motion()); assert(a.motionActive());
    reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
    assert(u.sent.back()[3]==0x12 && u.sent.back()[6]==2);
    reply(u,0x3d,1);a.poll();assert(a.busy()); // Old emergency completion cannot end motion.
    reply(u,0x3d,2);a.poll();assert(!a.busy()); }
  { HardwareSerial u; DfPlayerAudio a(u);a.test();assert(!a.motion());assert(!a.motionActive()); }
  { HardwareSerial u; DfPlayerAudio a(u);a.motion();a.test();assert(a.busy());assert(!a.motionActive());
    reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
    assert(u.sent.back()[3]==0x12 && u.sent.back()[6]==1); }
  { HardwareSerial u; DfPlayerAudio a(u);a.motion();reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
    fakeNow+=5001;a.poll();assert(a.busy());fakeNow+=2000;a.poll();assert(!a.busy() && count(u,0x16)==1); }
  { HardwareSerial u; DfPlayerAudio a(u);a.motion();reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
    assert(a.motionActive());a.test();assert(count(u,0x16)==1 && !a.motionActive());
    reply(u,0x3d,2);a.poll();assert(a.busy());reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
    assert(u.sent.back()[3]==0x12 && u.sent.back()[6]==1); }
  std::cout << "PASS: 17 DFPlayer response/playback/priority scenarios\n";
}
