package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;
public class CareSoundTest {
    @Test public void careAndDemoTracksUseExistingProtocol(){
        java.util.Set<Integer> tracks=new java.util.HashSet<>();tracks.add(2999);
        for(var sound:CareSound.values())if(sound!=CareSound.CUSTOM){assertTrue("Duplicate track",tracks.add(sound.track));assertSame(sound,CareSound.from(sound.key));}
        for(int track:tracks){
            var protocol=new SpeakerProtocol();assertEquals("REMIND "+track+" "+SpeakerProtocolTest.ID+"\n",protocol.begin(SpeakerProtocolTest.ID,0,track));
        }
    }
    @Test public void customTracksCannotCollideWithVoicePack(){
        assertEquals(101,CareSound.customTrack(1));assertEquals(2899,CareSound.customTrack(2799));
        for(long id:new long[]{-1,0,2800,2889,2890,2899,Long.MAX_VALUE})assertEquals(0,CareSound.customTrack(id));
        assertEquals(CareSound.CUSTOM,CareSound.from("invalid"));
    }
    @Test public void suggestedCategoriesKeepTakenOnlyForMedicine(){
        assertEquals("Medicine",CareSound.TABLET.reminderKind());assertEquals("Medicine",CareSound.MEDICINE.reminderKind());
        assertEquals("Appointment",CareSound.DOCTOR.reminderKind());
        assertEquals("Meal",CareSound.BREAKFAST.reminderKind());assertEquals("Activity",CareSound.POSITION_CHANGE.reminderKind());
    }
}
