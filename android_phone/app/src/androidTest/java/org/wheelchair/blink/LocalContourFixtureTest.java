package org.wheelchair.blink;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalContourFixtureTest {
    private ContourGeometry.Point[] points(Face face,int kind){
        return face.getContour(kind).getPoints().stream().map(p->new ContourGeometry.Point(p.x,p.y)).toArray(ContourGeometry.Point[]::new);
    }
    @Test public void inspectKnownOpenMouthWithoutCameraNetwork()throws Exception{
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var bitmap=BitmapFactory.decodeFile(new java.io.File(context.getCacheDir(),"contour-open-fixture.jpg").getPath());assertNotNull(bitmap);
        try(var model=FaceDetection.getClient(new FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL).build())){
            long start=SystemClock.elapsedRealtime();var faces=Tasks.await(model.process(InputImage.fromBitmap(bitmap,0)),8,TimeUnit.SECONDS);
            assertEquals(1,faces.size());var face=faces.get(0);
            var le=points(face,FaceContour.LEFT_EYE);var re=points(face,FaceContour.RIGHT_EYE);
            var lb=points(face,FaceContour.LEFT_EYEBROW_BOTTOM);var rb=points(face,FaceContour.RIGHT_EYEBROW_BOTTOM);
            var upper=points(face,FaceContour.UPPER_LIP_BOTTOM);var lower=points(face,FaceContour.LOWER_LIP_TOP);
            ContourGeometry.requireVisible(bitmap.getWidth(),bitmap.getHeight(),le,re,lb,rb,upper,lower);
            var geometry=ContourGeometry.measure(le,re,lb,rb,upper,lower);
            android.os.Bundle report=new android.os.Bundle();report.putString("stream","\nOffline open-mouth fixture: "+geometry+" inferenceMs="+(SystemClock.elapsedRealtime()-start)+"\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0,report);
            assertTrue("Open mouth should yield a clear lip gap",geometry.mouth()>.1);
        }finally{bitmap.recycle();}
    }
}
