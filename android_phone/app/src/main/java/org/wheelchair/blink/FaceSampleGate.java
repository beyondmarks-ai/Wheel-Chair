package org.wheelchair.blink;

/** Prevent delayed, repeated or different-face contour results from completing a hold. */
final class FaceSampleGate {
    private long consumed=-1;
    static boolean matches(long now,long frame,long sample,double x,double y,double sx,double sy) {
        return sample>=0 && now>=sample && now-sample<=500 && Math.abs(frame-sample)<=500
                && Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(sx)&&Double.isFinite(sy)
                && Math.hypot(x-sx,y-sy)<=.12;
    }
    boolean consume(long sample){if(sample<=consumed)return false;consumed=sample;return true;}
    boolean consumed(long sample){return sample<=consumed;}
}
