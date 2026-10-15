package org.wheelchair.blink;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.*;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MonitorService extends Service {
    private static volatile MonitorService current;
    static boolean obstacleDemoReady(){var s=current;return s!=null&&s.active&&s.speaker!=null&&s.speaker.careReady()&&!s.speaker.busy();}
    static boolean manualReady(int track){
        MonitorService s=current;
        return ManualRecording.forTrack(track)!=null&&s!=null&&s.active&&s.speaker!=null
                &&s.speaker.ready()&&(track==1||s.speaker.faceReady())
                &&(!s.speaker.busy()||(track==1&&!s.speaker.emergencyBusy()));
    }
    static BluetoothSpeaker.TriggerResult reminder(String id,int track,long notifyAt){
        MonitorService s=current;
        return s!=null&&s.active&&s.speaker!=null?s.speaker.reminder(id,track,notifyAt):BluetoothSpeaker.TriggerResult.NOT_CONNECTED;
    }
    private static final String CHANNEL = "blink_monitor";
    private volatile boolean active;
    private volatile HttpURLConnection stream;
    private final AtomicReference<Frame> latest = new AtomicReference<>();
    private ExecutorService cameraWorker, modelWorker;
    private ExecutorService eventWorker;
    private ScheduledExecutorService cloudWorker;
    private NetworkRoutes routes;
    private EventStore store;
    private BluetoothSpeaker speaker;
    private boolean faceMessagesEnabled;
    private final AtomicReference<String> calibrationAction=new AtomicReference<>();
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private record Frame(Bitmap bitmap, long received) { }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(intent!=null&&"OBSTACLE_DEMO".equals(intent.getAction())){
            if(getSharedPreferences("settings",0).getBoolean("obstacleDemoOnCard",false)){
                var result=active&&speaker!=null?speaker.trigger(java.util.UUID.randomUUID().toString(),2999):BluetoothSpeaker.TriggerResult.NOT_CONNECTED;
                String detail="DEMO: obstacle announcement - "+deliveryText(result);
                MonitorState.adminResult=detail;
                if(eventWorker!=null)eventWorker.execute(()->HealthStore.get(this).event("Obstacle demonstration",detail));
            }
            if(!active)stopSelf();return START_NOT_STICKY;
        }
        if(intent!=null&&"ADMIN_PLAY".equals(intent.getAction())){
            ManualRecording recording=ManualRecording.forTrack(intent.getIntExtra("track",0));
            if(recording!=null){
                var result=active&&speaker!=null?speaker.trigger(java.util.UUID.randomUUID().toString(),recording.track)
                        :BluetoothSpeaker.TriggerResult.NOT_CONNECTED;
                String detail="Manual: "+recording.phrase+" - "+deliveryText(result);
                MonitorState.adminResult=detail;
                android.util.Log.i("WheelchairManual",detail);
                if(eventWorker!=null)eventWorker.execute(()->HealthStore.get(this).event("Manual speaker",detail));
            }
            if(!active)stopSelf();
            return START_NOT_STICKY;
        }
        if(intent!=null && ("CALIBRATE_FACE".equals(intent.getAction())||"CAPTURE_MOUTH".equals(intent.getAction())||"CANCEL_CALIBRATION".equals(intent.getAction()))) {
            if(active)calibrationAction.set(intent.getAction());
            return START_NOT_STICKY;
        }
        if (intent != null && "TEST_SPEAKER".equals(intent.getAction())) {
            if (active && speaker != null) speaker.trigger(java.util.UUID.randomUUID().toString());
            return START_NOT_STICKY;
        }
        if (intent != null && "TEST_PHRASE".equals(intent.getAction())) {
            int track=intent.getIntExtra("track",0);
            if(active && speaker!=null && track>=3 && track<=5) {
                var message=FaceMessages.Message.values()[track-3];
                MonitorState.lastFaceMessage="Manual test: "+message.phrase+" — "+deliveryText(speaker.trigger(java.util.UUID.randomUUID().toString(),track));
            }
            return START_NOT_STICKY;
        }
        if (active) return START_NOT_STICKY;
        MonitorState.manualMode=intent!=null&&"ADMIN_CONNECT".equals(intent.getAction());
        String urlText = getSharedPreferences("settings", MODE_PRIVATE).getString("cameraUrl", "http://192.168.4.1:81/stream");
        final URL url;
        try { url = MonitorState.manualMode?null:NetworkRoutes.cameraUrl(urlText); }
        catch (Exception invalid) { MonitorState.status = "Check camera address"; stopSelf(); return START_NOT_STICKY; }
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "Camera blink monitoring", NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(1, notification());
        active = MonitorState.running = true;
        speaker = new BluetoothSpeaker(this, getSharedPreferences("settings", MODE_PRIVATE).getString("speakerAddress", ""));
        current=this;
        eventWorker=Executors.newSingleThreadExecutor();
        if(MonitorState.manualMode){
            MonitorState.adminResult="Connected mode started. Choose a recording when the speaker is ready.";
            MonitorState.status="Manual speaker mode";
            MonitorState.feedback="Camera detection is off. Hold the Blink Assist title to open the controls.";
            MonitorState.video=null;MonitorState.eyes=null;MonitorState.fps=MonitorState.cameraFps=0;
            MonitorState.sequence=0;MonitorState.calibrationStage="IDLE";
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WheelchairBlink:manual");
            wake.setReferenceCounted(false);renewWake();
            cloudWorker=Executors.newSingleThreadScheduledExecutor();
            cloudWorker.scheduleWithFixedDelay(this::renewWake,5,5,TimeUnit.MINUTES);
            return START_NOT_STICKY;
        }
        faceMessagesEnabled=getSharedPreferences("settings",MODE_PRIVATE).getBoolean("faceMessagesEnabled",false);
        MonitorState.faceMessagesEnabled=faceMessagesEnabled;
        MonitorState.faceFeedback=faceMessagesEnabled?"Relax your face to get ready":"Facial messages are off";
        MonitorState.lastFaceMessage="No facial message in this session";
        MonitorState.faceSignals="Mouth / smile / eyebrows: waiting for camera";
        MonitorState.faceSetup="Connecting to controller for facial messages";
        MonitorState.sessionEvents = MonitorState.completed = MonitorState.sequence = 0;
        MonitorState.lastBlinkSpeaker = "No blink alert in this session";
        MonitorState.guardian="No guardian SMS requested this session";
        MonitorState.video = null; MonitorState.eyes = null;
        MonitorState.cameraFps = MonitorState.fps = 0;
        MonitorState.eyeStatus = "Starting eye tracker";
        MonitorState.status = "Connecting to ESP32-CAM";
        routes = new NetworkRoutes(this); store = new EventStore(this);
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WheelchairBlink:monitor");
        wake.setReferenceCounted(false);
        renewWake();
        WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (manager != null) { wifi = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WheelchairBlink:camera"); wifi.acquire(); }
        cameraWorker = Executors.newSingleThreadExecutor(); modelWorker = Executors.newSingleThreadExecutor(); cloudWorker = Executors.newSingleThreadScheduledExecutor();
        cameraWorker.execute(() -> readCamera(url)); modelWorker.execute(this::detect);
        cloudWorker.scheduleWithFixedDelay(() -> {
            renewWake();
            try { MonitorState.cloud = store.upload(routes); } catch (Exception ignored) { MonitorState.cloud = "Tests remain queued"; }
        }, 1, 5, TimeUnit.SECONDS);
        return START_NOT_STICKY;
    }
    private synchronized void renewWake() { if (active && wake != null) wake.acquire(10 * 60 * 1000L); }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, MonitorService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle(MonitorState.manualMode?"Wheelchair speaker connected":"Wheelchair blink test is running")
                .setContentText(MonitorState.manualMode?"Manual speaker mode. Camera detection is off.":"Watching camera gestures. Tap to check status.").setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
    }
    private void readCamera(URL url) {
        long reportAt = SystemClock.elapsedRealtime(); int frames = 0;
        while (active) {
            HttpURLConnection connection = null;
            try {
                connection = routes.camera(url); stream = connection;
                connection.setConnectTimeout(3000); connection.setReadTimeout(2500); connection.setInstanceFollowRedirects(false);
                if (connection.getResponseCode() != 200) throw new IllegalStateException("Camera HTTP response");
                try (var input = connection.getInputStream()) {
                    byte[] chunk = new byte[8192]; ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    boolean inside = false; int previous = -1;
                    int length;
                    while (active && (length = input.read(chunk)) >= 0) {
                        for (int i = 0; i < length; i++) {
                            int value = chunk[i] & 255;
                            if (!inside && previous == 255 && value == 216) { jpeg.reset(); jpeg.write(255); jpeg.write(216); inside = true; }
                            else if (inside) {
                                jpeg.write(value);
                                if (previous == 255 && value == 217) {
                                    byte[] bytes = jpeg.toByteArray();
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                    if (bitmap != null) {
                                        long received = SystemClock.elapsedRealtime();
                                        latest.set(new Frame(bitmap, received));
                                        // Live video must not wait for model processing.
                                        MonitorState.video = MonitorState.preview ? new MonitorState.PreviewFrame(bitmap, received) : null;
                                        frames++;
                                        if (received-reportAt >= 1000) {
                                            MonitorState.cameraFps = frames*1000.0/(received-reportAt);
                                            reportAt = received; frames = 0;
                                        }
                                    }
                                    inside = false; jpeg.reset();
                                } else if (jpeg.size() > 2_000_000) throw new IllegalStateException("Oversized camera frame");
                            }
                            previous = value;
                        }
                    }
                }
            } catch (Exception ignored) { if (active) MonitorState.status = "Camera unavailable. Check its Wi-Fi and power."; }
            finally {
                if (connection != null) connection.disconnect(); stream = null;
                latest.set(null); MonitorState.video = null; MonitorState.eyes = null; MonitorState.cameraFps = 0;
                frames = 0; reportAt = SystemClock.elapsedRealtime();
            }
            if (active) SystemClock.sleep(1000);
        }
    }
    private void detect() {
        Gesture gesture = new Gesture(); long lastFrame = -1, reportAt = SystemClock.elapsedRealtime(); int frames = 0;
        var settings=getSharedPreferences("settings",MODE_PRIVATE);
        boolean useFoundry=settings.getBoolean("useFoundryGestures",false);
        MonitorState.foundryMode=useFoundry;
        boolean useContours=!useFoundry&&settings.getBoolean("useMlKitContours",false);
        FaceMessages faceMessages=new FaceMessages(settings.getBoolean("quickFaceMessages",false),useContours?600:350);
        FaceCalibration calibration=new FaceCalibration(useContours?600:350);
        String profileKey=ContourGeometry.profileKey(useContours);
        calibration.profile=FaceCalibration.Profile.decode(settings.getString(profileKey,""));
        MonitorState.calibrationStatus=calibration.profile==null?"Calibrate if mouth opening is not detected":"Saved mouth calibration loaded";
        if(useContours&&calibration.profile==null)MonitorState.calibrationStatus="ML Kit requires its own calibration. Start with step 1 below";
        MonitorState.facialModelStatus=useContours?"Starting ML Kit; existing blink tracker stays active":"Existing tracker for eyes and facial messages";
        MonitorState.calibrated=calibration.profile!=null;
        MonitorState.calibrationStage="IDLE";
        if(useFoundry){MonitorState.calibrated=false;MonitorState.calibrationStatus="Capture a relaxed face for this Azure session";}
        FaceSampleGate contourGate=new FaceSampleGate();
        float previousX = Float.NaN, previousY = Float.NaN;
        try (TrackingModel model = new TrackingModel(this);
             ContourTracker contours = useContours?new ContourTracker():null;
             FoundryTracker foundry = useFoundry?new FoundryTracker(routes,settings):null) {
            while (active) {
                Frame frame = latest.get(); long now = SystemClock.elapsedRealtime();
                String action=calibrationAction.getAndSet(null);
                if(foundry!=null&&action!=null){
                    if(action.equals("CALIBRATE_FACE"))foundry.calibrate();
                    else foundry.cancelCalibration();
                    action=null;
                }
                if(action!=null) {
                    if(action.equals("CALIBRATE_FACE"))calibration.start(now);
                    else if(action.equals("CAPTURE_MOUTH"))calibration.captureOpen(now);
                    else calibration.cancel();
                    faceMessages.reset("Calibration changed. Relax your face first");
                    MonitorState.calibrationStatus=calibration.feedback;
                }
                boolean calibrating=calibration.active();calibration.tick(now);
                if(calibrating)MonitorState.calibrationStatus=calibration.feedback;
                if(foundry==null)MonitorState.calibrationStage=calibration.stage.name();
                if (frame == null || now - frame.received > 500) {
                    if(foundry!=null)foundry.invalidate();
                    MonitorState.image = null;
                    MonitorState.eyes = null; MonitorState.eyeStatus = "Waiting for camera video";
                    gesture.reset("No fresh video. Gesture cancelled."); previousX = previousY = Float.NaN;
                    faceMessages.reset("Waiting for fresh camera video");
                    calibration.update(now,false,Double.NaN,new double[8]);
                    MonitorState.faceFeedback=faceMessagesEnabled?faceMessages.feedback:"Facial messages are off";
                    MonitorState.faceSignals="Mouth / smile / eyebrows: waiting for camera";
                    MonitorState.status = "Waiting for camera video"; MonitorState.feedback = gesture.feedback; MonitorState.sequence = 0;
                    if (now - reportAt > 1000) { MonitorState.fps = 0; reportAt = now; frames = 0; }
                    SystemClock.sleep(40); continue;
                }
                if (frame.received <= lastFrame) { SystemClock.sleep(5); continue; }
                lastFrame = frame.received; frames++;
                // Start both local models from this frame, instead of adding MediaPipe
                // latency before the contour worker can even begin its inference.
                if(contours!=null)contours.submit(frame.bitmap,frame.received);
                try (var image = inferenceImage(frame.bitmap)) {
                    var result = model.detect(image, frame.received);
                    boolean valid = false; double left = Double.NaN, right = Double.NaN;
                    double mouthRatio=Double.NaN;
                    double browRatio=Double.NaN;
                    boolean mouthVisible=false;
                    double smileLeft=Double.NaN,smileRight=Double.NaN,jaw=Double.NaN,innerBrow=Double.NaN,outerLeft=Double.NaN,outerRight=Double.NaN;
                    MonitorState.Eyes eyes = null;
                    MonitorState.status = "Show one face with both eyes visible";
                    MonitorState.eyeStatus = "Eyes not tracked — face the camera";
                    if (result.faceLandmarks().size() == 1 && result.faceBlendshapes().isPresent() && !result.faceBlendshapes().get().isEmpty()) {
                        var points = result.faceLandmarks().get(0);
                        for (var score : result.faceBlendshapes().get().get(0)) {
                            if (score.categoryName().equals("eyeBlinkLeft")) left = score.score();
                            if (score.categoryName().equals("eyeBlinkRight")) right = score.score();
                            switch(score.categoryName()) {
                                case "mouthSmileLeft": smileLeft=score.score(); break;
                                case "mouthSmileRight": smileRight=score.score(); break;
                                case "jawOpen": jaw=score.score(); break;
                                case "browInnerUp": innerBrow=score.score(); break;
                                case "browOuterUpLeft": outerLeft=score.score(); break;
                                case "browOuterUpRight": outerRight=score.score(); break;
                            }
                        }
                        float minX=1,maxX=0,minY=1,maxY=0;
                        for (var p : points) { minX=Math.min(minX,p.x()); maxX=Math.max(maxX,p.x()); minY=Math.min(minY,p.y()); maxY=Math.max(maxY,p.y()); }
                        float x=(minX+maxX)/2, y=(minY+maxY)/2;
                        boolean jump = !Float.isNaN(previousX) && Math.hypot(x-previousX,y-previousY) > .15;
                        double eyeSpan = Math.abs(points.get(33).x()-points.get(263).x())*frame.bitmap.getWidth();
                        valid = Double.isFinite(left) && Double.isFinite(right) && (maxX-minX)*frame.bitmap.getWidth() >= 65 && eyeSpan >= 25 && !jump;
                        mouthVisible=true;
                        for(int index:new int[]{13,14,61,291}) {
                            var point=points.get(index);
                            if(point.x()<.02||point.x()>.98||point.y()<.02||point.y()>.98)mouthVisible=false;
                        }
                        var ml=points.get(61);var mr=points.get(291);var mu=points.get(13);var md=points.get(14);
                        mouthRatio=FaceCalibration.mouthRatio(ml.x()*frame.bitmap.getWidth(),ml.y()*frame.bitmap.getHeight(),
                                mr.x()*frame.bitmap.getWidth(),mr.y()*frame.bitmap.getHeight(),
                                mu.x()*frame.bitmap.getWidth(),mu.y()*frame.bitmap.getHeight(),
                                md.x()*frame.bitmap.getWidth(),md.y()*frame.bitmap.getHeight());
                        mouthVisible &= Double.isFinite(mouthRatio);
                        // Brow-to-eye distance normalized by eye span; cloud compares the images.
                        double span=Math.hypot((points.get(33).x()-points.get(263).x())*frame.bitmap.getWidth(),
                                (points.get(33).y()-points.get(263).y())*frame.bitmap.getHeight());
                        double lb=Math.hypot((points.get(105).x()-points.get(159).x())*frame.bitmap.getWidth(),
                                (points.get(105).y()-points.get(159).y())*frame.bitmap.getHeight());
                        double rb=Math.hypot((points.get(334).x()-points.get(386).x())*frame.bitmap.getWidth(),
                                (points.get(334).y()-points.get(386).y())*frame.bitmap.getHeight());
                        browRatio=span>0?(lb+rb)/(2*span):Double.NaN;
                        previousX=x; previousY=y;
                        MonitorState.status = valid ? (faceMessagesEnabled?"Face tracked: facial messages + blinks":"Blink mode — facial messages off") : "Move closer and keep your whole face steady";
                        float leftX=(points.get(33).x()+points.get(133).x())/2, leftY=(points.get(33).y()+points.get(133).y())/2;
                        float rightX=(points.get(263).x()+points.get(362).x())/2, rightY=(points.get(263).y()+points.get(362).y())/2;
                        eyes = new MonitorState.Eyes(leftX,leftY,rightX,rightY,frame.received,valid);
                        MonitorState.eyeStatus = valid ? (left >= .50 && right >= .50 ? "Both eyes closed" :
                                left <= .35 && right <= .35 ? "Both eyes open" : "Eyes moving — blink both together") : "Move closer; both eyes must be clear";
                    } else {
                        previousX=previousY=Float.NaN;
                        if (result.faceLandmarks().size() > 1) MonitorState.eyeStatus = "More than one face — keep only the user in view";
                    }
                    long processedAt = SystemClock.elapsedRealtime();
                    if (processedAt - frame.received > 500) {
                        valid = false; eyes = null;
                        MonitorState.status = "Eye tracking is slow";
                        MonitorState.eyeStatus = "Tracking delayed — keep your face still and improve lighting";
                    }
                    MonitorState.eyes = eyes; MonitorState.trackingAt = frame.received;
                    // Do not count another sequence only to silently discard it
                    // while the previous manual/blink audio request is active.
                    boolean speakerBusy = speaker.emergencyBusy();
                    boolean blinkTriggered=false;
                    if (speakerBusy) gesture.reset("Speaker finishing previous alert. Wait, then blink three times.");
                    if (!speakerBusy && gesture.update(frame.received,left,right,valid)) {
                        blinkTriggered=true;
                        // Sound must not wait for disk access, Azure configuration or internet.
                        String alertId=java.util.UUID.randomUUID().toString();
                        BluetoothSpeaker.TriggerResult delivery = speaker.trigger(alertId);
                        try{CareAlerts.emergency(this);}catch(Exception notificationFailure){android.util.Log.w("WheelchairBlink","Emergency phone notification unavailable",notificationFailure);}
                        boolean guardianEnabled=GuardianSms.enabled(this);
                        long observed=SystemClock.elapsedRealtime(),closure=gesture.closedDuration;
                        eventWorker.execute(()->{
                            try{HealthStore.get(this).event("Emergency","Three-blink signal; "+deliveryText(delivery));}catch(Exception ignored){}
                            // SMS is independent of Bluetooth playback and Azure uploads.
                            try {
                                if(guardianEnabled&&SystemClock.elapsedRealtime()-observed<=30000)
                                    MonitorState.guardian=GuardianSms.send(this,alertId,false);
                                else if(guardianEnabled){
                                    HealthStore.get(this).beginAlert(alertId,false);
                                    HealthStore.get(this).alertStatus(alertId,"NOT SENT: alert expired before dispatch");
                                    MonitorState.guardian="Guardian alert expired before dispatch";
                                }
                            }catch(Exception failure){MonitorState.guardian="Guardian SMS failed; check setup and History";}
                            try{store.add("blink_sos_test",closure);}catch(Exception error){MonitorState.cloud="Could not save this test event";}
                        });
                        MonitorState.sessionEvents++;
                        String outcome;
                        switch (delivery) {
                            case REQUESTED: outcome = "speaker request started; check speaker status"; break;
                            case BUSY: outcome = "speaker busy; wait a few seconds, then blink again"; break;
                            default: outcome = "speaker disconnected; alert was not sent";
                        }
                        MonitorState.lastBlinkSpeaker = "Blink alert " + MonitorState.sessionEvents + ": " + outcome;
                        android.util.Log.i("WheelchairBlink", MonitorState.lastBlinkSpeaker);
                        Vibrator vibrator = getSystemService(Vibrator.class);
                        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                    // A single ordinary blink must not add the triple-blink
                    // sequence timeout before facial neutral/hold detection.
                    // Closed eyes still reset FaceMessages; a completed SOS is
                    // processed above and immediately blocks/preempts phrases.
                    double[] scores={left,right,smileLeft,smileRight,jaw,innerBrow,outerLeft,outerRight};
                    if(foundry!=null){
                        boolean available=valid&&mouthVisible&&left<=.35&&right<=.35&&!blinkTriggered&&!speakerBusy&&faceMessagesEnabled;
                        var message=foundry.observe(frame.bitmap,frame.received,available,mouthRatio,browRatio,previousX,previousY);
                        MonitorState.facialModelStatus=foundry.status();
                        if(message!=null)sendFaceMessage(message);
                    } else if(contours==null) {
                        updateFace(faceMessages,calibration,settings,profileKey,false,frame.received,
                                valid&&mouthVisible,mouthRatio,scores,blinkTriggered);
                    } else {
                        // Both models run independently. Only current single-user MediaPipe
                        // confirmation plus spatially matching fresh contours can produce a phrase.
                        var sample=contours.latest();
                        boolean matched=sample!=null&&FaceSampleGate.matches(processedAt,frame.received,
                                sample.received(),previousX,previousY,sample.x(),sample.y());
                        MonitorState.facialModelStatus=contours.status()+" ("+contours.duration()+" ms inference)";
                        boolean usable=valid&&matched&&sample.features()!=null;
                        // A consumed sample can age out while the next inference runs.
                        // Do not count it again or erase a hold between distinct fresh samples.
                        // The next new observation still checks its age and the inter-sample gap.
                        boolean awaitingNext=valid&&sample!=null&&sample.features()!=null&&contourGate.consumed(sample.received())
                                &&Math.hypot(previousX-sample.x(),previousY-sample.y())<=.12;
                        if(!usable&&!awaitingNext) {
                            String reason=!valid?"Show one steady face":sample==null?contours.status():
                                    sample.features()==null?sample.status():!matched?"ML Kit result delayed; waiting for a fresh face view":sample.status();
                            faceMessages.reset(reason);
                            calibration.update(processedAt,false,Double.NaN,new double[8]);
                            MonitorState.faceSignals="Mouth / eyebrows: waiting for clear contours";
                            MonitorState.faceFeedback=reason;
                            if(calibration.active())MonitorState.calibrationStatus=reason;
                        } else if(usable&&contourGate.consume(sample.received())) {
                            // Eye and smile scores come from the current frame, never a
                            // previous frame captured while the contour model was starting.
                            double[] combined=sample.features().scores(scores);
                            updateFace(faceMessages,calibration,settings,profileKey,true,sample.received(),
                                    true,sample.features().mouth(),combined,blinkTriggered);
                        }
                        // Never count a cached contour result as a new observation.
                        if(blinkTriggered||left>.35||right>.35) {
                            faceMessages.reset("Open both eyes; blinks take priority");
                            MonitorState.faceFeedback=faceMessages.feedback;
                        }
                    }
                    if(processedAt-reportAt>=1000)android.util.Log.i("WheelchairFace",
                            "tracker="+(useFoundry?"azure":useContours?"mlkit":"mediapipe")+" enabled="+faceMessagesEnabled+
                            " valid="+valid+" calibrated="+(calibration.profile!=null)+" status="+MonitorState.faceFeedback);
                    if(contours!=null&&processedAt-reportAt>=1000){var s=contours.latest();
                        android.util.Log.i("WheelchairContour","inferenceMs="+contours.duration()+" ageMs="+(s==null?-1:processedAt-s.received())+
                                " centerDelta="+(s==null?-1:Math.hypot(previousX-s.x(),previousY-s.y()))+" status="+(s==null?"none":s.status()));}
                    MonitorState.feedback = gesture.feedback; MonitorState.sequence = gesture.blinks.size(); MonitorState.completed = gesture.completed;
                }
                now = SystemClock.elapsedRealtime();
                if (now - reportAt >= 1000) { MonitorState.fps = frames*1000.0/(now-reportAt); frames=0; reportAt=now; }
            }
        } catch (Exception error) {
            android.util.Log.e("WheelchairBlink", "Detector stopped", error);
            MonitorState.feedback = "Detector stopped. Reopen the app and restart.";
            stopSelf();
        }
    }
    private void updateFace(FaceMessages messages,FaceCalibration calibration,
                            android.content.SharedPreferences settings,String profileKey,boolean contours,
                            long time,boolean valid,double mouthRatio,double[] scores,boolean blinkTriggered) {
        boolean wasCalibrating=calibration.active();
        if(calibration.update(time,valid,mouthRatio,scores)) {
            settings.edit().putString(profileKey,calibration.profile.encode()).putBoolean("faceMessagesEnabled",true).apply();
            faceMessagesEnabled=MonitorState.faceMessagesEnabled=true;
            MonitorState.calibrated=true;
        }
        if(wasCalibrating) {
            MonitorState.calibrationStatus=calibration.feedback;
            messages.reset("Calibration finished. Relax your face first");
        }
        MonitorState.calibrationStage=calibration.stage.name();
        boolean contourEyesOpen=!contours||(scores[0]<=.35&&scores[1]<=.35);
        if(calibration.profile!=null)scores=contours?ContourGeometry.normalize(calibration.profile,mouthRatio,scores)
                :calibration.profile.normalize(mouthRatio,scores);
        boolean needsCalibration=contours&&calibration.profile==null;
        boolean canDetect=faceMessagesEnabled&&!calibration.active()&&!wasCalibrating&&!blinkTriggered&&!needsCalibration&&contourEyesOpen;
        FaceMessages.Message message=messages.update(time,valid,canDetect,scores[0],scores[1],
                scores[2],scores[3],scores[4],scores[5],scores[6],scores[7]);
        MonitorState.faceSignals=valid?messages.signals():"Mouth / smile / eyebrows: face not clear";
        MonitorState.faceFeedback=calibration.active()?calibration.feedback:
                needsCalibration?"Calibrate this tracker first: tap step 1 below":
                !faceMessagesEnabled?"Facial messages are off - stop and enable the switch above":
                !valid?"Show your whole face and mouth; move closer if needed":messages.feedback;
        if(message!=null)sendFaceMessage(message);
    }
    private void sendFaceMessage(FaceMessages.Message message){
            MonitorState.lastFaceMessage=message.gesture+" -> "+message.phrase+" - "+
                    deliveryText(speaker.trigger(java.util.UUID.randomUUID().toString(),message.track));
            android.util.Log.i("WheelchairBlink",MonitorState.lastFaceMessage);
            String detail=MonitorState.lastFaceMessage;
            eventWorker.execute(()->{try{HealthStore.get(this).event("Facial message",detail);}catch(Exception ignored){}});
    }
    private static String deliveryText(BluetoothSpeaker.TriggerResult result) {
        switch(result) {
            case REQUESTED: return "speaker request sent; check speaker status";
            case BUSY: return "speaker busy; message was not sent";
            case NOT_READY: return "recordings/controller setup needed";
            default: return "speaker disconnected; message was not sent";
        }
    }
    static MPImage inferenceImage(Bitmap frame) {
        // MPImage.close() recycles its bitmap. Keep the camera/preview bitmap
        // separate so a visible preview never draws recycled image memory.
        return new BitmapImageBuilder(frame.copy(Bitmap.Config.ARGB_8888, false)).build();
    }
    @Override public void onDestroy() {
        if(current==this)current=null;
        synchronized (this) {
            active = MonitorState.running = false;
            MonitorState.manualMode=false;
            if (wake != null && wake.isHeld()) wake.release();
        }
        MonitorState.status = "Not monitoring"; MonitorState.image = null; latest.set(null);
        MonitorState.video = null; MonitorState.eyes = null; MonitorState.eyeStatus = "Eyes not tracked";
        MonitorState.faceFeedback="Not monitoring";
        MonitorState.calibrationStage="IDLE";
        if (stream != null) stream.disconnect();
        if (cameraWorker != null) cameraWorker.shutdownNow();
        if (modelWorker != null) modelWorker.shutdownNow();
        if (cloudWorker != null) cloudWorker.shutdownNow();
        if (eventWorker != null) eventWorker.shutdown();
        if (speaker != null) speaker.close();
        if (routes != null) routes.close();
        if (wifi != null && wifi.isHeld()) wifi.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
