package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class FaceMessagesTest {
    @Test public void contourCadenceAcceptsDistinct400msSamplesButRejectsLongGaps(){
        FaceMessages f=new FaceMessages(true,600);
        for(long t=0;t<=1600;t+=400)assertNull(f.update(t,true,true,.05,.05,.05,.05,.05,.05,.05,.05));
        assertTrue(f.feedback.startsWith("Ready"));
        for(long t=2000;t<=2800;t+=400)assertNull(f.update(t,true,true,.05,.05,.05,.05,.8,.05,.05,.05));
        assertEquals(FaceMessages.Message.HELP,f.update(3200,true,true,.05,.05,.05,.05,.8,.05,.05,.05));
        FaceMessages lost=new FaceMessages(true,600);
        for(long t=0;t<=1600;t+=400)lost.update(t,true,true,.05,.05,.05,.05,.05,.05,.05,.05);
        assertNull(lost.update(2400,true,true,.05,.05,.05,.05,.8,.05,.05,.05));
        assertFalse(lost.feedback.startsWith("Ready"));
    }
    @Test public void liveSignalsAreVisibleWithoutSpeakerAndRejectMissingScores(){
        FaceMessages f=new FaceMessages();
        for(int i=0;i<3;i++)f.update(now+=100,true,false,.05,.05,.05,.05,.8,.05,.05,.05);
        assertTrue(f.signals().contains("Mouth: open"));
        f.update(now+=100,false,false,.05,.05,.05,.05,.8,.05,.05,.05);
        assertTrue(f.signals().contains("stabilizing"));
        for(int i=0;i<3;i++)f.update(now+=100,true,false,.05,.05,.05,.05,.8,.05,.05,.05);
        f.update(now+=100,true,false,.05,.05,.05,.05,Double.NaN,.05,.05,.05);
        assertTrue(f.signals().contains("stabilizing"));
    }
    @Test public void ordinaryBlinkDoesNotAddSequenceTimeoutToMouthHold(){
        FaceMessages f=new FaceMessages();Gesture g=new Gesture();long time=0;
        for(int i=0;i<15;i++){
            time+=100;g.update(time,.05,.05,true);f.update(time,true,true,.05,.05,.05,.05,.05,.05,.05,.05);
        }
        for(int i=0;i<3;i++){
            time+=100;g.update(time,.8,.8,true);assertNull(f.update(time,true,true,.8,.8,.05,.05,.05,.05,.05,.05));
        }
        for(int i=0;i<9;i++){
            time+=100;assertFalse(g.update(time,.05,.05,true));assertNull(f.update(time,true,true,.05,.05,.05,.05,.05,.05,.05,.05));
        }
        assertEquals(1,g.blinks.size()); // Normal blink sequence is still pending.
        assertTrue(f.feedback.startsWith("Ready"));
        FaceMessages.Message message=null;
        for(int i=0;i<17;i++){
            time+=100;assertFalse(g.update(time,.05,.05,true));
            message=f.update(time,true,true,.05,.05,.05,.05,.8,.05,.05,.05);
            if(i<16)assertNull(message);
        }
        assertEquals(FaceMessages.Message.HELP,message);
    }
    private long now;
    private FaceMessages.Message step(FaceMessages f,int type,boolean valid,boolean available) {
        now+=100;
        return f.update(now,valid,available,.05,.05,type==3?.8:.05,type==3?.8:.05,
                type==4?.8:.05,type==5?.8:.05,type==5?.7:.05,type==5?.7:.05);
    }
    private void neutral(FaceMessages f){for(int i=0;i<10;i++)assertNull(step(f,0,true,true));}
    @Test public void allMappingsNeedHoldAndDoNotRepeat(){
        for(var m:FaceMessages.Message.values()) {
            FaceMessages f=new FaceMessages();neutral(f);
            for(int i=0;i<16;i++)assertNull(step(f,m.track,true,true));
            assertEquals(m,step(f,m.track,true,true));
            for(int i=0;i<100;i++)assertNull(step(f,m.track,true,true));
            neutral(f);for(int i=0;i<16;i++)assertNull(step(f,m.track,true,true));
            assertEquals(m,step(f,m.track,true,true));
        }
    }
    @Test public void expressionAtStartupCannotSpeak(){
        FaceMessages f=new FaceMessages();for(int i=0;i<100;i++)assertNull(step(f,3,true,true));
    }
    @Test public void briefExpressionIgnored(){
        FaceMessages f=new FaceMessages();neutral(f);
        for(int i=0;i<8;i++)assertNull(step(f,4,true,true));
        neutral(f);for(int i=0;i<16;i++)assertNull(step(f,4,true,true));
        assertEquals(FaceMessages.Message.HELP,step(f,4,true,true));
    }
    @Test public void lostFaceBusyOrGapRequiresFreshNeutral(){
        for(int mode=0;mode<3;mode++){
            FaceMessages f=new FaceMessages();neutral(f);
            for(int i=0;i<12;i++)assertNull(step(f,3,true,true));
            if(mode==2)now+=500;
            assertNull(step(f,3,mode!=0,mode!=1));
            for(int i=0;i<30;i++)assertNull(step(f,3,true,true));
        }
    }
    @Test public void ambiguousGesturesNeverSpeak(){
        FaceMessages f=new FaceMessages();neutral(f);
        for(int i=0;i<40;i++)assertNull(f.update(now+=100,true,true,.05,.05,.8,.8,.8,.05,.05,.05));
    }
    @Test public void blinksAndInvalidScoresCancelHold(){
        for(double score:new double[]{.8,Double.NaN,1.1,-.1}) {
            FaceMessages f=new FaceMessages();neutral(f);
            for(int i=0;i<12;i++)assertNull(step(f,3,true,true));
            assertNull(f.update(now+=100,true,true,score,score,.8,.8,.05,.05,.05,.05));
            for(int i=0;i<30;i++)assertNull(step(f,3,true,true));
        }
    }
    @Test public void cooldownCannotBeBypassedWithNeutralOrReset(){
        FaceMessages f=new FaceMessages();neutral(f);
        for(int i=0;i<16;i++)step(f,5,true,true);
        assertEquals(FaceMessages.Message.COME,step(f,5,true,true));
        f.reset("lost face");neutral(f);
        for(int i=0;i<30;i++)assertNull(step(f,5,true,true));
    }
    @Test public void switchGestureRestartsHold(){
        FaceMessages f=new FaceMessages();neutral(f);
        for(int i=0;i<12;i++)assertNull(step(f,3,true,true));
        for(int i=0;i<16;i++)assertNull(step(f,4,true,true));
        assertEquals(FaceMessages.Message.HELP,step(f,4,true,true));
    }
}
