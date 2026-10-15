package org.wheelchair.blink;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

/** Local phone speech fallback when a matching wired recording cannot be requested. */
public final class CareVoiceService extends Service {
    private static volatile CareVoiceService current;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue=new ArrayDeque<>();
    private TextToSpeech tts;private boolean ready,ttsFailed,destroyed;private String speaking;private MediaPlayer player;
    private void stopAudio(){if(tts!=null)tts.stop();if(player!=null){player.release();player=null;}}
    static void cancel(String id){var s=current;if(s!=null)s.handler.post(()->{s.queue.removeIf(x->x.equals(id));if(id.equals(s.speaking)){s.stopAudio();s.speaking=null;s.pump();}});}
    static void cancelAll(){var s=current;if(s!=null)s.handler.post(()->{s.queue.clear();s.stopAudio();s.stopSelf();});}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();current=this;
        NotificationChannel ch=new NotificationChannel("care_voice","Speaking reminder",NotificationManager.IMPORTANCE_LOW);ch.setSound(null,null);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        Notification n=new Notification.Builder(this,"care_voice").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Speaking your reminder").build();
        if(Build.VERSION.SDK_INT>=29)startForeground(304,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(304,n);
    }
    private void prepareTts(){
        if(tts!=null)return;
        tts=new TextToSpeech(this,result->handler.post(()->{
            if(destroyed)return;
            if(result!=TextToSpeech.SUCCESS){ttsFailed=true;pump();return;}
            int language=tts.setLanguage(Locale.getDefault());
            if(language==TextToSpeech.LANG_MISSING_DATA||language==TextToSpeech.LANG_NOT_SUPPORTED){ttsFailed=true;pump();return;}
            tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            ready=true;pump();
        }));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}public void onDone(String id){handler.post(()->finished(id,true));}
            public void onError(String id){handler.post(()->finished(id,false));}
        });
        handler.postDelayed(()->{if(!ready&&!destroyed){ttsFailed=true;pump();}},15000);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){String id=intent.getStringExtra("id");if(id!=null&&!queue.contains(id)&&!id.equals(speaking))queue.add(id);}
        pump();return START_NOT_STICKY;
    }
    private void pump(){
        if(destroyed||speaking!=null)return;
        while(!queue.isEmpty()){
            String id=queue.peek();var o=CareStore.get(this).getOccurrence(id);
            if(o==null||!o.state().equals("PENDING")||!CareTime.timely(System.currentTimeMillis(),o.notifyAt())||CareAlerts.emergencyActive(this)){queue.remove();continue;}
            CareSound sound=CareSound.from(o.voiceKey());
            if(sound==CareSound.CUSTOM&&!ready){
                if(ttsFailed){queue.remove();CareStore.get(this).speaker(id,"Phone speech unavailable; notification remains available");continue;}
                prepareTts();return;
            }
            queue.remove();
            speaking=id;CareStore.get(this).speaker(id,"Phone voice requested");
            if(sound!=CareSound.CUSTOM){
                try{
                    player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    try(var file=getResources().openRawResourceFd(sound.resource)){player.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());}
                    player.setOnCompletionListener(p->finished(id,true));player.setOnErrorListener((p,a,b)->{finished(id,false);return true;});player.prepare();player.start();
                }catch(Exception failed){finished(id,false);return;}
            }else if(tts.speak("Reminder. "+o.title(),TextToSpeech.QUEUE_FLUSH,null,id)==TextToSpeech.ERROR){finished(id,false);return;}
            handler.postDelayed(()->{if(id.equals(speaking)){stopAudio();finished(id,false);}},25000);return;
        }stopSelf();
    }
    private void finished(String id,boolean success){
        if(!id.equals(speaking))return;
        if(player!=null){player.release();player=null;}
        CareStore.get(this).speaker(id,success?"Phone speech completed":"Phone speech not confirmed");speaking=null;pump();
    }
    @Override public void onDestroy(){
        destroyed=true;
        for(String id:queue)CareStore.get(this).speaker(id,"Phone voice stopped before playback");
        if(speaking!=null)CareStore.get(this).speaker(speaking,"Phone speech stopped; completion not confirmed");
        if(current==this)current=null;handler.removeCallbacksAndMessages(null);
        stopAudio();if(tts!=null)tts.shutdown();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
