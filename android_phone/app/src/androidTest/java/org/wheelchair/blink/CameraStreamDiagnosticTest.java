package org.wheelchair.blink;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Read-only camera throughput measurement with both tracking models stopped. */
@RunWith(AndroidJUnit4.class)
public class CameraStreamDiagnosticTest {
    @Test public void measureCameraWithoutModels()throws Exception{
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        URL url=NetworkRoutes.cameraUrl(context.getSharedPreferences("settings",0).getString("cameraUrl","http://192.168.4.1:81/stream"));
        try(var routes=new NetworkRoutes(context)){
            var args=InstrumentationRegistry.getArguments();
            if(args.getString("qvga","").equals("true")){
                var control=routes.camera(new URL("http://"+url.getHost()+"/control?var=framesize&val=5"));
                control.setConnectTimeout(3000);control.setReadTimeout(5000);control.setRequestProperty("Connection","close");
                try{assertEquals(200,control.getResponseCode());}finally{control.disconnect();}
                SystemClock.sleep(1000);
            }
            var call=routes.camera(url);call.setConnectTimeout(3000);call.setReadTimeout(3000);
            try{
                assertEquals(200,call.getResponseCode());
                long start=SystemClock.elapsedRealtime(),last=-1,maxGap=0;int frames=0,previous=-1;boolean inside=false;
                var jpeg=new ByteArrayOutputStream();byte[] chunk=new byte[8192];String dimensions="none";
                try(var input=call.getInputStream()){
                    while(SystemClock.elapsedRealtime()-start<10000){
                        int n=input.read(chunk);if(n<0)break;
                        for(int i=0;i<n;i++){
                            int v=chunk[i]&255;
                            if(!inside&&previous==255&&v==216){jpeg.reset();jpeg.write(255);jpeg.write(216);inside=true;}
                            else if(inside){jpeg.write(v);if(previous==255&&v==217){
                                long now=SystemClock.elapsedRealtime();if(last>=0)maxGap=Math.max(maxGap,now-last);last=now;frames++;
                                if(frames==1){byte[] bytes=jpeg.toByteArray();var bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                                    assertNotNull(bitmap);dimensions=bitmap.getWidth()+"x"+bitmap.getHeight();bitmap.recycle();}
                                inside=false;
                            }}
                            previous=v;
                        }
                    }
                }finally{
                    android.os.Bundle report=new android.os.Bundle();report.putString("stream",
                        "\nCamera only: frames="+frames+" elapsedMs="+(SystemClock.elapsedRealtime()-start)+" maxGapMs="+maxGap+" dimensions="+dimensions+"\n");
                    InstrumentationRegistry.getInstrumentation().sendStatus(0,report);
                }
                assertTrue("No complete camera frames",frames>0);
                if(args.getString("qvga","").equals("true"))assertEquals("320x240",dimensions);
            }finally{call.disconnect();}
        }
    }
}
