package org.wheelchair.blink;

import android.content.Context;
import android.os.*;
import android.speech.tts.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

final class CareRecording implements AutoCloseable {
    interface Result{void done(File file,String error);}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Result callback;private final File raw,output;private TextToSpeech tts;private boolean ended;
    CareRecording(Context c,String title,Result callback){
        this.callback=callback;String key=UUID.randomUUID().toString();raw=new File(c.getCacheDir(),"speech-"+key+".wav");output=new File(c.getCacheDir(),"care-"+key+".wav");
        tts=new TextToSpeech(c.getApplicationContext(),status->handler.post(()->{
            if(ended)return;
            if(status!=TextToSpeech.SUCCESS){finish(null,"Install or enable a phone text-to-speech voice first");return;}
            int language=tts.setLanguage(Locale.getDefault());if(language<0){finish(null,"Download a voice for your language in Android speech settings");return;}
            if(tts.synthesizeToFile("Reminder. "+title,new Bundle(),raw,key)==TextToSpeech.ERROR)finish(null,"Could not prepare spoken recording");
        }));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onError(String id){handler.post(()->finish(null,"Phone speech engine could not create the recording"));}
            public void onDone(String id){new Thread(()->{try{Files.write(output.toPath(),CareWav.normalize(Files.readAllBytes(raw.toPath())));handler.post(()->finish(output,null));}catch(Exception e){handler.post(()->finish(null,e.getMessage()));}},"care-recording").start();}
        });
        handler.postDelayed(()->finish(null,"Voice preparation timed out. Check your phone speech settings."),30000);
    }
    private void finish(File file,String error){if(ended)return;close();callback.done(file,error);}
    @Override public void close(){ended=true;handler.removeCallbacksAndMessages(null);if(tts!=null){tts.stop();tts.shutdown();}raw.delete();}
}
