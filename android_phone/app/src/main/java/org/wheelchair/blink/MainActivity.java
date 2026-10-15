package org.wheelchair.blink;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int INK=0xff142d3d, TEAL=0xff087e8b, MUTED=0xff526675, BG=0xfff2f6fa;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EventStore store;
    private TextView status, progress, feedback, cloud, counts, rate, speakerStatus, speakerSelection, lastBlinkSpeaker, eyeStatus;
    private TextView faceFeedback, faceSetup, lastFaceMessage;
    private TextView faceSignals;
    private TextView calibrationStatus, deliveryStatus;
    private Button calibrate, cancelCalibration;
    private LinearLayout gestureSetup;
    private Button setupToggle;
    private CheckBox faceToggle;
    private CheckBox foundryToggle;
    private Spinner modelChoice, confirmationChoice;
    private TextView facialModelStatus;
    private final java.util.List<Button> phraseTests=new java.util.ArrayList<>();
    private EditText address;
    private CameraPreview preview;
    private CheckBox previewToggle;
    private Button start, stop, chooseSpeaker, testSpeaker, healthButton, guardianButton;
    private TextView guardianStatus, prototypeNote;
    private boolean visible;
    private long nextCounts;
    private final Runnable refreshVideo = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            if (MonitorState.preview) preview.invalidate();
            handler.postDelayed(this, 67);
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            status.setText(MonitorState.manualMode?"Manual speaker mode":MonitorState.running ? "Monitoring" : "Ready to start");
            preview.setVisibility(MonitorState.running&&!MonitorState.manualMode&&previewToggle.isChecked()?View.VISIBLE:View.GONE);
            progress.setVisibility(MonitorState.manualMode?View.GONE:View.VISIBLE);
            boolean smsOn=GuardianSms.enabled(MainActivity.this)&&GuardianSms.permitted(MainActivity.this);
            guardianButton.setText(smsOn?"Guardian · SMS on":"Guardian · SMS off");
            guardianStatus.setText(MonitorState.guardian);
            guardianStatus.setVisibility(MonitorState.guardian.startsWith("No guardian")?View.GONE:View.VISIBLE);
            prototypeNote.setText(smsOn?"Prototype mode. Three blinks can send a real SMS to your saved guardian.":"Prototype mode. Guardian SMS is off.");
            var reading=HealthState.latest;
            boolean liveHealth=HealthState.connected&&reading!=null&&reading.fresh(SystemClock.elapsedRealtime());
            healthButton.setText("Health & history");
            progress.setText((MonitorState.running ? MonitorState.sequence : 0) + " / 3");
            feedback.setText(MonitorState.feedback);
            feedback.setVisibility(MonitorState.running?View.VISIBLE:View.GONE);
            if (counts.isShown() && SystemClock.elapsedRealtime() >= nextCounts) {
                counts.setText("This session: " + MonitorState.sessionEvents + " test gestures\nSaved tests: " + store.count(false) + "  ·  Queued: " + store.count(true));
                nextCounts = SystemClock.elapsedRealtime() + 1000;
            }
            cloud.setText(MonitorState.cloud);
            speakerStatus.setText(MonitorState.speaker);
            lastBlinkSpeaker.setText(MonitorState.lastBlinkSpeaker);
            faceFeedback.setText(simpleGestureStatus());
            faceFeedback.setVisibility(MonitorState.running?View.VISIBLE:View.GONE);
            eyeStatus.setVisibility(MonitorState.running&&!MonitorState.manualMode?View.VISIBLE:View.GONE);
            boolean freshFace=MonitorState.running && SystemClock.elapsedRealtime()-MonitorState.trackingAt<=600;
            faceSignals.setText(freshFace?MonitorState.faceSignals:"Mouth / smile / eyebrows: waiting for camera");
            faceSetup.setText(MonitorState.faceSetup);
            deliveryStatus.setText(MonitorState.speaker.startsWith("Speaker")?MonitorState.speaker:"Speaker: "+MonitorState.speaker);
            calibrationStatus.setText(MonitorState.calibrationStatus);
            boolean capturing=MonitorState.calibrationStage.equals("RELAXED")||MonitorState.calibrationStage.equals("OPEN");
            calibrate.setEnabled(MonitorState.running&&!MonitorState.manualMode&&!capturing);
            calibrate.setText(MonitorState.calibrationStage.equals("WAIT_OPEN")?"Capture open mouth":"Set up my gestures");
            boolean setupActive=!MonitorState.calibrationStage.equals("IDLE");
            if(setupActive){gestureSetup.setVisibility(View.VISIBLE);setupToggle.setText("Gesture setup in progress");}
            else setupToggle.setText(gestureSetup.getVisibility()==View.VISIBLE?"Hide gesture setup":"Set up gestures");
            setupToggle.setEnabled(!setupActive);
            cancelCalibration.setVisibility(setupActive?View.VISIBLE:View.GONE);
            cancelCalibration.setEnabled(MonitorState.running&&!MonitorState.calibrationStage.equals("IDLE"));
            lastFaceMessage.setText(MonitorState.lastFaceMessage);
            lastFaceMessage.setVisibility(MonitorState.lastFaceMessage.startsWith("No facial message")?View.GONE:View.VISIBLE);
            lastBlinkSpeaker.setVisibility(MonitorState.lastBlinkSpeaker.startsWith("No blink alert")?View.GONE:View.VISIBLE);
            faceToggle.setEnabled(!MonitorState.running);
            foundryToggle.setEnabled(!MonitorState.running);
            modelChoice.setEnabled(!MonitorState.running);
            confirmationChoice.setEnabled(!MonitorState.running);
            facialModelStatus.setText(MonitorState.facialModelStatus);
            if(MonitorState.running)faceToggle.setChecked(MonitorState.faceMessagesEnabled);
            for(Button test:phraseTests)test.setEnabled(MonitorState.running&&MonitorState.calibrationStage.equals("IDLE"));
            chooseSpeaker.setEnabled(!MonitorState.running);
            testSpeaker.setEnabled(MonitorState.running);
            testSpeaker.setAlpha(MonitorState.running?1f:.45f);
            stop.setAlpha(MonitorState.running?1f:.45f);
            start.setAlpha(MonitorState.running?.45f:1f);
            boolean freshEyes = MonitorState.running && SystemClock.elapsedRealtime()-MonitorState.trackingAt <= 600;
            eyeStatus.setText(freshEyes ? MonitorState.eyeStatus : MonitorState.running ? "Waiting for eye tracking…" : "Start monitoring and face the camera");
            rate.setText(MonitorState.manualMode?"Manual speaker mode. Camera detection is off.":MonitorState.running ? String.format(Locale.US,"Video %.1f fps · Face tracking %.1f fps",MonitorState.cameraFps,MonitorState.fps) : "Detection runs on this phone. No laptop required.");
            start.setEnabled(!MonitorState.running); stop.setEnabled(MonitorState.running); address.setEnabled(!MonitorState.running);
            handler.postDelayed(this, 200);
        }
    };
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        view.setPadding(0,dp(4),0,dp(6)); return view;
    }
    private GradientDrawable background(int color, int radius) { GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);result.setPadding(dp(18),dp(16),dp(18),dp(16));result.setBackground(background(Color.WHITE,18));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);parent.addView(result,params);return result;
    }
    private Button button(String label, LinearLayout parent, boolean primary) {
        Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setTextSize(16);button.setTextColor(primary?Color.WHITE:INK);
        button.setBackground(background(primary?TEAL:0xffe6eef4,12));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(50));params.topMargin=dp(8);parent.addView(button,params);return button;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);store=new EventStore(this);
        var settings=getSharedPreferences("settings",MODE_PRIVATE);
        MonitorState.cloud=store.configured()?"Azure configured. Start monitoring to upload queued tests.":"Azure uploads are not configured.";
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(16),dp(16),dp(16));scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });
        TextView title=text("Blink Assist",26,INK,true);root.addView(title);
        title.setOnLongClickListener(view->{startActivity(new Intent(this,AdminActivity.class));return true;});
        root.addView(text("Live monitoring",14,MUTED,false));
        button("Back to dashboard",root,false).setOnClickListener(v->AppUi.open(this,HomeActivity.class));

        LinearLayout monitor=card(root);
        status=text("Ready to start",20,INK,true);monitor.addView(status);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);monitor.addView(controls);
        start=button("Start",controls,true);stop=button("Stop",controls,false);
        LinearLayout.LayoutParams startParams=new LinearLayout.LayoutParams(0,dp(52),1);startParams.rightMargin=dp(8);start.setLayoutParams(startParams);
        stop.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));
        previewToggle=new CheckBox(this);previewToggle.setText("Show live video");previewToggle.setTextColor(INK);
        previewToggle.setChecked(settings.getBoolean("showLiveVideo",true));
        preview=new CameraPreview(this);preview.setVisibility(previewToggle.isChecked()?View.VISIBLE:View.GONE);
        LinearLayout.LayoutParams videoParams=new LinearLayout.LayoutParams(-1,dp(210));videoParams.topMargin=dp(12);
        monitor.addView(preview,videoParams);
        previewToggle.setOnCheckedChangeListener((view,checked)->{
            settings.edit().putBoolean("showLiveVideo",checked).apply();
            MonitorState.preview=checked;preview.setVisibility(checked?View.VISIBLE:View.GONE);
            if(!checked)MonitorState.video=null;
        });
        eyeStatus=text("Start monitoring and face the camera",15,TEAL,true);monitor.addView(eyeStatus);
        faceFeedback=text("Start to check mouth and eyebrow gestures",15,INK,false);monitor.addView(faceFeedback);
        progress=text("0 / 3",30,TEAL,true);progress.setGravity(Gravity.CENTER);monitor.addView(progress);
        monitor.addView(text("Blink three times for Emergency",15,INK,true));
        feedback=text(MonitorState.feedback,14,MUTED,false);monitor.addView(feedback);
        deliveryStatus=text("Speaker: not connected",14,INK,false);monitor.addView(deliveryStatus);
        lastBlinkSpeaker=text("",14,INK,true);monitor.addView(lastBlinkSpeaker);
        lastFaceMessage=text("",14,INK,true);monitor.addView(lastFaceMessage);
        testSpeaker=button("Test speaker",monitor,false);
        testSpeaker.setOnClickListener(view->startService(new Intent(this,MonitorService.class).setAction("TEST_SPEAKER")));

        guardianStatus=text("",14,INK,false);monitor.addView(guardianStatus);
        var careLink=AppUi.column(this);var alertsLink=AppUi.column(this);var healthLink=AppUi.column(this);var guardianLink=AppUi.column(this);
        button("Care plan",careLink,false).setOnClickListener(v->startActivity(new Intent(this,CareActivity.class)));
        button("Alerts & sounds",alertsLink,false).setOnClickListener(v->startActivity(new Intent(this,AlertsActivity.class)));
        healthButton=button("Health & history",healthLink,false);
        healthButton.setOnClickListener(v->startActivity(new Intent(this,HealthActivity.class)));
        guardianButton=button("Guardian · SMS off",guardianLink,false);
        guardianButton.setOnClickListener(v->startActivity(new Intent(this,GuardianActivity.class)));
        AppUi.grid(root,careLink,alertsLink,healthLink,guardianLink);
        setupToggle=button("Set up gestures",root,false);
        gestureSetup=card(root);gestureSetup.setVisibility(View.GONE);
        setupToggle.setOnClickListener(view->{
            boolean show=gestureSetup.getVisibility()!=View.VISIBLE;
            gestureSetup.setVisibility(show?View.VISIBLE:View.GONE);
            setupToggle.setText(show?"Hide gesture setup":"Set up gestures");
        });
        gestureSetup.addView(text("Mouth and eyebrow messages",20,INK,true));
        faceToggle=new CheckBox(this);faceToggle.setText("Use facial messages");faceToggle.setTextColor(INK);
        faceToggle.setChecked(settings.getBoolean("faceMessagesEnabled",false));gestureSetup.addView(faceToggle);
        faceToggle.setOnCheckedChangeListener((view,checked)->{
            if(MonitorState.running)return;
            settings.edit().putBoolean("faceMessagesEnabled",checked).apply();
            MonitorState.faceFeedback=checked?"Start monitoring, then relax your face":"Facial messages are off";
        });
        foundryToggle=new CheckBox(this);foundryToggle.setText("Try Azure mouth & eyebrow detection");foundryToggle.setTextColor(INK);
        foundryToggle.setChecked(settings.getBoolean("useFoundryGestures",false));gestureSetup.addView(foundryToggle);
        foundryToggle.setOnCheckedChangeListener((view,checked)->{
            if(!MonitorState.running)settings.edit().putBoolean("useFoundryGestures",checked).apply();
        });
        gestureSetup.addView(text("Azure trial: sends a relaxed reference and gesture images over the internet. Uses Azure credits. Allow about 2–4 seconds; blinks stay local. Each trial lasts up to 10 minutes. Smile messages use the local mode.",13,MUTED,false));
        gestureSetup.addView(text("Start monitoring. Relax your face, then tap below for two seconds. In Azure mode this saves your relaxed reference. In local mode, next open your mouth when prompted.",14,MUTED,false));
        calibrationStatus=text(MonitorState.calibrationStatus,15,TEAL,true);gestureSetup.addView(calibrationStatus);
        calibrate=button("Set up my gestures",gestureSetup,true);
        calibrate.setOnClickListener(view->startService(new Intent(this,MonitorService.class).setAction(
                MonitorState.calibrationStage.equals("WAIT_OPEN")?"CAPTURE_MOUTH":"CALIBRATE_FACE")));
        cancelCalibration=button("Cancel setup",gestureSetup,false);
        cancelCalibration.setOnClickListener(view->startService(new Intent(this,MonitorService.class).setAction("CANCEL_CALIBRATION")));
        gestureSetup.addView(text("Open mouth: Please help me\nRaise eyebrows: Please come here\nSmile: Yes",15,INK,false));
        gestureSetup.addView(text("Relax between gestures. Stop monitoring to change the facial-message switch.",13,MUTED,false));

        Button advancedToggle=button("Advanced",root,false);
        LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);root.addView(advanced);
        advancedToggle.setOnClickListener(view->{
            boolean show=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(show?View.VISIBLE:View.GONE);
            advancedToggle.setText(show?"Hide advanced":"Advanced");
        });
        LinearLayout detection=card(advanced);detection.addView(text("Detection settings",20,INK,true));
        detection.addView(previewToggle);
        detection.addView(text("Stop monitoring before changing the tracker or timing.",13,MUTED,false));
        detection.addView(text("Mouth and eyebrow tracker",14,INK,true));
        modelChoice=choice(detection,new String[]{"Existing tracker (MediaPipe)","New tracker (ML Kit contours)"},"useMlKitContours");
        detection.addView(text("Both keep eye tracking. The new tracker needs its own gesture setup and a clear, close view of the whole face.",13,MUTED,false));
        detection.addView(text("Gesture confirmation",14,INK,true));
        confirmationChoice=choice(detection,new String[]{"Standard: hold for 1.5 seconds","Quick: mouth 0.5 s / eyebrows 0.7 s"},"quickFaceMessages");
        detection.addView(text("Quick accepts shorter gestures and may accept unintended movement. Smile still requires 1.5 seconds.",13,MUTED,false));
        facialModelStatus=text(MonitorState.facialModelStatus,13,MUTED,false);detection.addView(facialModelStatus);
        faceSignals=text(MonitorState.faceSignals,14,INK,false);detection.addView(faceSignals);
        rate=text("",13,MUTED,false);detection.addView(rate);
        counts=text("",13,MUTED,false);detection.addView(counts);

        LinearLayout camera=card(advanced);camera.addView(text("Camera connection",20,INK,true));
        camera.addView(text("Connect Wi-Fi to ESP32-CAM-MB.",14,MUTED,false));
        button("Open Wi-Fi settings",camera,false).setOnClickListener(view->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        address=new EditText(this);address.setSingleLine(true);address.setTextSize(14);address.setTextColor(INK);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setContentDescription("Camera stream address");
        address.setText(settings.getString("cameraUrl","http://192.168.4.1:81/stream"));camera.addView(address);

        LinearLayout speakerCard=card(advanced);speakerCard.addView(text("Speaker connection",20,INK,true));
        speakerCard.addView(text("Pair Wheelchair-Speaker, then select it. Keep camera Wi-Fi connected. Sound does not need internet.",14,MUTED,false));
        speakerSelection=text(settings.getString("speakerLabel","No speaker selected"),14,INK,false);speakerCard.addView(speakerSelection);
        speakerStatus=text(MonitorState.speaker,14,MUTED,false);speakerCard.addView(speakerStatus);
        button("Open Bluetooth settings",speakerCard,false).setOnClickListener(view->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        chooseSpeaker=button("Select paired speaker",speakerCard,false);chooseSpeaker.setOnClickListener(view->selectSpeaker());
        faceSetup=text(MonitorState.faceSetup,14,MUTED,false);speakerCard.addView(faceSetup);
        for(FaceMessages.Message message:FaceMessages.Message.values()) {
            Button test=button("Test: "+message.phrase,speakerCard,false);phraseTests.add(test);
            test.setOnClickListener(view->startService(new Intent(this,MonitorService.class).setAction("TEST_PHRASE").putExtra("track",message.track)));
        }

        LinearLayout azure=card(advanced);azure.addView(text("Optional Azure test uploads",20,INK,true));
        cloud=text("",14,MUTED,false);azure.addView(cloud);
        button("Import Azure setup",azure,false).setOnClickListener(view->{
            Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(choose,11);
        });
        button("Send connection test",azure,false).setOnClickListener(view->{
            if(!store.configured()){Toast.makeText(this,"Import the Azure setup file first.",Toast.LENGTH_LONG).show();return;}
            MonitorState.cloud="Sending a labeled connection test...";
            new Thread(()->{
                try(NetworkRoutes routes=new NetworkRoutes(getApplicationContext())){store.add("connectivity_test",0);MonitorState.cloud=store.upload(routes);}
                catch(Exception error){MonitorState.cloud="Connection test could not be sent. Check setup and internet.";}
            },"azure-connection-test").start();
        });
        prototypeNote=text("Prototype mode. Guardian SMS is off.",12,MUTED,false);root.addView(prototypeNote);
        start.setOnClickListener(view->{
            try {
                NetworkRoutes.cameraUrl(address.getText().toString());
                settings.edit().putString("cameraUrl",address.getText().toString().trim()).apply();
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);
                startForegroundService(new Intent(this,MonitorService.class));
            }catch(Exception invalid){
                advanced.setVisibility(View.VISIBLE);advancedToggle.setText("Hide advanced");
                Toast.makeText(this,"Check the camera address under Advanced",Toast.LENGTH_LONG).show();
            }
        });
        stop.setOnClickListener(view->stopService(new Intent(this,MonitorService.class)));
        setContentView(scroll);
    }
    private String simpleGestureStatus() {
        if(MonitorState.manualMode)return "Manual speaker controls are active. Stop to return to camera monitoring.";
        if(!MonitorState.running)return "Start to check mouth and eyebrow gestures";
        String stage=MonitorState.calibrationStage;
        if(!stage.equals("IDLE"))return "Gesture setup: "+MonitorState.calibrationStatus;
        if(!MonitorState.faceMessagesEnabled)return "Facial messages are off. Stop, then enable them in Set up gestures.";
        String message=MonitorState.faceFeedback;
        if(message.contains("Calibrate this tracker"))return "Tap Set up gestures before using mouth messages.";
        if(message.contains("200 px")||message.contains("too small")||message.contains("larger face"))
            return "Mouth needs a closer, clearer view. Keep the whole face visible.";
        if(message.contains("ML Kit unavailable"))return "Mouth tracker unavailable. Check Advanced; eye tracking can continue.";
        if(message.contains("delayed")||message.contains("fresh face"))return "Mouth tracking is delayed. Keep your face steady.";
        if(message.contains("Starting ML Kit"))return "Starting mouth tracking...";
        if(message.startsWith("Ready:"))return "Gestures ready. Open your mouth or raise your eyebrows.";
        return "Gestures: "+message;
    }
    private Spinner choice(LinearLayout parent,String[] labels,String key) {
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(getSharedPreferences("settings",MODE_PRIVATE).getBoolean(key,false)?1:0);
        parent.addView(spinner,new LinearLayout.LayoutParams(-1,dp(52)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> view) { }
            @Override public void onItemSelected(AdapterView<?> view,View selected,int position,long id) {
                if(MonitorState.running)return;
                var settings=getSharedPreferences("settings",MODE_PRIVATE);
                settings.edit().putBoolean(key,position==1).apply();
                if(key.equals("useMlKitContours")) {
                    MonitorState.facialModelStatus=position==1?"ML Kit selected for mouth / eyebrows; existing blink tracker retained":"Existing tracker selected";
                    boolean saved=FaceCalibration.Profile.decode(settings.getString(ContourGeometry.profileKey(position==1),""))!=null;
                    MonitorState.calibrationStatus=saved?"Saved calibration available for this tracker":
                            position==1?"Start monitoring and calibrate the new tracker":"Calibrate if mouth opening is not detected";
                }
            }
        });
        return spinner;
    }
    private void selectSpeaker() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},12); return;
        }
        try {
            android.bluetooth.BluetoothManager manager=getSystemService(android.bluetooth.BluetoothManager.class);
            android.bluetooth.BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){Toast.makeText(this,"Turn on Bluetooth and pair Wheelchair-Speaker first",Toast.LENGTH_LONG).show();return;}
            java.util.List<android.bluetooth.BluetoothDevice> devices=new java.util.ArrayList<>();
            for(var device:adapter.getBondedDevices()) if("Wheelchair-Speaker".equals(device.getName())) devices.add(device);
            if(devices.isEmpty()){Toast.makeText(this,"Pair Wheelchair-Speaker in Bluetooth settings, then select it here",Toast.LENGTH_LONG).show();return;}
            String[] labels=new String[devices.size()];
            for(int i=0;i<labels.length;i++)labels[i]=devices.get(i).getName()+" · "+devices.get(i).getAddress();
            new android.app.AlertDialog.Builder(this).setTitle("Select controller speaker").setItems(labels,(dialog,index)->{
                getSharedPreferences("settings",MODE_PRIVATE).edit().putString("speakerAddress",devices.get(index).getAddress()).putString("speakerLabel",labels[index]).apply();
                speakerSelection.setText(labels[index]);MonitorState.speaker="Speaker selected. Start monitoring to connect.";
            }).setNegativeButton("Cancel",null).show();
        } catch(Exception error){Toast.makeText(this,"Could not read paired devices. Check Bluetooth permission.",Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==12&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)selectSpeaker();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==11 && result==RESULT_OK && data!=null && data.getData()!=null){
            try(var input=getContentResolver().openInputStream(data.getData())){
                if(input==null)throw new IllegalArgumentException();
                byte[] bytes=new byte[8193]; int used=0, read;
                while(used<bytes.length && (read=input.read(bytes,used,bytes.length-used))!=-1) used+=read;
                if(used>8192)throw new IllegalArgumentException();
                store.importConfig(new String(bytes,0,used,StandardCharsets.UTF_8));
                MonitorState.cloud="Azure setup imported. Ready to send test events.";
                Toast.makeText(this,"Azure setup imported",Toast.LENGTH_SHORT).show();
            }catch(Exception invalid){Toast.makeText(this,"Could not import this device setup file.",Toast.LENGTH_LONG).show();}
        }
    }
    @Override protected void onStart(){super.onStart();visible=true;MonitorState.preview=previewToggle!=null&&previewToggle.isChecked();handler.post(refresh);handler.post(refreshVideo);}
    @Override protected void onStop(){visible=false;handler.removeCallbacks(refresh);handler.removeCallbacks(refreshVideo);MonitorState.preview=false;MonitorState.image=null;MonitorState.video=null;super.onStop();}
}
