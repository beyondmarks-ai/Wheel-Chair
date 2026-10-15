package org.wheelchair.blink;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Base64;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/** Optional bounded cloud trial. Networking never runs on the eye-tracking thread. */
final class FoundryTracker implements AutoCloseable {
    static final String ENDPOINT="https://wheelchair-gestures-7bc2336b.azurewebsites.net/api/gesture";
    private final NetworkRoutes routes;
    private final String key;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean busy,closed;
    private volatile HttpURLConnection connection;
    private record Answer(FoundryGestureGate owner,FoundryGestureGate.Ticket ticket,String mouth,String brow,boolean visible,long latency,String error){ }
    private final AtomicReference<Answer> answer=new AtomicReference<>();
    private FoundryGestureGate gate;
    private Bitmap neutral;
    private boolean capturing;
    private long captureStarted=-1,lastCapture=-1,trialStarted,lastRequest=-10000,diagnosticAt,captureDeadline;
    private int captureCount,requests;
    private double mouthSum,browSum,minMouth,maxMouth,minBrow,maxBrow;
    private float neutralX,neutralY;
    private volatile String status="Azure trial: capture a relaxed face first";
    FoundryTracker(NetworkRoutes routes,SharedPreferences settings){this.routes=routes;key=settings.getString("foundryFunctionKey","");}
    String status(){return status;}
    void calibrate(){
        gate=null;answer.set(null);capturing=true;clearCapture();
        captureDeadline=SystemClock.elapsedRealtime()+15000;
        MonitorState.calibrationStage="RELAXED";
        MonitorState.calibrationStatus="Relax your mouth and eyebrows for two seconds";
    }
    void cancelCalibration(){capturing=false;clearCapture();MonitorState.calibrationStage="IDLE";}
    private void clearCapture(){captureStarted=lastCapture=-1;captureCount=0;mouthSum=browSum=0;minMouth=minBrow=Double.POSITIVE_INFINITY;maxMouth=maxBrow=Double.NEGATIVE_INFINITY;}
    void invalidate(){if(gate!=null)gate.cancel();answer.set(null);if(capturing)clearCapture();}
    FaceMessages.Message observe(Bitmap frame,long time,boolean valid,double mouth,double brow,float x,float y){
        long now=SystemClock.elapsedRealtime();
        valid &= now-time<=500&&Double.isFinite(mouth)&&Double.isFinite(brow);
        if(key.isEmpty()){status="Azure setup missing on this phone";MonitorState.faceFeedback=status;return null;}
        if(capturing){
            if(now>=captureDeadline){cancelCalibration();MonitorState.calibrationStatus="Reference not captured. Face the camera and try again.";return null;}
            if(now-diagnosticAt>=1000){diagnosticAt=now;android.util.Log.i("WheelchairFoundry","reference valid="+valid+" samples="+captureCount+" mouth="+mouth+" brow="+brow);}
            if(!valid||(lastCapture>=0&&time-lastCapture>500))clearCapture();
            if(valid){
                if(captureStarted<0){captureStarted=time;neutralX=x;neutralY=y;}
                if(Math.hypot(x-neutralX,y-neutralY)>.08){clearCapture();return null;}
                lastCapture=time;captureCount++;mouthSum+=mouth;browSum+=brow;
                minMouth=Math.min(minMouth,mouth);maxMouth=Math.max(maxMouth,mouth);
                minBrow=Math.min(minBrow,brow);maxBrow=Math.max(maxBrow,brow);
                if(maxMouth-minMouth>.04||maxBrow-minBrow>.035){clearCapture();return null;}
                if(time-captureStarted>=1000&&captureCount>=6){
                    if(neutral!=null)neutral.recycle();
                    neutral=frame.copy(Bitmap.Config.ARGB_8888,false);
                    gate=new FoundryGestureGate(mouthSum/captureCount,browSum/captureCount);
                    capturing=false;trialStarted=now;requests=0;
                    MonitorState.calibrationStage="IDLE";MonitorState.calibrated=true;
                    MonitorState.calibrationStatus="Relaxed face saved for this session";
                    status="Azure trial ready (up to 10 minutes)";
                }
            }
            MonitorState.faceFeedback=valid?"Relax your face while the reference is captured":"Show your whole face and open both eyes for setup";
            return null;
        }
        if(gate==null){MonitorState.faceFeedback="Tap Set up gestures and capture a relaxed face";return null;}
        if(now-trialStarted>600000||requests>=120){
            invalidate();MonitorState.faceFeedback="Azure trial finished. Capture a relaxed face to try again.";status="Trial limit reached; eye blinks still work";return null;
        }
        boolean sameView=valid&&Math.hypot(x-neutralX,y-neutralY)<.15;
        gate.observe(time,sameView,mouth,brow);
        MonitorState.faceFeedback=gate.feedback(now);
        MonitorState.faceSignals=String.format(java.util.Locale.US,"Mouth change %.3f / brow change %.3f",mouth-gate.neutralMouth,brow-gate.neutralBrow);
        Answer result=answer.getAndSet(null);
        if(result!=null&&result.owner==gate){
            status=result.error==null?"Azure: "+result.mouth+" mouth / "+result.brow+" eyebrows ("+result.latency+" ms)":result.error;
            android.util.Log.i("WheelchairFoundry",status);
            if(result.error==null){
                FaceMessages.Message message=gate.accept(result.ticket,now,result.mouth,result.brow,result.visible);
                if(message!=null)return message;
                if(now-result.ticket.captured()>4000)status="Azure reply too late; no message played";
            }
        }
        var ticket=gate.request(now);
        if(ticket!=null&&!busy&&now-lastRequest>=2500){
            var network=routes.internet();
            if(network==null){status="Azure needs internet; eye blinks still work";MonitorState.faceFeedback=status;return null;}
            busy=true;lastRequest=now;requests++;
            android.util.Log.i("WheelchairFoundry","Request "+requests+": "+ticket.kind());
            FoundryGestureGate owner=gate;
            Bitmap current=frame.copy(Bitmap.Config.ARGB_8888,false),reference=neutral.copy(Bitmap.Config.ARGB_8888,false);
            worker.execute(()->{
                long started=SystemClock.elapsedRealtime();
                HttpURLConnection call=null;
                try{
                    JSONObject body=new JSONObject().put("neutral",jpeg(reference)).put("current",jpeg(current));
                    call=(HttpURLConnection)network.openConnection(new URL(ENDPOINT));connection=call;
                    call.setConnectTimeout(3000);call.setReadTimeout(6000);call.setInstanceFollowRedirects(false);
                    call.setRequestMethod("POST");call.setDoOutput(true);
                    call.setRequestProperty("Content-Type","application/json");call.setRequestProperty("x-functions-key",key);
                    try(var output=call.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                    if(call.getResponseCode()!=200)throw new IllegalStateException("HTTP "+call.getResponseCode());
                    byte[] bytes;
                    try(var input=call.getInputStream()){
                        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] chunk=new byte[512];int n;
                        while((n=input.read(chunk))!=-1){output.write(chunk,0,n);if(output.size()>4096)throw new IllegalStateException("Oversized response");}bytes=output.toByteArray();
                    }
                    JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                    if(!closed)answer.set(new Answer(owner,ticket,json.getString("mouth"),json.getString("eyebrows"),json.getBoolean("face_visible"),SystemClock.elapsedRealtime()-started,null));
                }catch(Exception error){if(!closed)answer.set(new Answer(owner,ticket,"unknown","unknown",false,0,"Azure unavailable or delayed; eye blinks still work"));}
                finally{if(call!=null)call.disconnect();connection=null;current.recycle();reference.recycle();busy=false;}
            });
        }
        return null;
    }
    private static String jpeg(Bitmap bitmap){
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,80,bytes);
        return Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);
    }
    @Override public void close(){closed=true;if(connection!=null)connection.disconnect();worker.shutdownNow();if(neutral!=null)neutral.recycle();}
}
