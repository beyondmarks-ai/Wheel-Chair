package org.wheelchair.blink;

final class HealthState {
    record Live(HealthReading reading,long received,long wallTime) {
        boolean fresh(long now){return now>=received&&now-received<=6000;}
    }
    static volatile Live latest;
    static volatile boolean connected;
    static volatile String status="Start monitoring to connect sensors";
}
