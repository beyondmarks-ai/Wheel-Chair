#include <cassert>
#include <iostream>
#include <limits>
#include "../../azure_iot_live/MotionDetector.h"
struct Rig {
  MotionDetector detector;
  uint32_t now=0; int events=0;
  void feed(int ms,float ax=0,float ay=0,float az=1,float gx=0,float gy=0,float gz=0) {
    for(int i=0;i<ms;i+=20) { now+=20; if(detector.update(now,ax,ay,az,gx,gy,gz))events++; }
  }
  void rest() { feed(2200); assert(detector.calibrated());assert(events==0); }
};
int main() {
  { Rig r;r.rest();r.feed(30000);assert(r.events==0); }
  { Rig r;r.feed(2400,.6f,0,.8f);assert(r.detector.calibrated());r.feed(10000,.6f,0,.8f);assert(r.events==0); }
  { Rig r;r.feed(3000,0,0,1,25);assert(!r.detector.calibrated());assert(r.events==0);r.rest(); }
  { Rig r;r.rest();r.feed(40,.4f);r.feed(3000);assert(r.events==0); }
  { Rig r;r.rest();r.feed(200,.3f);assert(r.events==1); }
  { Rig r;r.rest();r.feed(12000,0,0,1,0,0,30);assert(r.events==1);r.feed(2500);r.feed(200,0,0,1,0,0,30);assert(r.events==2); }
  { Rig r;r.rest();r.feed(200,0,0,1,0,0,30);r.feed(2000);r.feed(200,0,0,1,0,0,30);assert(r.events==1);r.feed(10000);r.feed(200,0,0,1,0,0,30);assert(r.events==2); }
  { Rig r;r.rest();r.feed(1000,1,0,0,0,30,0);r.feed(9000,1,0,0);assert(!r.detector.moving());assert(r.events==1); }
  { Rig r;r.rest();assert(!r.detector.update(r.now+20,0,0,0,0,0,0));assert(!r.detector.calibrated()); }
  { Rig r;r.rest();assert(!r.detector.update(r.now+20,std::numeric_limits<float>::quiet_NaN(),0,1,0,0,0));assert(!r.detector.calibrated()); }
  { Rig r;r.rest();r.now+=1000;r.feed(200,.4f);assert(!r.detector.calibrated());assert(r.events==0); }
  { Rig r;r.now=0xfffff000u;r.rest();r.feed(200,0,0,1,0,0,30);r.feed(12000);r.feed(200,0,0,1,0,0,30);assert(r.events==2); }
  { Rig r;r.feed(2400,0,0,1,2,-1,1);assert(r.detector.calibrated());r.feed(10000,0,0,1,2,-1,1);assert(r.events==0); }
  std::cout<<"PASS: 13 motion calibration, acceleration, rotation, spike, cooldown, rearm, invalid-input and rollover scenarios\n";
}
