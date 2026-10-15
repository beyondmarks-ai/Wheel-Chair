package org.wheelchair.blink;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewTimingTest {
    private final MonitorState.Eyes eyes = new MonitorState.Eyes(.3f,.4f,.6f,.4f,1000,true);
    @Test public void recentEyeLocationsCanOverlayLiveFrame() { assertTrue(eyes.visibleAt(1150,1100)); }
    @Test public void oldTrackingCannotMarkEyesOnNewVideo() { assertFalse(eyes.visibleAt(1510,1100));assertFalse(eyes.visibleAt(1400,1300)); }
    @Test public void FutureTimestampRejected() { assertFalse(eyes.visibleAt(999,1000)); }
}
