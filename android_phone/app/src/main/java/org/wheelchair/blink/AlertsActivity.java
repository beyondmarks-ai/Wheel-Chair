package org.wheelchair.blink;
import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public final class AlertsActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final TextView[] checks=new TextView[4];
    private TextView session,last;
    private Button permission;
    private final Runnable refresh=new Runnable(){public void run(){
        checks[0].setText(shortStatus(CareAlerts.readiness(AlertsActivity.this,CareAlerts.EMERGENCY)));
        checks[1].setText(shortStatus(CareAlerts.readiness(AlertsActivity.this,CareAlerts.MOTION)));
        checks[2].setText(shortStatus(CareAlerts.readiness(AlertsActivity.this,CareAlerts.CARE)));
        checks[3].setText(GuardianSms.enabled(AlertsActivity.this)&&GuardianSms.permitted(AlertsActivity.this)?"SMS enabled":"Set up SMS");
        session.setText(!MonitorState.running?"Monitoring is off":MonitorState.manualMode?"Speaker mode · Blink detection off":"Camera monitoring is active");
        last.setText(MonitorState.notification);
        permission.setVisibility(getSystemService(NotificationManager.class).areNotificationsEnabled()?View.GONE:View.VISIBLE);
        handler.postDelayed(this,1000);
    }};
    private String shortStatus(String value){
        if(value.equals("Notifications allowed"))return "Ready";
        if(value.contains("permission"))return "Permission needed";
        if(value.contains("silent"))return "Silent";
        if(value.contains("pop-up"))return "Pop-up off";
        return "Blocked";
    }
    private void channel(String id){startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,id));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);CareAlerts.channels(this);
        var root=AppUi.screen(this,"Alerts & sounds","Stay informed. Keep your guardian connected.",3);
        var connection=AppUi.card(root);session=AppUi.label(connection,"",18,AppUi.INK,true);
        AppUi.label(connection,"Blink alerts need camera monitoring. Movement alerts need the Bluetooth controller.",13,AppUi.MUTED,false);
        permission=AppUi.button(root,"Allow notifications",true,()->{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},91);
            else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        AppUi.section(root,"Alert controls","Tap a card to manage its settings");
        String[] names={"Emergency","Movement","Reminders","Guardian"};String[] channels={CareAlerts.EMERGENCY,CareAlerts.MOTION,CareAlerts.CARE,""};
        View[] tiles=new View[4];
        for(int n=0;n<4;n++){
            int index=n;var tile=AppUi.card(this);tile.setMinimumHeight(AppUi.dp(this,120));
            tile.addView(new AppUi.Icon(this,n==3?"guardian":"alerts",AppUi.TEAL),new LinearLayout.LayoutParams(AppUi.dp(this,26),AppUi.dp(this,26)));
            AppUi.gap(tile,8);AppUi.label(tile,names[n],18,AppUi.INK,true);checks[n]=AppUi.label(tile,"",13,AppUi.MUTED,false);
            tile.setClickable(true);tile.setFocusable(true);tile.setContentDescription(names[n]+" settings");tile.setOnClickListener(v->{if(index==3)AppUi.open(this,GuardianActivity.class);else channel(channels[index]);});tiles[n]=tile;
        }
        AppUi.grid(root,tiles);
        var prefs=getSharedPreferences("settings",0);var movement=new CheckBox(this);movement.setText("Show movement notifications");movement.setTextColor(AppUi.INK);movement.setChecked(prefs.getBoolean("careMotionSound",true));root.addView(movement);
        movement.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("careMotionSound",on).apply());
        var latest=AppUi.card(root);AppUi.label(latest,"LATEST PHONE ALERT",11,AppUi.MUTED,true);last=AppUi.label(latest,"",14,AppUi.INK,false);
        var test=AppUi.disclosure(root,"Test notification sounds");AppUi.label(test,"Tests stay on this phone. They do not send guardian SMS.",13,AppUi.MUTED,false);
        var blink=AppUi.column(this);var motion=AppUi.column(this);
        AppUi.button(blink,"Test blink",false,()->CareAlerts.demo(this,"blink"));AppUi.button(motion,"Test movement",false,()->CareAlerts.demo(this,"movement"));AppUi.grid(test,blink,motion);
        var more=AppUi.disclosure(root,"More settings & help");
        AppUi.button(more,"Unacknowledged reminder sound",false,()->channel(CareAlerts.LATE));
        AppUi.button(more,"App & background settings",false,()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))));
        AppUi.label(more,"Allow sound and pop-ups in each category. Silent mode, Do Not Disturb and Vivo background restrictions can suppress alerts. Guardian SMS needs a saved number, SMS permission and a working SIM; the guardian's phone controls its own SMS sound.",13,AppUi.MUTED,false);
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
