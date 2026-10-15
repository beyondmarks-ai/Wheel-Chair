package org.wheelchair.blink;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.mediapipe.tasks.core.Delegate;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VideoSmokeTest {
    @Test public void cpuVideoFallbackLoadsAndPreservesCameraBitmap() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap camera=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);camera.eraseColor(Color.GRAY);
        try(var model=TrackingModel.create(context, Delegate.CPU)) {
            for(int frame=1;frame<=3;frame++)try(var image=MonitorService.inferenceImage(camera)){
                assertEquals(0,model.detectForVideo(image,frame*100L).faceLandmarks().size());
            }
        }
        assertFalse(camera.isRecycled());camera.recycle();
    }
    @Test public void liveVideoDrawsWithoutAnyTrackingResult() {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(()->{
            CameraPreview view=new CameraPreview(instrumentation.getTargetContext());view.layout(0,0,320,240);
            Bitmap source=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);source.eraseColor(Color.BLUE);
            Bitmap output=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);
            try {
                MonitorState.running=true;MonitorState.video=new MonitorState.PreviewFrame(source,SystemClock.elapsedRealtime());MonitorState.eyes=null;
                view.draw(new Canvas(output));assertEquals(Color.BLUE,output.getPixel(160,120));
                MonitorState.video=new MonitorState.PreviewFrame(source,SystemClock.elapsedRealtime()-2000);
                view.draw(new Canvas(output));assertNotEquals("A stale frame must disappear",Color.BLUE,output.getPixel(10,10));
            } finally { MonitorState.running=false;MonitorState.video=null;source.recycle();output.recycle(); }
        });
    }
}
