package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class HealthReadingTest {
    @Test public void firmwareSnapshotPreservesUnitsAndMotionCount(){
        var r=HealthReading.parse("HEALTH 1 1234 72 98 29.5 55.0 30.0 0 1 2");
        assertNotNull(r);assertEquals(72,r.bpm(),0);assertEquals(98,r.oxygen(),0);
        assertEquals(29.5,r.roomC(),0);assertEquals(55,r.humidity(),0);assertEquals(30,r.distance(),0);
        assertFalse(r.moving());assertTrue(r.finger());assertEquals(2,r.motionEvents());
    }
    @Test public void missingOpticalAndDistanceReadingsRemainMissing(){
        var r=HealthReading.parse("HEALTH 1 1000 - - 29 55 - - 0 0");
        assertNotNull(r);assertNull(r.bpm());assertNull(r.oxygen());assertNull(r.distance());assertNull(r.moving());
        assertFalse(r.finger());
    }
    @Test public void malformedStaleOpticalAndOutOfRangeValuesAreRejected(){
        String valid="HEALTH 1 1234 72 98 29.5 55.0 30.0 0 1 2";
        for(String input:new String[]{"",valid+" extra",valid.replace("HEALTH 1","HEALTH 2"),
                valid.replace("72","NaN"),valid.replace("98","101"),valid.replace("72","0"),
                valid.replace("55.0","101"),valid.replace("30.0","-10"),valid.replace("0 1 2","0 0 2"),
                valid.replace("1234","4294967296"),valid.replace("0 1 2","maybe 1 2"),
                valid.replace("0 1 2","0 1 -1")})assertNull(input,HealthReading.parse(input));
    }
    @Test public void liveValuesExpireInsteadOfDisplayingAsCurrentForever(){
        var sample=new HealthState.Live(HealthReading.parse("HEALTH 1 1000 - - - - - - 0 0"),1000,100000);
        assertTrue(sample.fresh(1000));assertTrue(sample.fresh(7000));assertFalse(sample.fresh(7001));assertFalse(sample.fresh(999));
    }
}
