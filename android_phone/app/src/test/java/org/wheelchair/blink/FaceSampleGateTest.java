package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class FaceSampleGateTest {
    @Test public void repeatedOrReorderedResultsCannotAdvanceGesture() {
        FaceSampleGate gate=new FaceSampleGate();
        assertTrue(gate.consume(1000));
        for(int i=0;i<30;i++)assertFalse(gate.consume(1000));
        assertFalse(gate.consume(900));assertTrue(gate.consume(1120));
    }
    @Test public void delayedFutureMismatchedAndMissingFacesAreRejected() {
        assertTrue(FaceSampleGate.matches(1300,1250,1100,.5,.5,.51,.51));
        assertFalse(FaceSampleGate.matches(1700,1500,1100,.5,.5,.5,.5));
        assertTrue(FaceSampleGate.matches(1500,1500,1100,.5,.5,.5,.5));
        assertFalse(FaceSampleGate.matches(1601,1601,1100,.5,.5,.5,.5));
        assertFalse(FaceSampleGate.matches(1000,1000,1100,.5,.5,.5,.5));
        assertFalse(FaceSampleGate.matches(1300,1250,1100,.5,.5,.7,.5));
        assertFalse(FaceSampleGate.matches(1300,1250,1100,Double.NaN,.5,.5,.5));
    }
}
