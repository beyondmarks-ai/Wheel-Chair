package org.wheelchair.blink;
import android.content.*;

public final class CareRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null||i.getAction()==null)return;
        if(!java.util.List.of("android.intent.action.BOOT_COMPLETED","android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET","android.intent.action.TIMEZONE_CHANGED","android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED").contains(i.getAction()))return;
        PendingResult pending=goAsync();new Thread(()->{try{CareScheduler.restore(c);}catch(Exception e){android.util.Log.e("WheelchairCare","Restore failed",e);}finally{pending.finish();}},"care-restore").start();
    }
}
