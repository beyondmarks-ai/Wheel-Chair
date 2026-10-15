package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class GestureTest {
    Gesture gesture = new Gesture(); long time = 0; int events = 0;
    void feed(int ms, double left, double right, boolean valid) {
        for (int t = 0; t < ms; t += 50) { time += 50; if (gesture.update(time, left, right, valid)) events++; }
    }
    void open(int ms) { feed(ms, .06, .02, true); }
    void close(int ms) { feed(ms, .60, .61, true); }
    @Test public void comfortableTriple() { open(1000); for (int i=0;i<3;i++) { close(300); open(1300); } assertEquals(1,events); }
    @Test public void slowTriple() { open(1000); for(int i=0;i<3;i++){close(950);open(400);} assertEquals(1,events); }
    @Test public void twoBlinksNotEnough() { open(1000); for(int i=0;i<2;i++){close(200);open(400);} assertEquals(0,events); }
    @Test public void spikesIgnored() { open(1000); for(int i=0;i<3;i++){close(50);open(400);} assertEquals(0,gesture.completed); }
    @Test public void winksIgnored() { open(1000); for(int i=0;i<3;i++){feed(200,.9,.1,true);open(400);} assertEquals(0,events); }
    @Test public void spacedBlinksIgnored() { open(1000); for(int i=0;i<3;i++){close(200);open(2200);} assertEquals(0,events); }
    @Test public void sustainedClosureIgnored() { open(1000);close(2000);open(500);assertEquals(0,gesture.completed); }
    @Test public void faceLossClearsCount() { open(1000);for(int i=0;i<2;i++){close(200);open(400);}feed(50,0,0,false);open(1000);close(200);open(400);assertEquals(0,events); }
    @Test public void frameGapClearsCount() { open(1000);close(200);open(400);time+=1000;open(1000);for(int i=0;i<2;i++){close(200);open(400);}assertEquals(0,events); }
    @Test public void startsClosedUnarmed() { close(2000);open(500);assertEquals(0,gesture.completed); }
    @Test public void cooldownPreventsDuplicate() { open(1000);for(int i=0;i<6;i++){close(200);open(400);}assertEquals(1,events); }
    @Test public void invalidScoresReset() { open(1000);close(200);open(400);feed(50,Double.NaN,.1,true);assertEquals(0,gesture.blinks.size()); }
    @Test public void thirdCountEmitsBeforeFurtherConfirmationFrames() {
        open(1000);
        for(int i=0;i<2;i++){close(200);open(400);}
        close(200);open(150);
        assertEquals(3,gesture.completed);
        assertEquals("Every displayed third count must emit an alert",1,events);
        feed(50,.42,.42,true); // ambiguity after counting must not cancel an emitted alert
        assertEquals(1,events);
    }
    @Test public void thirdClosureAloneDoesNotEmit() {
        open(1000);for(int i=0;i<2;i++){close(200);open(400);}
        close(200);open(50);assertEquals(0,events);assertEquals(2,gesture.completed);
        open(100);assertEquals(1,events);assertEquals(3,gesture.completed);
    }
}
