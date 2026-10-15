package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class FaceCalibrationTest {
    private static double[] relaxed(){return new double[]{.10,.12,.25,.25,.15,.40,.35,.35};}
    private long time;
    private boolean capture(FaceCalibration c,double ratio) {
        boolean saved=false;for(int i=0;i<21;i++)saved=c.update(time+=100,true,ratio,relaxed());return saved;
    }
    private FaceCalibration calibrated(){
        FaceCalibration c=new FaceCalibration();c.start(time);assertFalse(capture(c,.05));
        assertEquals(FaceCalibration.Stage.WAIT_OPEN,c.stage);c.captureOpen(time);
        assertTrue(capture(c,.35));assertEquals(FaceCalibration.Stage.IDLE,c.stage);return c;
    }
    @Test public void twoExplicitCapturesRequired(){
        FaceCalibration c=new FaceCalibration();c.start(0);c.captureOpen(0);
        assertEquals(FaceCalibration.Stage.RELAXED,c.stage);assertFalse(capture(c,.05));
        for(int i=0;i<40;i++)assertFalse(c.update(time+=100,true,.4,relaxed()));
        assertNull(c.profile);assertEquals(FaceCalibration.Stage.WAIT_OPEN,c.stage);
        c.captureOpen(time);assertTrue(capture(c,.35));assertNotNull(c.profile);
    }
    @Test public void weakJawScoreAndRaisedNeutralBrowsCanStillDetectMouth(){
        FaceCalibration c=calibrated();FaceMessages f=new FaceMessages();
        double[] scores=c.profile.normalize(.05,relaxed());
        for(int i=0;i<10;i++)assertNull(update(f,scores));
        assertTrue(f.feedback.startsWith("Ready"));
        scores=c.profile.normalize(.35,relaxed()); // Model jaw remains .15, far below old .65.
        for(int i=0;i<16;i++)assertNull(update(f,scores));
        assertEquals(FaceMessages.Message.HELP,update(f,scores));
        for(int i=0;i<100;i++)assertNull(update(f,scores));
    }
    private FaceMessages.Message update(FaceMessages f,double[] s){return f.update(time+=100,true,true,s[0],s[1],s[2],s[3],s[4],s[5],s[6],s[7]);}
    @Test public void similarPositionsRejectedAndOldProfilePreserved(){
        FaceCalibration c=calibrated();var old=c.profile;c.start(time);capture(c,.05);c.captureOpen(time);
        assertFalse(capture(c,.10));assertSame(old,c.profile);assertTrue(c.feedback.contains("too similar"));
    }
    @Test public void interruptionRestartsCaptureAndMissingVideoTimesOut(){
        FaceCalibration c=new FaceCalibration();c.start(0);
        for(int i=0;i<15;i++)c.update(time+=100,true,.05,relaxed());
        c.update(time+=100,false,.05,relaxed());
        for(int i=0;i<10;i++)c.update(time+=100,true,.05,relaxed());
        assertEquals(FaceCalibration.Stage.RELAXED,c.stage);
        c.tick(15000);assertFalse(c.active());assertNull(c.profile);assertTrue(c.feedback.contains("timed out"));
    }
    @Test public void staleOrReorderedSamplesCannotFinishCapture(){
        for(long shift:new long[]{500,-200}) {
            FaceCalibration c=new FaceCalibration();time=0;c.start(0);
            for(int i=0;i<15;i++)c.update(time+=100,true,.05,relaxed());
            time+=shift;c.update(time,true,.05,relaxed());
            for(int i=0;i<10;i++)c.update(time+=100,true,.05,relaxed());
            assertEquals(FaceCalibration.Stage.RELAXED,c.stage);assertNull(c.profile);
        }
    }
    @Test public void unstableCaptureAndClosedEyesRejected(){
        FaceCalibration c=new FaceCalibration();c.start(0);
        for(int i=0;i<21;i++)c.update(time+=100,true,i%2==0?.05:.3,relaxed());
        assertFalse(c.active());assertNull(c.profile);
        c.start(time);double[] closed=relaxed();closed[0]=closed[1]=.8;
        for(int i=0;i<21;i++)c.update(time+=100,true,.05,closed);
        assertFalse(c.active());assertNull(c.profile);
    }
    @Test public void ratiosAreScaleAndRollInvariantAndRejectLowResolution(){
        double r=FaceCalibration.mouthRatio(0,0,40,0,20,0,20,12);
        assertEquals(.3,r,.0001);
        assertEquals(r,FaceCalibration.mouthRatio(0,0,80,0,40,0,40,24),.0001);
        assertEquals(r,FaceCalibration.mouthRatio(0,0,0,40,0,20,-12,20),.0001);
        assertTrue(Double.isNaN(FaceCalibration.mouthRatio(0,0,10,0,5,0,5,3)));
        assertTrue(Double.isNaN(FaceCalibration.mouthRatio(0,0,40,0,20,Double.NaN,20,12)));
    }
    @Test public void roundTripAndCorruptProfiles(){
        var p=calibrated().profile;var restored=FaceCalibration.Profile.decode(p.encode());
        assertNotNull(restored);assertEquals(p.closed,restored.closed,0);assertEquals(p.open,restored.open,0);
        assertArrayEquals(p.baseline,restored.baseline,0);
        for(String invalid:new String[]{"","bad","1,0,0,0,0,0,0,0,0,0,0","1,NaN,.4,0,0,0,0,0,0,0,0"})assertNull(FaceCalibration.Profile.decode(invalid));
    }
    @Test public void closedEyesAndMissingMouthNeverBecomeValidAfterNormalization(){
        var p=calibrated().profile;double[] scores=relaxed();scores[0]=scores[1]=.50;
        var out=p.normalize(.35,scores);assertTrue(out[0]>=.5);assertTrue(out[1]>=.5);
        assertTrue(Double.isNaN(p.normalize(Double.NaN,scores)[4]));
        scores[2]=-1;assertTrue(Double.isNaN(p.normalize(.35,scores)[2]));
    }
    @Test public void cancelDoesNotReplaceSavedCalibration(){
        FaceCalibration c=calibrated();var old=c.profile;c.start(time);c.cancel();
        assertFalse(c.active());assertSame(old,c.profile);
    }
}
