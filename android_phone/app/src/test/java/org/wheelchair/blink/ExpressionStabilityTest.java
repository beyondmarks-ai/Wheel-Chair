package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExpressionStabilityTest {
    private long time;
    private FaceMessages.Message sample(FaceMessages f,double jaw,double inner,double outer){
        return f.update(time+=100,true,true,.05,.05,.05,.05,jaw,inner,outer,outer);
    }
    private void neutral(FaceMessages f){for(int i=0;i<12;i++)assertNull(sample(f,.05,.05,.05));}
    @Test public void mouthThresholdJitterCanCompleteOnce(){
        FaceMessages f=new FaceMessages();neutral(f);int events=0;
        for(int i=0;i<22;i++){
            var message=sample(f,i%2==0?.69:.61,.05,.05);
            if(message!=null){assertEquals(FaceMessages.Message.HELP,message);events++;}
            if(i>=2)assertTrue(f.signals().contains("Mouth: open"));
        }
        assertEquals(1,events);
        for(int i=0;i<80;i++)assertNull(sample(f,.69,.05,.05));
    }
    @Test public void eyebrowThresholdJitterCanCompleteOnce(){
        FaceMessages f=new FaceMessages();neutral(f);int events=0;
        for(int i=0;i<24;i++){
            var message=sample(f,.05,i%2==0?.6:.52,i%2==0?.4:.32);
            if(message!=null){assertEquals(FaceMessages.Message.COME,message);events++;}
            if(i>=2)assertTrue(f.signals().contains("Brows: raised"));
        }
        assertEquals(1,events);
    }
    @Test public void isolatedDropDoesNotCancelARealHold(){
        FaceMessages f=new FaceMessages();neutral(f);int events=0;
        for(int i=0;i<20;i++)if(sample(f,i==8?.05:.85,.05,.05)!=null)events++;
        assertEquals(1,events);
    }
    @Test public void clearClosureCancelsRatherThanAccumulatesHolds(){
        FaceMessages f=new FaceMessages();neutral(f);
        for(int cycle=0;cycle<10;cycle++){
            for(int i=0;i<7;i++)assertNull(sample(f,.85,.05,.05));
            for(int i=0;i<5;i++)assertNull(sample(f,.05,.05,.05));
        }
    }
    @Test public void singleSpikesAndFullRangeFlippingCannotTrigger(){
        FaceMessages f=new FaceMessages();neutral(f);
        for(int i=0;i<100;i++)assertNull(sample(f,i%5==0?.9:.05,.05,.05));
        for(int i=0;i<100;i++)assertNull(sample(f,i%2==0?.9:.05,.05,.05));
    }
    @Test public void invalidGapDropsPreviouslyLatchedStates(){
        FaceMessages f=new FaceMessages();neutral(f);
        for(int i=0;i<5;i++)sample(f,.8,.05,.05);
        assertTrue(f.signals().contains("Mouth: open"));
        time+=600;assertNull(sample(f,.8,.05,.05));assertTrue(f.signals().contains("stabilizing"));
        for(int i=0;i<40;i++)assertNull(sample(f,.8,.05,.05)); // New neutral required.
    }
    @Test public void releaseThresholdActuallyClosesAndBrowsRelease(){
        ExpressionStability f=new ExpressionStability();
        for(int i=0;i<3;i++)f.update(.7,.7,.8,.7,.5,.5);
        assertTrue(f.mouth&&f.brows&&f.smile);
        for(int i=0;i<3;i++)f.update(.39,.39,.44,.39,.24,.24);
        assertFalse(f.mouth||f.brows||f.smile);
    }
}
