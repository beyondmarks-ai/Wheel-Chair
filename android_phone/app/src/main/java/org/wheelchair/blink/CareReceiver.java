package org.wheelchair.blink;

import android.content.*;

public final class CareReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        PendingResult pending=goAsync();
        new Thread(()->{try{handle(context,intent);}catch(Exception e){android.util.Log.e("WheelchairCare","Care action failed",e);}finally{pending.finish();}},"care-alarm").start();
    }
    static void handle(Context c,Intent i){
        synchronized(CareScheduler.class){handleLocked(c,i);}
    }
    private static void handleLocked(Context c,Intent i){
        var store=CareStore.get(c);String action=i.getAction(),id=i.getStringExtra("id");long now=System.currentTimeMillis();
        if(action==null||id==null)return;
        if(action.equals("DUE")){
            var p=store.getPlan(i.getLongExtra("plan",0));
            if(p==null||p.revision()!=i.getLongExtra("revision",0))return;
            var occurrence=store.begin(p,i.getLongExtra("time",0),now);
            // Ignore duplicate deliveries: they must not cancel a newly armed next occurrence.
            if(occurrence==null)return;
            CareScheduler.plan(c,p);announce(c,occurrence);return;
        }
        var o=store.getOccurrence(id);if(o==null)return;
        switch(action){
            case "SNOOZE":
                var resumed=store.resume(id,i.getLongExtra("time",0),now);if(resumed!=null)announce(c,resumed);return;
            case "OVERDUE":
                if(!store.claimEscalation(id,i.getLongExtra("time",0),now))return;
                HealthStore.get(c).event("Unacknowledged reminder",o.title());
                CareAlerts.due(c,store.getOccurrence(id),true);
                if(o.guardian()&&CareTime.timely(now,o.notifyAt()+o.grace()*60000L)){
                    var check=store.getOccurrence(id);
                    if(check.state().equals("PENDING"))GuardianSms.care(c,id,false);
                }return;
            case "DONE":case "SKIPPED":case "SNOOZE_ACTION":
                String state=action.equals("SNOOZE_ACTION")?"SNOOZED":action;
                if(!store.action(id,state,now))return;
                CareVoiceService.cancel(id);CareScheduler.cancelOccurrence(c,id);
                HealthStore.get(c).event("Care "+(state.equals("DONE")&&o.kind().equals("Medicine")?"taken (user reported)":state.toLowerCase(java.util.Locale.ROOT)),o.title());
                if(state.equals("SNOOZED"))CareScheduler.snooze(c,store.getOccurrence(id));
                else if(state.equals("SKIPPED")&&o.guardian())GuardianSms.care(c,id,true);
        }
    }
    private static void announce(Context c,CareStore.Occurrence o){
        if(!o.state().equals("PENDING")){
            HealthStore.get(c).event("Delayed reminder",o.title()+" - scheduled time passed; no late playback");return;
        }
        CareAlerts.due(c,o,false);CareScheduler.escalation(c,o);
        HealthStore.get(c).event("Reminder due",o.title());
        if(CareAlerts.emergencyActive(c)){CareStore.get(c).speaker(o.id(),"Emergency active; reminder speech skipped");return;}
        BluetoothSpeaker.TriggerResult result=o.audioReady()&&o.track()>=101?MonitorService.reminder(o.id(),o.track(),o.notifyAt()):BluetoothSpeaker.TriggerResult.NOT_READY;
        CareStore.get(c).speaker(o.id(),result==BluetoothSpeaker.TriggerResult.REQUESTED?"Wired speaker requested; completion not confirmed":"Wired speaker unavailable/unprepared; using phone voice");
        if(result!=BluetoothSpeaker.TriggerResult.REQUESTED){
            try{c.startForegroundService(new Intent(c,CareVoiceService.class).putExtra("id",o.id()));}
            catch(Exception unavailable){CareStore.get(c).speaker(o.id(),"Phone voice unavailable; reminder notification posted");}
        }
    }
}
