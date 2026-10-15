package org.wheelchair.blink;

import android.graphics.Bitmap;
import android.os.SystemClock;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional bundled ML Kit model. One inference at a time; never blocks the blink worker. */
final class ContourTracker implements AutoCloseable {
    record Sample(long received,double x,double y,ContourGeometry.Features features,String status) { }
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private FaceDetector detector;
    private volatile boolean closed;
    private volatile Sample latest;
    private volatile String status="Starting ML Kit mouth / eyebrow tracker";
    private long submitted=-1;
    private volatile long duration;
    Sample latest(){return latest;}
    String status(){return status;}
    long duration(){return duration;}
    synchronized void submit(Bitmap bitmap,long time) {
        if(closed || time-submitted<120 || !busy.compareAndSet(false,true))return;
        submitted=time;
        worker.execute(()->{
            long started=SystemClock.elapsedRealtime();
            try {
                if(closed){busy.set(false);return;}
                if(detector==null)detector=FaceDetection.getClient(new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        // Landmarks retain Euler angles with FAST + contours,
                        // so the frontal-pose check below has real angle data.
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE).build());
                detector.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(Runnable::run,task->{
                    try {
                        if(closed)return;
                        if(!task.isSuccessful())throw new IllegalStateException("ML Kit inference failed",task.getException());
                        var faces=task.getResult();
                        if(faces.size()!=1)throw new IllegalArgumentException("ML Kit: show one complete face");
                        var face=faces.get(0);var box=face.getBoundingBox();
                        if(Math.abs(face.getHeadEulerAngleY())>20||Math.abs(face.getHeadEulerAngleX())>20)
                            throw new IllegalArgumentException("ML Kit: face the camera straight on");
                        double cx=box.exactCenterX()/bitmap.getWidth(),cy=box.exactCenterY()/bitmap.getHeight();
                        var le=points(face,FaceContour.LEFT_EYE);var re=points(face,FaceContour.RIGHT_EYE);
                        var lb=points(face,FaceContour.LEFT_EYEBROW_BOTTOM);var rb=points(face,FaceContour.RIGHT_EYEBROW_BOTTOM);
                        var upper=points(face,FaceContour.UPPER_LIP_BOTTOM);var lower=points(face,FaceContour.LOWER_LIP_TOP);
                        // 200 px is a contour-quality recommendation, not a model input contract.
                        // Check the actual features: eye spacing >=35 px, lips >=18 px, all in frame.
                        // A box extending above the hairline need not hide any required feature.
                        ContourGeometry.requireVisible(bitmap.getWidth(),bitmap.getHeight(),le,re,lb,rb,upper,lower);
                        var geometry=ContourGeometry.measure(le,re,lb,rb,upper,lower);
                        status="ML Kit mouth / eyebrows tracked";
                        latest=new Sample(time,cx,cy,geometry,status);
                    } catch(Exception error){publishFailure(time,error);}
                    finally {duration=SystemClock.elapsedRealtime()-started;busy.set(false);}
                });
            } catch(Exception error){publishFailure(time,error);busy.set(false);}
        });
    }
    private void publishFailure(long time,Exception error) {
        if(closed)return;
        status=error instanceof IllegalArgumentException?error.getMessage():"ML Kit unavailable. Blinks remain active; stop and select Existing tracker to retry";
        latest=new Sample(time,Double.NaN,Double.NaN,null,status);
    }
    private static ContourGeometry.Point[] points(Face face,int type) {
        var contour=face.getContour(type);
        if(contour==null)throw new IllegalArgumentException("ML Kit: mouth / eyebrows are not visible");
        return contour.getPoints().stream().map(p->new ContourGeometry.Point(p.x,p.y)).toArray(ContourGeometry.Point[]::new);
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        worker.execute(()->{if(detector!=null)detector.close();});
        worker.shutdown();
    }
}
