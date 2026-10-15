package org.wheelchair.blink;
import android.app.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AlertNotificationTest {
    @Test public void localTestsPostDistinctNotificationsWithoutEmergencyStateOrGuardianDelivery()throws Exception{
        var c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var manager=c.getSystemService(NotificationManager.class);CareAlerts.channels(c);
        assertTrue(CareAlerts.readiness(c,CareAlerts.EMERGENCY).startsWith("Notifications allowed"));
        long until=c.getSharedPreferences("settings",0).getLong("careEmergencyUntil",0);
        long before;try(var cursor=HealthStore.get(c).getReadableDatabase().rawQuery("SELECT count(*) FROM alerts",null)){cursor.moveToFirst();before=cursor.getLong(0);}
        try{
            for(String kind:new String[]{"blink","movement","obstacle"}){
                CareAlerts.demo(c,kind);
                java.util.Optional<android.service.notification.StatusBarNotification> alert=java.util.Optional.empty();
                long deadline=android.os.SystemClock.elapsedRealtime()+4000;
                while(android.os.SystemClock.elapsedRealtime()<deadline){
                    alert=java.util.Arrays.stream(manager.getActiveNotifications()).filter(n->n.getId()==306&&"local-demo".equals(n.getTag())&&n.getNotification().extras.getString(Notification.EXTRA_TITLE,"").toLowerCase().contains(kind.equals("blink")?"blink":kind)).findFirst();
                    if(alert.isPresent())break;Thread.sleep(100);
                }
                assertTrue("Notification absent: "+kind+"; "+MonitorState.notification,alert.isPresent());
                String title=alert.get().getNotification().extras.getString(Notification.EXTRA_TITLE);
                assertTrue(title,title.startsWith(kind.equals("obstacle")?"DEMO:":"TEST:"));
                var channel=manager.getNotificationChannel(alert.get().getNotification().getChannelId());
                assertEquals(NotificationManager.IMPORTANCE_HIGH,channel.getImportance());assertNotNull(channel.getSound());
            }
            assertEquals(until,c.getSharedPreferences("settings",0).getLong("careEmergencyUntil",0));
            try(var cursor=HealthStore.get(c).getReadableDatabase().rawQuery("SELECT count(*) FROM alerts",null)){cursor.moveToFirst();assertEquals(before,cursor.getLong(0));}
        }finally{manager.cancel("local-demo",306);}
    }
}
