package org.wheelchair.blink;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

/** Hidden entry from the main title; manual actions are never labelled as gestures. */
public final class AdminActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<ManualRecording,Button> recordings=new LinkedHashMap<>();
    private TextView status,result,mode;
    private Button connect,disconnect;
    private boolean visible;
    private final SoundPreview soundPreview=new SoundPreview();
    private final Runnable refresh=new Runnable(){public void run(){
        if(!visible)return;
        status.setText(MonitorState.speaker);
        result.setText(MonitorState.adminResult);
        mode.setText(!MonitorState.running?"Connect the controller to enable the buttons.":
                MonitorState.manualMode?"Manual mode · Camera off":"Camera monitoring is also running");
        connect.setEnabled(!MonitorState.running);disconnect.setEnabled(MonitorState.running);
        disconnect.setText(MonitorState.running&&!MonitorState.manualMode?"Stop monitoring & disconnect":"Disconnect speaker");
        for(var entry:recordings.entrySet()){
            boolean ready=MonitorService.manualReady(entry.getKey().track);
            entry.getValue().setEnabled(ready);entry.getValue().setAlpha(ready?1f:.45f);
        }
        handler.postDelayed(this,200);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xfff7f3f3);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(16),dp(20),dp(24));scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        label(root,"Admin controls",27,true);
        label(root,"Tap to speak",18,true);
        label(root,"These buttons play the saved recordings through the wheelchair speaker. No gesture or internet is needed.",15,false);
        mode=label(root,"",14,true);status=label(root,"",15,false);
        connect=button(root,"Connect speaker",false);connect.setOnClickListener(v->connect());
        for(var recording:ManualRecording.values()){
            Button button=button(root,recording.phrase,true);button.setTag("manual-track-"+recording.track);
            button.setContentDescription("Play recording: "+recording.phrase);
            button.setEnabled(false);recordings.put(recording,button);
            button.setOnClickListener(v->{
                if(!MonitorService.manualReady(recording.track)){
                    MonitorState.adminResult="Speaker is unavailable or busy. Nothing was queued.";return;
                }
                button.setEnabled(false);
                startService(new Intent(this,MonitorService.class).setAction("ADMIN_PLAY").putExtra("track",recording.track));
            });
        }
        result=label(root,"",15,true);
        label(root,"Obstacle demonstration",20,true);
        label(root,"Manual demo only. The ultrasonic sensor is not measuring an obstacle.",14,false);
        var preferences=getSharedPreferences("settings",0);
        CheckBox obstacleReady=new CheckBox(this);obstacleReady.setText("Demo voice 2999.mp3 is copied to the DFPlayer card");
        obstacleReady.setChecked(preferences.getBoolean("obstacleDemoOnCard",false));root.addView(obstacleReady);
        obstacleReady.setOnCheckedChangeListener((v,on)->preferences.edit().putBoolean("obstacleDemoOnCard",on).apply());
        button(root,"DEMO: obstacle ahead",true).setOnClickListener(v->{
            if(CareAlerts.emergencyActive(this)){MonitorState.adminResult="Emergency active. Obstacle demo skipped.";return;}
            CareAlerts.demo(this,"obstacle");
            if(obstacleReady.isChecked()&&MonitorService.obstacleDemoReady())
                startService(new Intent(this,MonitorService.class).setAction("OBSTACLE_DEMO"));
            else {
                boolean playing=soundPreview.play(this,R.raw.voice_obstacle_demo);
                MonitorState.adminResult=playing?"DEMO: playing on this phone; wired recording unavailable or speaker busy":"DEMO: phone playback could not start";
                HealthStore.get(this).event("Obstacle demonstration",MonitorState.adminResult);
            }
        });
        button(root,"Get SD voices / arrange reminders",false).setOnClickListener(v->startActivity(new Intent(this,CareActivity.class)));
        label(root,"Manual playback is saved in history. These buttons do not send guardian alerts. Keep the controller powered with its SD card inserted.",14,false);
        disconnect=button(root,"Disconnect speaker",false);disconnect.setOnClickListener(v->stopService(new Intent(this,MonitorService.class)));
        button(root,"Bluetooth settings",false).setOnClickListener(v->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        button(root,"Close panel",false).setOnClickListener(v->finish());
        setContentView(scroll);
    }
    private void connect(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},21);return;
        }
        try{
            BluetoothManager manager=getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){notice("Turn on Bluetooth first.");return;}
            var settings=getSharedPreferences("settings",MODE_PRIVATE);
            String saved=settings.getString("speakerAddress","");
            List<BluetoothDevice> devices=new ArrayList<>();
            for(var device:adapter.getBondedDevices()){
                if(device.getAddress().equals(saved)){begin();return;}
                if("Wheelchair-Speaker".equals(device.getName()))devices.add(device);
            }
            if(devices.isEmpty()){notice("Pair Wheelchair-Speaker in Bluetooth settings first.");return;}
            String[] names=devices.stream().map(d->d.getName()+" · "+d.getAddress()).toArray(String[]::new);
            new AlertDialog.Builder(this).setTitle("Select wheelchair controller").setItems(names,(dialog,index)->{
                settings.edit().putString("speakerAddress",devices.get(index).getAddress()).putString("speakerLabel",names[index]).apply();begin();
            }).setNegativeButton("Cancel",null).show();
        }catch(Exception error){notice("Check Bluetooth permission and the controller connection.");}
    }
    private void begin(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},22);
        MonitorState.speaker="Connecting to the controller...";
        startForegroundService(new Intent(this,MonitorService.class).setAction("ADMIN_CONNECT"));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==21&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)connect();
    }
    private void notice(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout root,String text,int size,boolean bold){
        TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(0xff30242a);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        view.setPadding(0,dp(6),0,dp(8));root.addView(view);return view;
    }
    private Button button(LinearLayout root,String text,boolean red){
        Button view=new Button(this);view.setText(text);view.setAllCaps(false);view.setTextSize(red?22:16);
        view.setTextColor(red?Color.WHITE:0xff30242a);view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        GradientDrawable shape=new GradientDrawable();shape.setColor(red?0xffb91c1c:0xffe8dfe2);shape.setCornerRadius(dp(16));view.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);
        view.setMinHeight(dp(red?88:52));view.setPadding(dp(12),dp(14),dp(12),dp(14));root.addView(view,params);return view;
    }
    @Override protected void onStart(){super.onStart();visible=true;handler.post(refresh);}
    @Override protected void onStop(){visible=false;handler.removeCallbacks(refresh);soundPreview.close();super.onStop();}
}
