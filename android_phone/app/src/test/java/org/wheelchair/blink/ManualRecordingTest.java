package org.wheelchair.blink;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManualRecordingTest {
    @Test public void eachButtonUsesItsExistingAcknowledgedControllerCommand(){
        assertEquals("Emergency",ManualRecording.forTrack(1).phrase);
        assertEquals("Please help me",ManualRecording.forTrack(4).phrase);
        assertEquals("Please come here",ManualRecording.forTrack(5).phrase);
        assertEquals("Yes",ManualRecording.forTrack(3).phrase);
        for(var recording:ManualRecording.values()){
            SpeakerProtocol protocol=new SpeakerProtocol();
            String command=protocol.begin(SpeakerProtocolTest.ID,0,recording.track);
            assertEquals((recording.track==1?"SOS ":"PHRASE "+recording.track+" ")+SpeakerProtocolTest.ID+"\n",command);
            protocol.receive("SPEAKER "+SpeakerProtocolTest.ID+" PLAY_REQUESTED");
            assertTrue(protocol.busy());
            protocol.receive("SPEAKER "+SpeakerProtocolTest.ID+" FINISHED");
            assertFalse(protocol.busy());
        }
    }
    @Test public void panelCannotSelectUnacknowledgedMotionOrArbitraryCareTracks(){
        for(int track:new int[]{Integer.MIN_VALUE,-1,0,2,6,101,2999,Integer.MAX_VALUE})assertNull(ManualRecording.forTrack(track));
    }
}
