package org.wheelchair.blink;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AdminPanelTest {
    private TextView findTitle(View view){
        if(view instanceof TextView&&"Blink Assist".contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup){var group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){var found=findTitle(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    @Test public void hiddenEntryDoesNotStartCameraOrPlayAndButtonsHaveCorrectTracks()throws Exception{
        var instrumentation=InstrumentationRegistry.getInstrumentation();var context=instrumentation.getTargetContext();
        context.stopService(new Intent(context,MonitorService.class));SystemClock.sleep(500);
        Activity main=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        var monitor=instrumentation.addMonitor(AdminActivity.class.getName(),null,false);
        Activity panel=null;
        try{
            instrumentation.runOnMainSync(()->{var title=findTitle(main.getWindow().getDecorView());assertNotNull(title);assertTrue(title.performLongClick());});
            panel=monitor.waitForActivityWithTimeout(3000);assertNotNull(panel);
            Activity checked=panel;
            instrumentation.runOnMainSync(()->{
                for(var recording:ManualRecording.values()){
                    Button button=checked.getWindow().getDecorView().findViewWithTag("manual-track-"+recording.track);
                    assertNotNull(button);assertEquals(recording.phrase,button.getText().toString());assertFalse(button.isEnabled());
                }
            });
            assertFalse(MonitorState.running);assertFalse(MonitorState.manualMode);
            var info=context.getPackageManager().getActivityInfo(new android.content.ComponentName(context,AdminActivity.class),0);
            assertFalse(info.exported);
        }finally{
            Activity end=panel;instrumentation.runOnMainSync(()->{if(end!=null)end.finish();main.finish();});instrumentation.removeMonitor(monitor);
        }
    }
}
