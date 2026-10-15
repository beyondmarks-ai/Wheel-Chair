package org.wheelchair.blink;

import android.app.Activity;
import android.content.*;
import android.telephony.SmsMessage;

public final class GuardianSmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String id=intent.getStringExtra("alertId");
        if(!GuardianPolicy.validId(id))return;
        String status;
        if("org.wheelchair.blink.SMS_SENT".equals(intent.getAction()))
            status=getResultCode()==Activity.RESULT_OK?"SENT":"NOT SENT: carrier/SIM error "+getResultCode();
        else if("org.wheelchair.blink.SMS_DELIVERED".equals(intent.getAction())){
            byte[] pdu=intent.getByteArrayExtra("pdu");String format=intent.getStringExtra("format");
            SmsMessage message;
            try{message=pdu==null?null:SmsMessage.createFromPdu(pdu,format);}catch(Exception malformed){return;}
            if(message==null)return;
            status=GuardianPolicy.deliveryStatus(message.getStatus(),format);
            if(status==null)return;
        }else return;
        HealthStore.get(context).alertStatus(id,status);
    }
}
