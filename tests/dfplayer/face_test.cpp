#include <cassert>
#include <iostream>
#include "../../azure_iot_live/PhoneSpeaker.h"
uint32_t fakeNow=0;
HardwareSerial Serial;
BluetoothSerial* BluetoothSerial::instance=nullptr;
PhoneSpeaker* phone=nullptr;
void forward(const char* s){phone->status(s);}
void send(PhoneSpeaker& p,const std::string& line){
  for(char c:line)BluetoothSerial::instance->incoming.push_back(c);
  while(BluetoothSerial::instance->available())p.poll();
}
void reply(HardwareSerial& u,uint8_t command,uint16_t value){
  uint8_t f[10]={0x7e,0xff,6,command,0,uint8_t(value>>8),uint8_t(value),0,0,0xef};
  uint16_t sum=0;for(int i=1;i<=6;i++)sum+=f[i];uint16_t check=uint16_t(0-sum);
  f[7]=check>>8;f[8]=check;for(auto b:f)u.incoming.push_back(b);
}
void play(HardwareSerial& u,DfPlayerAudio& a){
  reply(u,0x43,30);a.poll();fakeNow+=251;a.poll();reply(u,0x43,30);a.poll();fakeNow+=201;a.poll();
}
const std::string id="12345678-1234-1234-1234-123456789abc";
const std::string other="23456789-1234-1234-1234-123456789abc";
int main(){
  {HardwareSerial u;DfPlayerAudio a(u);PhoneSpeaker p(a);p.begin();send(p,"FACE HELLO\n");
   assert(BluetoothSerial::instance->output.empty());send(p,"HELLO\n");
   assert(BluetoothSerial::instance->output=="WHEELCHAIR_SPEAKER 1\n");
   send(p,"FACE HELLO\n");assert(BluetoothSerial::instance->output.find("FACE_MESSAGES 1 NOT_READY")!=std::string::npos);
   p.setFaceAudioReady(true);send(p,"FACE HELLO\n");assert(BluetoothSerial::instance->output.find("FACE_MESSAGES 1 READY")!=std::string::npos);
   assert(u.sent.empty());}
  {HardwareSerial u;DfPlayerAudio a(u);PhoneSpeaker p(a);p.begin();send(p,"HELLO\nPHRASE 3 "+id+"\n");
   assert(u.sent.empty());assert(BluetoothSerial::instance->output.find("NOT_READY")!=std::string::npos);}
  for(int track=3;track<=5;track++){
    HardwareSerial u;DfPlayerAudio a(u);PhoneSpeaker p(a);p.begin();p.setFaceAudioReady(true);phone=&p;a.setListener(forward);
    send(p,"HELLO\nPHRASE "+std::to_string(track)+" "+id+"\n");assert(a.nonEmergencyActive());
    play(u,a);assert(u.sent.back()[3]==0x12&&u.sent.back()[6]==track);
    send(p,"PHRASE 3 "+other+"\n");assert(BluetoothSerial::instance->output.find(other+" BUSY")!=std::string::npos);
    reply(u,0x3d,track);a.poll();assert(!a.busy());
    assert(BluetoothSerial::instance->output.find(id+" FINISHED")!=std::string::npos);
    auto n=u.sent.size();send(p,"PHRASE "+std::to_string(track)+" "+id+"\n");assert(u.sent.size()==n);
  }
  {HardwareSerial u;DfPlayerAudio a(u);PhoneSpeaker p(a);p.begin();p.setFaceAudioReady(true);phone=&p;a.setListener(forward);
   send(p,"HELLO\nPHRASE 4 "+id+"\n");play(u,a);send(p,"SOS "+other+"\n");
   assert(a.busy()&&!a.nonEmergencyActive());
   assert(BluetoothSerial::instance->output.find(id+" STOPPED")!=std::string::npos);
   play(u,a);assert(u.sent.back()[6]==1);
   reply(u,0x3d,4);a.poll();assert(a.busy());reply(u,0x3d,1);a.poll();assert(!a.busy());
   assert(BluetoothSerial::instance->output.find(other+" FINISHED")!=std::string::npos);}
  {HardwareSerial u;DfPlayerAudio a(u);PhoneSpeaker p(a);p.begin();p.setFaceAudioReady(true);send(p,"HELLO\n");
   for(auto prefix:{"PHRASE 1 ","PHRASE 2 ","PHRASE 6 ","PHRASE 03 ","PHRASE -3 "})send(p,std::string(prefix)+id+"\n");
   assert(u.sent.empty());send(p,"PHRASE 3 "+id+"\n");assert(a.busy());
   BluetoothSerial::instance->connected=false;p.poll();assert(!a.busy());
   BluetoothSerial::instance->connected=true;send(p,"PHRASE 4 "+other+"\n");assert(!a.busy());}
  {HardwareSerial u;DfPlayerAudio a(u);a.test();assert(!a.phrase(3));assert(!a.phrase(1));assert(!a.phrase(6));}
  std::cout<<"PASS: phrase readiness, tracks 3-5, completion, busy, duplicates, emergency preemption, input validation, disconnect\n";
}
