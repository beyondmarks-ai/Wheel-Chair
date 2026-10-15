package org.wheelchair.blink;
import android.graphics.Bitmap;

public final class MonitorState {
    public static volatile boolean running = false, preview = false;
    public static volatile boolean manualMode;
    public static volatile String adminResult="No manual recording requested";
    public static volatile String notification="No alert posted this session";
    public static volatile String status = "Not monitoring", feedback = "Connect to the camera Wi-Fi, then start.", cloud = "Azure setup not checked";
    public static volatile int sequence = 0, completed = 0, sessionEvents = 0;
    public static volatile double fps = 0;
    public static volatile String facialModelStatus="Existing tracker selected";
    public static volatile boolean foundryMode;
    public static volatile String guardian="No guardian SMS requested this session";
    public static volatile Bitmap image;
    public record PreviewFrame(Bitmap bitmap, long received) { }
    public record Eyes(float leftX, float leftY, float rightX, float rightY, long received, boolean valid) {
        boolean visibleAt(long now, long videoTime) {
            return now >= received && now - received <= 500 && Math.abs(videoTime-received) <= 250;
        }
    }
    public static volatile PreviewFrame video;
    public static volatile Eyes eyes;
    public static volatile String eyeStatus = "Eyes not tracked";
    public static volatile long trackingAt;
    public static volatile double cameraFps;
    public static volatile String speaker = "Speaker not connected";
    public static volatile String lastBlinkSpeaker = "No blink alert in this session";
    public static volatile String faceFeedback="Facial messages are off", lastFaceMessage="No facial message in this session";
    public static volatile String faceSetup="Connect the updated controller to use facial messages";
    public static volatile String faceSignals="Mouth / smile / eyebrows: waiting for camera";
    public static volatile String calibrationStatus="Calibrate if mouth opening is not detected", calibrationStage="IDLE";
    public static volatile boolean calibrated, faceMessagesEnabled;
    private MonitorState() { }
}
