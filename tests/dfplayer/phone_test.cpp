#include <cassert>
#include <iostream>
#include "../../azure_iot_live/PhoneSpeaker.h"
uint32_t fakeNow = 0;
HardwareSerial Serial;
BluetoothSerial* BluetoothSerial::instance = nullptr;
PhoneSpeaker* currentPhone = nullptr;
void forwardStatus(const char* status) { currentPhone->status(status); }
const char* id = "12345678-1234-1234-1234-123456789abc";
void send(PhoneSpeaker& p, const std::string& line) {
  for(char c : line) BluetoothSerial::instance->incoming.push_back(c);
  while (BluetoothSerial::instance->available()) p.poll();
}
int main() {
  { HardwareSerial u; DfPlayerAudio a(u); PhoneSpeaker p(a);p.begin();currentPhone=&p;a.setListener(forwardStatus);
    send(p,"CARE HELLO\n");assert(BluetoothSerial::instance->output.empty());
    send(p,"HELLO\nCARE HELLO\n");assert(BluetoothSerial::instance->output.find("CARE_MESSAGES 1 READY")!=std::string::npos);
    for(const char* track : {"100","3000","0101","101x","-101","99999"})send(p,std::string("REMIND ")+track+" "+id+"\n");
    assert(u.sent.empty());send(p,std::string("REMIND 101 ")+id+"\n");assert(a.busy()&&a.nonEmergencyActive());
    auto count=u.sent.size();send(p,std::string("REMIND 101 ")+id+"\n");assert(u.sent.size()==count);
    send(p,"SOS 23456789-1234-1234-1234-123456789abc\n");assert(a.busy()&&!a.nonEmergencyActive());
    assert(BluetoothSerial::instance->output.find(std::string(id)+" STOPPED")!=std::string::npos);
    send(p,"REMIND 2999 33456789-1234-1234-1234-123456789abc\n");assert(BluetoothSerial::instance->output.find("BUSY")!=std::string::npos);
  }
  { HardwareSerial u; DfPlayerAudio a(u); PhoneSpeaker p(a);p.begin();
    p.setHealthReporter([](BluetoothSerial& link){link.println("HEALTH 1 1234 72 98 29.5 55.0 30.0 0 1 2");});
    send(p,"HEALTH?\n");assert(BluetoothSerial::instance->output.empty());
    send(p,"HELLO\n");send(p,"HEALTH?\n");
    assert(BluetoothSerial::instance->output.find("HEALTH 1 1234 72 98 29.5 55.0 30.0 0 1 2")!=std::string::npos);
    assert(u.sent.empty()); // A sensor query cannot play a recording.
    send(p,std::string("SOS ")+id+"\n");assert(a.busy());
    auto count=u.sent.size();send(p,"HEALTH?\n");assert(u.sent.size()==count&&a.busy());
  }
  { HardwareSerial u; DfPlayerAudio a(u); PhoneSpeaker p(a); p.begin();
    assert(u.sent.empty()); send(p, std::string("SOS ")+id+"\n"); assert(u.sent.empty());
    send(p,"HELLO\n"); assert(BluetoothSerial::instance->output.find("WHEELCHAIR_SPEAKER 1")!=std::string::npos);
    assert(u.sent.empty()); send(p,std::string("SOS ")+id+"\n"); assert(a.busy() && u.sent.size()==1);
    send(p,std::string("SOS ")+id+"\n"); assert(u.sent.size()==1);
    assert(BluetoothSerial::instance->output.find("DUPLICATE")!=std::string::npos);
    send(p,"SOS 23456789-1234-1234-1234-123456789abc\n");
    assert(BluetoothSerial::instance->output.find("BUSY")!=std::string::npos);
    p.status("PLAY_REQUESTED");p.status("ERROR");
    assert(BluetoothSerial::instance->output.find(std::string(id)+" ERROR")!=std::string::npos);
  }
  { HardwareSerial u; DfPlayerAudio a(u); PhoneSpeaker p(a);p.begin();send(p,"HELLO\n");
    send(p,"SOS bad-id\n");send(p,std::string(100,'X')+"SOS "+id+"\n");assert(u.sent.empty());
    send(p,std::string("SOS ")+id+"\n");assert(a.busy());
    BluetoothSerial::instance->connected=false;p.poll();assert(!a.busy());
    BluetoothSerial::instance->connected=true;send(p,"HELLO\n");send(p,std::string("SOS ")+id+"\n");assert(!a.busy());
  }
  { HardwareSerial u; DfPlayerAudio a(u);PhoneSpeaker p(a);p.begin();send(p,"HELLO\n");
    send(p,"SOS 12345678-");fakeNow+=1001;p.poll();send(p,"1234-1234-1234-123456789abc\n");assert(u.sent.empty());
  }
  { HardwareSerial u; DfPlayerAudio a(u);PhoneSpeaker p(a);p.begin();currentPhone=&p;a.setListener(forwardStatus);
    assert(a.motion());send(p,"HELLO\n");send(p,std::string("SOS ")+id+"\n");
    assert(a.busy()&&!a.motionActive());
    p.status("PLAY_REQUESTED");
    assert(BluetoothSerial::instance->output.find(std::string("SPEAKER ")+id+" PLAY_REQUESTED")!=std::string::npos);
    assert(BluetoothSerial::instance->output.find("STOPPED")==std::string::npos);
  }
  std::cout << "PASS: phone handshake, single request, duplicate, busy, status, invalid/oversized input, disconnect, stale partial command\n";
}
