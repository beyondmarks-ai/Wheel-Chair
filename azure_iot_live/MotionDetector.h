#pragma once
#include <cmath>
#include <cstdint>

// Acceleration in g; angular velocity in degrees/second. No blocking or IO.
class MotionDetector {
 public:
  void reset() {
    calibrated_ = initialized_ = moving_ = false;
    armed_ = true; stableMs_ = activeMs_ = quietMs_ = 0;
    linearG_ = rotationDps_ = 0;
  }
  bool update(uint32_t now, float ax, float ay, float az,
              float gx, float gy, float gz, bool valid = true) {
    if (!valid || !std::isfinite(ax) || !std::isfinite(ay) || !std::isfinite(az) ||
        !std::isfinite(gx) || !std::isfinite(gy) || !std::isfinite(gz) ||
        norm(ax,ay,az) < .1f) { reset(); return false; }
    if (initialized_ && uint32_t(now - previousMs_) > 150) reset();
    if (!initialized_) {
      initialized_ = true; previousMs_ = now;
      gravity_[0]=previousA_[0]=ax; gravity_[1]=previousA_[1]=ay; gravity_[2]=previousA_[2]=az;
      bias_[0]=gx; bias_[1]=gy; bias_[2]=gz;
      return false;
    }
    uint32_t dt = now - previousMs_; previousMs_ = now;
    if (!dt) return false;
    float magnitude = norm(ax,ay,az);
    if (!calibrated_) {
      bool still = magnitude > .8f && magnitude < 1.2f && norm(gx,gy,gz) < 5 &&
          norm(ax-previousA_[0],ay-previousA_[1],az-previousA_[2]) < .04f;
      previousA_[0]=ax; previousA_[1]=ay; previousA_[2]=az;
      if (!still) { stableMs_=0; gravity_[0]=ax;gravity_[1]=ay;gravity_[2]=az;bias_[0]=gx;bias_[1]=gy;bias_[2]=gz;return false; }
      stableMs_ += dt;
      float alpha = float(dt) / float(stableMs_);
      gravity_[0]+=(ax-gravity_[0])*alpha; gravity_[1]+=(ay-gravity_[1])*alpha; gravity_[2]+=(az-gravity_[2])*alpha;
      bias_[0]+=(gx-bias_[0])*alpha; bias_[1]+=(gy-bias_[1])*alpha; bias_[2]+=(gz-bias_[2])*alpha;
      if (stableMs_ >= 2000) calibrated_ = true;
      return false;
    }
    linearG_ = norm(ax-gravity_[0],ay-gravity_[1],az-gravity_[2]);
    rotationDps_ = norm(gx-bias_[0],gy-bias_[1],gz-bias_[2]);
    // Adapt gravity so stopping in a new orientation can become still again.
    float alpha = float(dt)/(1000.0f+dt);
    gravity_[0]+=(ax-gravity_[0])*alpha; gravity_[1]+=(ay-gravity_[1])*alpha; gravity_[2]+=(az-gravity_[2])*alpha;
    bool active = linearG_ >= .12f || rotationDps_ >= 15;
    bool quiet = linearG_ < .06f && rotationDps_ < 6;
    if (quiet) {
      if (quietMs_ < 1500) quietMs_ += dt;
      if (quietMs_ >= 1500) { armed_=true; moving_=false; }
    } else quietMs_=0;
    if (!active) { activeMs_=0; return false; }
    if (activeMs_ < 100) activeMs_ += dt;
    if (activeMs_ < 100 || !armed_) return false;
    moving_=true; armed_=false;
    if (hasEvent_ && uint32_t(now-lastEventMs_) < 10000) return false;
    hasEvent_=true; lastEventMs_=now; ++events_;
    return true;
  }
  bool calibrated() const { return calibrated_; }
  bool moving() const { return moving_; }
  float linearG() const { return linearG_; }
  float rotationDps() const { return rotationDps_; }
  uint32_t events() const { return events_; }
 private:
  bool initialized_=false, calibrated_=false, armed_=true, moving_=false, hasEvent_=false;
  uint32_t previousMs_=0, stableMs_=0, activeMs_=0, quietMs_=0, lastEventMs_=0, events_=0;
  float gravity_[3]={}, previousA_[3]={}, bias_[3]={}, linearG_=0, rotationDps_=0;
  static float norm(float x,float y,float z) { return std::sqrt(x*x+y*y+z*z); }
};
