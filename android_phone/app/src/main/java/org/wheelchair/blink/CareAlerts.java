package org.wheelchair.blink;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

final class CareAlerts {
    static final String CARE="care_chime_v1",LATE="care_late_v1",EMERGENCY="blink_emergency_v1",MOTION="movement_alert_v2",DEMO="obstacle_demo_v1";
    static void channels(Context c){
        channel(c,CARE,"Scheduled care",R.raw.care_chime,NotificationManager.IMPORTANCE_HIGH);
        channel(c,LATE,"Unacknowledged care",R.raw.missed_chime,NotificationManager.IMPORTANCE_HIGH);
        channel(c,EMERGENCY,"Blink emergency",R.raw.emergency_chime,NotificationManager.IMPORTANCE_HIGH);
        var oldMotion=c.getSystemService(NotificationManager.class).getNotificationChannel("movement_chime_v1");
        channel(c,MOTION,"Movement alerts",R.raw.movement_chime,oldMotion!=null&&oldMotion.getImportance()==NotificationManager.IMPORTANCE_NONE?NotificationManager.IMPORTANCE_NONE:NotificationManager.IMPORTANCE_HIGH);
        channel(c,DEMO,"Obstacle demonstrations",R.raw.movement_chime,NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel silent=new NotificationChannel("care_quiet_v1","Care updates during an emergency",NotificationManager.IMPORTANCE_LOW);
        silent.setSound(null,null);c.getSystemService(NotificationManager.class).createNotificationChannel(silent);
    }
    private static void channel(Context c,String id,String title,int sound,int importance){
        NotificationChannel channel=new NotificationChannel(id,title,importance);
        channel.setSound(Uri.parse("android.resource://"+c.getPackageName()+"/raw/"+c.getResources().getResourceEntryName(sound)),
                new AudioAttributes.Builder().setUsage(id.equals(MOTION)?AudioAttributes.USAGE_NOTIFICATION:AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        channel.enableVibration(true);c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private static PendingIntent open(Context c){return PendingIntent.getActivity(c,0,new Intent(c,CareActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static String readiness(Context c,String channel){
        var manager=c.getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return "Notification permission is off";
        if(!manager.areNotificationsEnabled())return "App notifications are blocked";
        var setting=manager.getNotificationChannel(channel);
        if(setting!=null&&setting.getImportance()==NotificationManager.IMPORTANCE_NONE)return "This notification category is blocked";
        if(setting!=null&&setting.getImportance()<NotificationManager.IMPORTANCE_HIGH)return "Notifications allowed; pop-up alerts are off";
        if(setting!=null&&setting.getSound()==null)return "Notifications allowed; this category is silent";
        return "Notifications allowed";
    }
    private static void post(Context c,String tag,int id,Notification notification){
        String check=readiness(c,notification.getChannelId());
        if(!check.startsWith("Notifications allowed")){MonitorState.notification=check+". Open Alerts & sounds.";return;}
        try{c.getSystemService(NotificationManager.class).notify(tag,id,notification);MonitorState.notification="Notification posted: "+notification.extras.getString(Notification.EXTRA_TITLE);}
        catch(SecurityException blocked){MonitorState.notification="Android blocked this notification. Open Alerts & sounds.";}
    }
    static void demo(Context c,String kind){
        channels(c);boolean obstacle=kind.equals("obstacle"),motion=kind.equals("movement");
        String title=obstacle?"DEMO: obstacle ahead":motion?"TEST: movement alert":"TEST: blink emergency alert";
        post(c,"local-demo",306,new Notification.Builder(c,obstacle?DEMO:motion?MOTION:EMERGENCY)
                .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title)
                .setContentText("Manual demonstration only. No sensor detection or guardian SMS.")
                .setContentIntent(PendingIntent.getActivity(c,3,new Intent(c,AlertsActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
                .setCategory(Notification.CATEGORY_EVENT).setAutoCancel(true).build());
    }
    static boolean emergencyActive(Context c){return c.getSharedPreferences("settings",0).getLong("careEmergencyUntil",0)>System.currentTimeMillis();}
    static void due(Context c,CareStore.Occurrence o,boolean overdue){
        channels(c);
        Notification.Builder b=new Notification.Builder(c,emergencyActive(c)?"care_quiet_v1":overdue?LATE:CARE).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle((overdue?"Not acknowledged: ":"Time for: ")+o.title())
                .setContentText(overdue?"Please check and acknowledge this reminder":"Tap Taken/Done, Snooze or Skip")
                .setContentIntent(open(c)).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(false).setOnlyAlertOnce(false);
        for(String action:new String[]{"DONE","SNOOZE_ACTION","SKIPPED"}){
            String label=action.equals("DONE")?(o.kind().equals("Medicine")?"Taken":"Done"):action.equals("SKIPPED")?"Skip":"Snooze 10 min";
            b.addAction(new Notification.Action.Builder(null,label,CareScheduler.alarm(c,action,o.id(),0,0,0)).build());
        }
        post(c,o.id(),301,b.build());
    }
    static void emergency(Context c){
        channels(c);c.getSharedPreferences("settings",0).edit().putLong("careEmergencyUntil",System.currentTimeMillis()+10000).apply();
        CareVoiceService.cancelAll();
        post(c,null,302,new Notification.Builder(c,EMERGENCY).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Emergency: three blinks detected").setContentText("Check the user. Speaker and guardian status are in Blink Assist.")
                .setContentIntent(PendingIntent.getActivity(c,1,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
                .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(true).build());
    }
    static void movement(Context c){
        var p=c.getSharedPreferences("settings",0);long now=System.currentTimeMillis();
        if(!p.getBoolean("careMotionSound",true)){MonitorState.notification="Movement notifications are off. Open Alerts & sounds.";return;}
        if(now-p.getLong("careMotionAt",0)<30000)return;
        p.edit().putLong("careMotionAt",now).apply();channels(c);
        Notification.Builder b=new Notification.Builder(c,emergencyActive(c)?"care_quiet_v1":MOTION).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Movement detected").setContentText("The controller recorded movement. See Health & history.")
                .setContentIntent(PendingIntent.getActivity(c,2,new Intent(c,HealthActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true);
        post(c,null,303,b.build());
    }
}
