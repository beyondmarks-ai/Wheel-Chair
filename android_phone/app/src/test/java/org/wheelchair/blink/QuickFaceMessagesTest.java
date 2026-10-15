package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuickFaceMessagesTest {
    private long time;
    private FaceMessages.Message step(FaceMessages f,int track,double eyes) {
        return f.update(time+=100,true,true,eyes,eyes,track==3?.8:.05,track==3?.8:.05,
                track==4?.8:.05,track==5?.8:.05,track==5?.7:.05,track==5?.7:.05);
    }
    private void neutral(FaceMessages f){for(int i=0;i<12;i++)assertNull(step(f,0,.05));}
    @Test public void quickMouthAndBrowsNeedShorterStableHoldsButSmileStaysDeliberate() {
        for(int track=3;track<=5;track++) {
            var f=new FaceMessages(true);neutral(f);
            int frames=track==4?7:track==5?9:17; // Includes median filter entry frame.
            for(int i=1;i<frames;i++)assertNull(step(f,track,.05));
            assertEquals(FaceMessages.Message.values()[track-3],step(f,track,.05));
            for(int i=0;i<100;i++)assertNull(step(f,track,.05));
        }
    }
    @Test public void transientMovementAndClosedEyesCannotTriggerQuickMode() {
        var f=new FaceMessages(true);neutral(f);
        for(int i=0;i<3;i++)assertNull(step(f,4,.05));
        neutral(f);
        for(int i=0;i<5;i++)assertNull(step(f,4,.05));
        assertNull(step(f,4,.8));
        for(int i=0;i<30;i++)assertNull(step(f,4,.05));
    }
    @Test public void missingFramesAndCooldownStillRequireFreshNeutral() {
        var f=new FaceMessages(true);neutral(f);
        for(int i=0;i<5;i++)assertNull(step(f,5,.05));
        time+=500;assertNull(step(f,5,.05));
        for(int i=0;i<20;i++)assertNull(step(f,5,.05));
        neutral(f);for(int i=0;i<8;i++)assertNull(step(f,5,.05));
        assertEquals(FaceMessages.Message.COME,step(f,5,.05));
        f.reset("Tracking interrupted");neutral(f);
        for(int i=0;i<20;i++)assertNull(step(f,4,.05));
    }
}
