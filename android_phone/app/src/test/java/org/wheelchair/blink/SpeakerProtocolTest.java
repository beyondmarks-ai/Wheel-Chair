package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeakerProtocolTest {
    @Test public void careCommandHasLongerDeadlineAndStillYieldsToEmergency(){
        SpeakerProtocol p=new SpeakerProtocol();assertEquals("REMIND 101 "+ID+"\n",p.begin(ID,0,101));
        assertFalse(p.expire(10000));assertFalse(p.expire(29999));assertEquals("SOS "+OTHER+"\n",p.begin(OTHER,29999,1));
        p.receive("SPEAKER "+ID+" STOPPED");assertTrue(p.emergencyBusy());p.receive("SPEAKER "+OTHER+" FINISHED");assertFalse(p.busy());
    }
    @Test public void careTrackBoundsAndTimeout(){
        SpeakerProtocol p=new SpeakerProtocol();for(int invalid:new int[]{0,2,6,100,3000,-1})assertNull(p.begin(ID,0,invalid));
        assertNotNull(p.begin(ID,0,2999));assertTrue(p.expire(30000));assertNull(p.begin(ID,30001,2999));
    }
    @Test public void phrasesSelectTrackAndEmergencyPreempts(){
        for(int track=3;track<=5;track++) {
            SpeakerProtocol p=new SpeakerProtocol();assertEquals("PHRASE "+track+" "+ID+"\n",p.begin(ID,0,track));
            assertTrue(p.busy());assertFalse(p.emergencyBusy());assertNull(p.begin(OTHER,1,4));
            assertEquals("SOS "+OTHER+"\n",p.begin(OTHER,2));assertTrue(p.emergencyBusy());
            p.receive("SPEAKER "+ID+" STOPPED");assertTrue(p.busy());
            p.receive("SPEAKER "+OTHER+" FINISHED");assertFalse(p.busy());
        }
    }
    @Test public void invalidTrackAndMissingRecordings(){
        SpeakerProtocol p=new SpeakerProtocol();assertNull(p.begin(ID,0,2));assertNull(p.begin(ID,0,6));
        assertNotNull(p.begin(ID,0,3));p.receive("SPEAKER "+ID+" NOT_READY");assertFalse(p.busy());
        assertTrue(p.status().contains("not enabled"));assertNotNull(p.begin(OTHER,1));assertNull(p.begin(ID,2,3));
    }
    static final String ID="12345678-1234-1234-1234-123456789abc", OTHER="23456789-1234-1234-1234-123456789abc";
    @Test public void requestAndConfirmedFinish() {
        SpeakerProtocol p=new SpeakerProtocol(); assertEquals("SOS "+ID+"\n",p.begin(ID,0));
        p.receive("SPEAKER "+ID+" CHECKING");assertTrue(p.status().contains("checking"));
        p.receive("SPEAKER "+ID+" PLAY_REQUESTED");assertTrue(p.status().contains("requested"));
        p.receive("SPEAKER "+ID+" FINISHED");assertTrue(p.status().contains("reports"));assertNotNull(p.begin(OTHER,1));
    }
    @Test public void unrelatedRepliesCannotComplete() {
        SpeakerProtocol p=new SpeakerProtocol();p.begin(ID,0);p.receive("SPEAKER "+OTHER+" FINISHED");
        p.receive("SPEAKER "+ID+" MADE_UP");assertNull(p.begin(OTHER,1));assertTrue(p.expire(10000));
    }
    @Test public void onlyOnePendingAndNoDuplicates() {
        SpeakerProtocol p=new SpeakerProtocol();p.begin(ID,0);assertNull(p.begin(OTHER,0));
        p.receive("SPEAKER "+ID+" UNCONFIRMED");assertNull(p.begin(ID,1));assertNotNull(p.begin(OTHER,1));
    }
    @Test public void timeoutNeverClaimsPlayback() {
        SpeakerProtocol p=new SpeakerProtocol();p.begin(ID,0);assertFalse(p.expire(9999));assertTrue(p.expire(10000));
        assertTrue(p.status().contains("unconfirmed"));assertNull(p.begin(ID,10001));
    }
    @Test public void disconnectedRequestNotReplayed() {
        SpeakerProtocol p=new SpeakerProtocol();p.begin(ID,0);p.disconnect();assertNull(p.begin(ID,1));
        p.receive("SPEAKER "+ID+" FINISHED");assertTrue(p.status().contains("not confirmed"));assertNotNull(p.begin(OTHER,2));
    }
    @Test public void failuresReleasePending() {
        for(String result:new String[]{"ERROR","BUSY","DUPLICATE","STOPPED"}) {
            SpeakerProtocol p=new SpeakerProtocol();p.begin(ID,0);p.receive("SPEAKER "+ID+" "+result);assertNotNull(p.begin(OTHER,1));
        }
    }
    @Test public void invalidIdsRejected() {
        SpeakerProtocol p=new SpeakerProtocol();assertNull(p.begin("SOS\n",0));assertNull(p.begin("",0));assertNotNull(p.begin(ID,0));
    }
    @Test public void busyCoversWholePlaybackAndClearsOnTerminalReply() {
        SpeakerProtocol p=new SpeakerProtocol();assertFalse(p.busy());p.begin(ID,0);assertTrue(p.busy());
        p.receive("SPEAKER "+ID+" CHECKING");assertTrue(p.busy());
        p.receive("SPEAKER "+ID+" PLAY_REQUESTED");assertTrue(p.busy());
        p.receive("SPEAKER "+ID+" UNCONFIRMED");assertFalse(p.busy());
        p.begin(OTHER,1);assertTrue(p.busy());p.disconnect();assertFalse(p.busy());
    }
    @Test public void tripleBlinkProducesOneSpeakerCommand() {
        Gesture g=new Gesture();SpeakerProtocol p=new SpeakerProtocol();int commands=0;long time=0;
        for(int i=0;i<20;i++)g.update(time+=50,.05,.05,true);
        for(int blink=0;blink<3;blink++){
            for(int i=0;i<6;i++)if(g.update(time+=50,.7,.7,true)&&p.begin(ID,time)!=null)commands++;
            for(int i=0;i<8;i++)if(g.update(time+=50,.05,.05,true)&&p.begin(ID,time)!=null)commands++;
            if(blink<2)assertEquals(0,commands);
        }
        assertEquals(1,commands);
    }
    @Test public void thirdCountRequestsSpeakerWithoutExtraOpenFrames() {
        Gesture g=new Gesture();SpeakerProtocol p=new SpeakerProtocol();long time=0;int commands=0;
        for(int i=0;i<20;i++)g.update(time+=50,.05,.05,true);
        for(int blink=0;blink<3;blink++){
            for(int i=0;i<4;i++)g.update(time+=50,.7,.7,true);
            int openFrames=blink==2?3:8;
            for(int i=0;i<openFrames;i++)if(g.update(time+=50,.05,.05,true)&&p.begin(ID,time)!=null)commands++;
        }
        assertEquals(3,g.completed);assertEquals(1,commands);
        for(int i=0;i<20;i++)if(g.update(time+=50,.4,.4,true)&&p.begin(OTHER,time)!=null)commands++;
        assertEquals(1,commands);
    }
}
