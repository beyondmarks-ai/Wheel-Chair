package org.wheelchair.blink;

import android.content.Context;
import android.media.*;

/** Activity-owned preview, stopped when leaving the screen. */
final class SoundPreview implements AutoCloseable {
    private MediaPlayer player;
    boolean play(Context c,int resource){
        close();
        try{
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            try(var file=c.getResources().openRawResourceFd(resource)){player.setDataSource(file.getFileDescriptor(),file.getStartOffset(),file.getLength());}
            player.setOnCompletionListener(p->close());player.setOnErrorListener((p,a,b)->{close();return true;});
            player.prepare();player.start();return true;
        }catch(Exception error){close();return false;}
    }
    @Override public void close(){if(player!=null){player.release();player=null;}}
}
