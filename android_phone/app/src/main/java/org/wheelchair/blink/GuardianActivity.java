package org.wheelchair.blink;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.util.UUID;
import java.util.concurrent.*;

public final class GuardianActivity extends Activity {
    private EditText number;
    private CheckBox enabled;
    private TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean updating;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout root,String value,int size){return AppUi.label(root,value,size,size>=18?AppUi.INK:AppUi.MUTED,size>=18);}
    private Button button(LinearLayout root,String value){return AppUi.button(root,value,false,()->{});}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);var prefs=getSharedPreferences("settings",0);
        var page=AppUi.screen(this,"Guardian","Automatic SMS alerts to someone you trust",3);
        var root=AppUi.card(page);
        text(root,"Your guardian",21);
        text(root,"Save a phone number, then enable automatic alerts.",14);
        number=new EditText(this);number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);number.setSingleLine();
        number.setHint("Guardian number, including +country code");number.setText(prefs.getString("guardianNumber",""));root.addView(number);
        enabled=new CheckBox(this);enabled.setText("Enable guardian SMS (includes three-blink emergency)");
        if(!GuardianSms.permitted(this))prefs.edit().putBoolean("guardianEnabled",false).apply();
        enabled.setChecked(GuardianSms.enabled(this));
        button(root,"Save guardian number").setOnClickListener(v->{
            String phone=GuardianSms.number(number.getText().toString());
            if(phone==null){number.setError("Include +country code, for example +91 followed by the mobile number");return;}
            prefs.edit().putString("guardianNumber",phone).putBoolean("guardianEnabled",false).apply();
            updating=true;enabled.setChecked(false);updating=false;status.setText("Number saved. Enable automatic alerts below.");
        });
        root.addView(enabled);status=text(root,GuardianSms.enabled(this)?"Automatic guardian SMS is enabled":"Automatic guardian SMS is off",15);
        enabled.setOnCheckedChangeListener((v,on)->{
            if(updating)return;
            if(!on){prefs.edit().putBoolean("guardianEnabled",false).apply();status.setText("Automatic guardian SMS is off");return;}
            String saved=prefs.getString("guardianNumber","");
            if(GuardianSms.number(saved)==null||!saved.equals(GuardianSms.number(number.getText().toString()))){
                updating=true;enabled.setChecked(false);updating=false;status.setText("Save the guardian number first");return;
            }
            if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)&&
                    !getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)){
                updating=true;enabled.setChecked(false);updating=false;status.setText("This device does not support sending SMS");return;
            }
            if(!GuardianSms.permitted(this)){
                updating=true;enabled.setChecked(false);updating=false;requestPermissions(new String[]{Manifest.permission.SEND_SMS},41);return;
            }
            prefs.edit().putBoolean("guardianEnabled",true).apply();status.setText("Enabled. Three blinks while monitoring will send an SMS automatically.");
        });
        var details=AppUi.disclosure(root,"Delivery test & SIM settings");
        button(details,"Send a test SMS to saved number").setOnClickListener(v->{
            String saved=prefs.getString("guardianNumber","");
            if(GuardianSms.number(saved)==null||!GuardianSms.permitted(this)){status.setText("Save the number and enable SMS permission first");return;}
            new AlertDialog.Builder(this).setTitle("Send test SMS?").setMessage("Send a message labelled TEST ONLY to "+saved+"? Carrier charges may apply.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Send test",(dialog,which)->{
                        status.setText("Requesting test SMS...");worker.execute(()->{
                            String result;
                            try{result=GuardianSms.send(this,UUID.randomUUID().toString(),true);}catch(Exception error){result="Could not request the test SMS";}
                            String message=result;runOnUiThread(()->{if(!isFinishing())status.setText(message);});
                        });
                    }).show();
        });
        button(details,"Open SIM / network settings").setOnClickListener(v->startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)));
        text(details,"Check Health & history for results. Sent means accepted by the carrier. Delivered depends on carrier reports and does not mean the guardian has read it. Missed alerts are not replayed after restart or reconnection.",14);
        for(String[] choice:new String[][]{{"guardianCareAlerts","Also alert for unacknowledged or skipped care reminders"},{"guardianMotionAlerts","Also send movement notices (at most once per 5 minutes)"}}){
            CheckBox box=new CheckBox(this);box.setText(choice[1]);box.setChecked(prefs.getBoolean(choice[0],false));root.addView(box);
            box.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean(choice[0],on).apply());
        }
        text(details,"Care alerts also require Guardian alert on the reminder. SMS needs a default SIM, signal and balance; carrier charges may apply. Use a guardian who has agreed to receive alerts.",13);


    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==41){
            boolean allowed=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;
            enabled.setChecked(allowed);status.setText(allowed?"Automatic guardian SMS is enabled. Send a test to verify it.":"SMS permission was not granted. Automatic guardian SMS remains off.");
        }
    }
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
