package org.wheelchair.blink;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import java.time.ZoneId;

final class CareScheduler {
    static boolean permitted(Context c){return Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static PendingIntent alarm(Context c,String action,String key,long plan,long revision,long time){
        Intent i=new Intent(c,CareReceiver.class).setAction(action).setData(Uri.parse("blink-care:"+action+":"+key))
                .putExtra("id",key).putExtra("plan",plan).putExtra("revision",revision).putExtra("time",time);
        return PendingIntent.getBroadcast(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void set(Context c,PendingIntent intent,long at){
        if(!permitted(c)||at<=0)return;
        PendingIntent show=PendingIntent.getActivity(c,0,new Intent(c,CareActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(at,show),intent);
    }
    static void plan(Context c,CareStore.Plan p){
        var manager=c.getSystemService(AlarmManager.class);
        manager.cancel(alarm(c,"DUE",""+p.id(),p.id(),p.revision(),0));
        long next=p.enabled()?CareTime.next(System.currentTimeMillis(),ZoneId.systemDefault(),p.hour(),p.minute(),p.days(),p.once()):-1;
        CareStore.get(c).next(p.id(),next);
        if(next>0)set(c,alarm(c,"DUE",""+p.id(),p.id(),p.revision(),next),next);
    }
    static void escalation(Context c,CareStore.Occurrence o){
        if(o.state().equals("PENDING")&&!o.escalated()){
            long deadline=o.notifyAt()+o.grace()*60000L;
            if(deadline>System.currentTimeMillis())set(c,alarm(c,"OVERDUE",o.id(),0,0,o.notifyAt()),deadline);
        }
    }
    static void snooze(Context c,CareStore.Occurrence o){set(c,alarm(c,"SNOOZE",o.id(),0,0,o.notifyAt()),o.notifyAt());}
    static void cancelOccurrence(Context c,String id){
        var m=c.getSystemService(AlarmManager.class);
        m.cancel(alarm(c,"OVERDUE",id,0,0,0));m.cancel(alarm(c,"SNOOZE",id,0,0,0));
        c.getSystemService(NotificationManager.class).cancel(id,301);
    }
    static void cancelPlan(Context c,long id){
        c.getSystemService(AlarmManager.class).cancel(alarm(c,"DUE",""+id,id,0,0));
        for(var o:CareStore.get(c).pending())if(o.plan()==id){CareStore.get(c).action(o.id(),"CANCELLED",System.currentTimeMillis());CareVoiceService.cancel(o.id());cancelOccurrence(c,o.id());}
    }
    static synchronized void restore(Context c){
        var store=CareStore.get(c);long now=System.currentTimeMillis();
        for(var p:store.plans()){
            if(p.next()>0&&p.next()<=now){
                if(CareTime.timely(now,p.next())&&permitted(c)){
                    set(c,alarm(c,"DUE",""+p.id(),p.id(),p.revision(),p.next()),now+500);continue;
                }
                store.begin(p,p.next(),now);
            }
            plan(c,p);
        }
        for(var o:store.pending()){
            if(o.state().equals("SNOOZED")){
                if(o.notifyAt()>now)snooze(c,o);
                else if(CareTime.timely(now,o.notifyAt())&&permitted(c))set(c,alarm(c,"SNOOZE",o.id(),0,0,o.notifyAt()),now+500);
                else store.resume(o.id(),o.notifyAt(),now);
            }else if(o.notifyAt()+o.grace()*60000L>now)escalation(c,o);
            else if(store.claimEscalation(o.id(),o.notifyAt(),now))HealthStore.get(c).event("Missed reminder",o.title()+" - recovered after deadline; guardian SMS not replayed");
        }
    }
}
