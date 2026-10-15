package org.wheelchair.blink;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PhoneSmokeTest {
    @Test public void packagedModelLoadsAndRejectsBlankImage() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var options=FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setRunningMode(RunningMode.IMAGE).setNumFaces(2).setOutputFaceBlendshapes(true).build();
        Bitmap bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GRAY);
        try(FaceLandmarker model=FaceLandmarker.createFromOptions(context,options);var image=MonitorService.inferenceImage(bitmap)) {
            assertEquals(0,model.detect(image).faceLandmarks().size());
        }
        assertFalse("Inference must not recycle the preview bitmap",bitmap.isRecycled());
    }
    @Test public void localCameraAddressesOnly() throws Exception {
        assertEquals("192.168.4.1",NetworkRoutes.cameraUrl("http://192.168.4.1:81/stream").getHost());
        try { NetworkRoutes.cameraUrl("http://8.8.8.8/stream");fail("Public camera address accepted"); } catch(IllegalArgumentException expected) { }
    }
    @Test public void configuredAzureReceivesExplicitConnectionTest() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(EventStore store=new EventStore(context);NetworkRoutes routes=new NetworkRoutes(context)) {
            org.junit.Assume.assumeTrue("Provision emulator separately for cloud check",store.configured());
            store.add("connectivity_test",0);
            String result=store.upload(routes);
            assertEquals(result,0,store.count(true));
        }
    }
}
