package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class FoundryGestureGateTest {
    @Test public void loweredBrowsCanRearmAndSmallJitterKeepsCandidate(){
        var gate=new FoundryGestureGate(.03,.16);
        for(long t=0;t<=1000;t+=100)gate.observe(t,true,.03,.12);
        var ticket=mouth(gate);assertNotNull(ticket);
        gate.observe(1600,true,.056,.16);
        assertEquals(FaceMessages.Message.HELP,gate.accept(ticket,1600,"open","neutral",true));
    }
    private FoundryGestureGate armed(){
        var gate=new FoundryGestureGate(.03,.16);
        for(long t=0;t<=1000;t+=100)gate.observe(t,true,.03,.16);
        return gate;
    }
    private FoundryGestureGate.Ticket mouth(FoundryGestureGate gate){
        for(long t=1100;t<=1500;t+=100)gate.observe(t,true,.14,.16);
        return gate.request(1500);
    }
    @Test public void positiveNeedsLiveMatchingGestureAndOnlyPlaysOnce(){
        var gate=armed();var ticket=mouth(gate);assertNotNull(ticket);
        for(long t=1600;t<=3500;t+=100)gate.observe(t,true,.14,.16);
        assertEquals(FaceMessages.Message.HELP,gate.accept(ticket,3500,"open","neutral",true));
        assertNull(gate.accept(ticket,3501,"open","neutral",true));
        gate.observe(3700,true,.14,.16);assertNull(gate.request(3700));
    }
    @Test public void noFaceOrBlinkCancelsInFlightResult(){
        var gate=armed();var ticket=mouth(gate);
        gate.observe(1600,false,.14,.16);
        gate.observe(1700,true,.14,.16);
        assertNull(gate.accept(ticket,1700,"open","neutral",true));
    }
    @Test public void ReturningToNeutralCancelsOldCloudAnswer(){
        var gate=armed();var ticket=mouth(gate);
        gate.observe(1600,true,.03,.16);
        gate.observe(1700,true,.14,.16);
        assertNull(gate.accept(ticket,1700,"open","neutral",true));
    }
    @Test public void unknownCombinedAndInvisibleNeverSpeak(){
        var gate=armed();var ticket=mouth(gate);
        assertNull(gate.accept(ticket,1500,"open","unknown",true));
        assertNull(gate.accept(ticket,1500,"open","raised",true));
        assertNull(gate.accept(ticket,1500,"open","neutral",false));
    }
    @Test public void responseOlderThanFourSecondsRejectedEvenWithLiveGesture(){
        var gate=armed();var ticket=mouth(gate);
        for(long t=1600;t<=5600;t+=100)gate.observe(t,true,.14,.16);
        assertNull(gate.accept(ticket,5600,"open","neutral",true));
    }
    @Test public void missingCurrentVideoRejectsCloudResponse(){
        var gate=armed();var ticket=mouth(gate);
        assertNull(gate.accept(ticket,2100,"open","neutral",true));
    }
    @Test public void eyebrowsUseSeparatePhrase(){
        var gate=armed();
        for(long t=1100;t<=1500;t+=100)gate.observe(t,true,.03,.21);
        var ticket=gate.request(1500);assertNotNull(ticket);
        assertEquals(FaceMessages.Message.COME,gate.accept(ticket,1500,"closed","raised",true));
    }
    @Test public void openingMouthWithoutNeutralNeverArms(){
        var gate=new FoundryGestureGate(.03,.16);
        for(long t=0;t<=3000;t+=100)gate.observe(t,true,.14,.16);
        assertNull(gate.request(3000));
    }
    @Test public void videoGapRequiresNeutralAgain(){
        var gate=armed();var ticket=mouth(gate);
        gate.observe(2200,true,.14,.16);
        assertNull(gate.accept(ticket,2200,"open","neutral",true));
        assertNull(gate.request(2200));
    }
}
