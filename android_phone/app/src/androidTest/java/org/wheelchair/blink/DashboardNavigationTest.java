package org.wheelchair.blink;
import android.app.*;
import android.content.Intent;
import android.view.*;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DashboardNavigationTest {
    private View find(View v,String description){
        if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
        if(v instanceof ViewGroup){var g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){var match=find(g.getChildAt(n),description);if(match!=null)return match;}}
        return null;
    }
    private View title(View v){
        if(v instanceof TextView&&"Blink Assist".contentEquals(((TextView)v).getText()))return v;
        if(v instanceof ViewGroup){var g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){var match=title(g.getChildAt(n));if(match!=null)return match;}}return null;
    }
    @Test public void tabsReachRealScreensAndHiddenEntryDoesNotStartMonitoring(){
        var i=InstrumentationRegistry.getInstrumentation();var c=i.getTargetContext();
        Activity current=i.startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean runningBefore=MonitorState.running;
        Class<?>[] screens={HealthActivity.class,CareActivity.class,AlertsActivity.class};String[] labels={"Health tab","Care tab","Alerts tab"};
        for(int n=0;n<screens.length;n++){
            var monitor=i.addMonitor(screens[n].getName(),null,false);Activity from=current;String label=labels[n];
            i.runOnMainSync(()->{View tab=find(from.getWindow().getDecorView(),label);assertNotNull(tab);assertTrue(tab.performClick());});
            current=monitor.waitForActivityWithTimeout(4000);assertNotNull(current);i.removeMonitor(monitor);
        }
        Activity home=i.startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        var monitor=i.addMonitor(AdminActivity.class.getName(),null,false);
        i.runOnMainSync(()->{var heading=title(home.getWindow().getDecorView());assertNotNull(heading);assertTrue(heading.performLongClick());});
        Activity panel=monitor.waitForActivityWithTimeout(4000);assertNotNull(panel);i.removeMonitor(monitor);
        assertEquals(runningBefore,MonitorState.running);
        i.runOnMainSync(()->{panel.finish();home.finish();});
    }
}
