package org.wheelchair.blink;

import android.Manifest;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telephony.SmsManager;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One explicitly enabled SMS per alert ID. No reconnect/restart replay queue. */
final class GuardianSms {
    static String number(String raw){
        return GuardianPolicy.number(raw);
    }
    static boolean enabled(Context c){return c.getSharedPreferences("settings",0).getBoolean("guardianEnabled",false);}
    static boolean permitted(Context c){return c.checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED;}
    static String readiness(Context c){
        var p=c.getSharedPreferences("settings",0);
        if(number(p.getString("guardianNumber",""))==null)return "Guardian number is missing";
        if(!enabled(c))return "Automatic guardian SMS is off";
        if(!permitted(c))return "SMS permission is missing";
        return "Guardian SMS enabled. Verify the default SMS SIM, signal and balance.";
    }
    static String send(Context context,String id,boolean test){
        return sendBody(context,id,test,null);
    }
    static String care(Context c,String id,boolean skipped){
        if(!c.getSharedPreferences("settings",0).getBoolean("guardianCareAlerts",false))return "Guardian care alerts are off";
        return sendBody(c,id,false,skipped?"A scheduled care reminder was marked skipped. Please check with the user.":"A scheduled care reminder has not been acknowledged. Please check with the user.");
    }
    static synchronized void motion(Context c){
        var p=c.getSharedPreferences("settings",0);long now=System.currentTimeMillis();
        if(!enabled(c)||!p.getBoolean("guardianMotionAlerts",false)||now-p.getLong("guardianMotionAt",0)<300000)return;
        p.edit().putLong("guardianMotionAt",now).apply();
        MonitorState.guardian=sendBody(c,java.util.UUID.randomUUID().toString(),false,"Movement detected by the wheelchair controller. This is a movement notice, not a confirmed emergency.");
    }
    private static String sendBody(Context context,String id,boolean test,String notice){
        if(!GuardianPolicy.validId(id))return "Invalid guardian alert ID";
        var settings=context.getSharedPreferences("settings",0);
        if(!test&&!enabled(context))return "Guardian SMS is off";
        var store=HealthStore.get(context);
        if(!store.beginAlert(id,test))return "This guardian alert was already requested";
        String phone=number(settings.getString("guardianNumber",""));
        if(phone==null||!permitted(context)){
            store.alertStatus(id,"NOT SENT: check guardian number and SMS permission");return "Guardian SMS not sent: check setup";
        }
        try {
            SmsManager manager=android.os.Build.VERSION.SDK_INT>=31?context.getSystemService(SmsManager.class):SmsManager.getDefault();
            if(manager==null)throw new IllegalStateException();
            // An explicit default SIM avoids an interactive SIM picker during a blink alert.
            if(manager.getSubscriptionId()<0)throw new IllegalStateException("Choose the default SMS SIM in Android settings");
            String time=DateTimeFormatter.ofPattern("HH:mm dd/MM",Locale.US).format(LocalDateTime.now());
            String body=notice==null?GuardianPolicy.message(time,id,test):"Blink Assist: "+notice+" "+time+" Ref "+id.substring(0,8);
            manager.sendTextMessage(phone,null,body,callback(context,id,"SENT"),callback(context,id,"DELIVERED"));
            return "Guardian SMS requested; check History for delivery";
        }catch(Exception error){
            store.alertStatus(id,"NOT SENT: check SIM, balance, signal and SMS permission");
            return "Guardian SMS could not be sent. Check SIM and permission";
        }
    }
    private static PendingIntent callback(Context c,String id,String type){
        Intent i=new Intent(c,GuardianSmsReceiver.class).setAction("org.wheelchair.blink.SMS_"+type)
                .setData(Uri.parse("blink-sms:"+id+":"+type)).putExtra("alertId",id);
        // Delivery reports add their PDU to this explicit, private receiver.
        int mutability=type.equals("DELIVERED")?(android.os.Build.VERSION.SDK_INT>=31
                ?PendingIntent.FLAG_MUTABLE:0):PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|mutability);
    }
}
