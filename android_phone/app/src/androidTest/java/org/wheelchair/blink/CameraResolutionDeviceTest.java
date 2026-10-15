package org.wheelchair.blink;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Explicit live camera diagnostic; run only when the user requests a camera test. */
@RunWith(AndroidJUnit4.class)
public class CameraResolutionDeviceTest {
    private byte[] get(NetworkRoutes routes,URL url)throws Exception{
        var call=routes.camera(url);call.setConnectTimeout(3000);call.setReadTimeout(5000);call.setRequestProperty("Connection","close");
        try{assertEquals(200,call.getResponseCode());try(var input=call.getInputStream()){
            var out=new java.io.ByteArrayOutputStream();byte[] data=new byte[8192];int n;
            while((n=input.read(data))!=-1){out.write(data,0,n);assertTrue(out.size()<2000000);}return out.toByteArray();
        }}finally{call.disconnect();}
    }
    @Test public void enableVgaAndMeasureContours()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        URL stream=NetworkRoutes.cameraUrl(context.getSharedPreferences("settings",0).getString("cameraUrl","http://192.168.4.1:81/stream"));
        String base="http://"+stream.getHost();
        try(var routes=new NetworkRoutes(context)){
            JSONObject before=new JSONObject(new String(get(routes,new URL(base+"/status")),StandardCharsets.UTF_8));
            int old=before.getInt("framesize");boolean changed=false,verified=false;
            try{
                get(routes,new URL(base+"/control?var=framesize&val=8"));changed=true;
                SystemClock.sleep(500);
                try(var detector=FaceDetection.getClient(new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build())){
                    for(int i=0;i<5;i++){
                        byte[] jpeg=get(routes,new URL(base+"/capture"));var bitmap=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
                        assertNotNull(bitmap);assertEquals(640,bitmap.getWidth());assertEquals(480,bitmap.getHeight());verified=true;
                        long start=SystemClock.elapsedRealtime();
                        var faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),5,TimeUnit.SECONDS);
                        String info="VGA "+bitmap.getWidth()+"x"+bitmap.getHeight()+" ML Kit "+(SystemClock.elapsedRealtime()-start)+" ms faces="+faces.size();
                        if(faces.size()==1){var face=faces.get(0);var box=face.getBoundingBox();
                            info+=" face="+box.width()+"x"+box.height()+" lipPoints="+(face.getContour(FaceContour.UPPER_LIP_BOTTOM)==null?0:face.getContour(FaceContour.UPPER_LIP_BOTTOM).getPoints().size());}
                        android.util.Log.i("WheelchairCameraTest",info);bitmap.recycle();
                    }
                }
            }finally{if(changed&&!verified)get(routes,new URL(base+"/control?var=framesize&val="+old));}
        }
    }
}
