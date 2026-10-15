package org.wheelchair.blink;

import android.content.Context;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

/** Created, invoked and closed on the same model thread (required by the GPU). */
final class TrackingModel implements AutoCloseable {
    private final Context context;
    private FaceLandmarker model;
    private boolean gpu;
    TrackingModel(Context context) {
        this.context = context;
        try { model = create(context, Delegate.GPU); gpu = true; }
        catch (RuntimeException unavailable) {
            android.util.Log.w("WheelchairBlink", "GPU unavailable; using CPU", unavailable);
            model = create(context, Delegate.CPU);
        }
    }
    static FaceLandmarker create(Context context, Delegate delegate) {
        var options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").setDelegate(delegate).build())
                .setRunningMode(RunningMode.VIDEO).setNumFaces(2)
                .setMinFaceDetectionConfidence(.6f).setMinFacePresenceConfidence(.6f)
                .setMinTrackingConfidence(.6f).setOutputFaceBlendshapes(true).build();
        return FaceLandmarker.createFromOptions(context, options);
    }
    FaceLandmarkerResult detect(MPImage image, long timestamp) {
        try { return model.detectForVideo(image, timestamp); }
        catch (RuntimeException error) {
            if (!gpu) throw error;
            android.util.Log.w("WheelchairBlink", "GPU inference failed; switching to CPU", error);
            model.close(); model = null; gpu = false;
            model = create(context, Delegate.CPU);
            return model.detectForVideo(image, timestamp);
        }
    }
    @Override public void close() { if (model != null) model.close(); }
}
