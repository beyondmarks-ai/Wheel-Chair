package org.wheelchair.blink;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Live camera frames and recent eye locations share the same letterboxed coordinates. */
final class CameraPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    CameraPreview(Context context) { super(context); setContentDescription("Live ESP32 camera with eye tracking markers"); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xff142d3d);
        long now = SystemClock.elapsedRealtime();
        MonitorState.PreviewFrame frame = MonitorState.video;
        if (frame == null || !MonitorState.running || now - frame.received() > 1000) {
            paint.setColor(Color.WHITE); paint.setTextSize(15 * getResources().getDisplayMetrics().scaledDensity);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(MonitorState.running ? "Waiting for live video…" : "Start monitoring to see live video", getWidth()/2f, getHeight()/2f, paint);
            return;
        }
        Bitmap bitmap = frame.bitmap();
        float scale = Math.min(getWidth()/(float)bitmap.getWidth(), getHeight()/(float)bitmap.getHeight());
        float width = bitmap.getWidth()*scale, height = bitmap.getHeight()*scale;
        float x = (getWidth()-width)/2, y = (getHeight()-height)/2;
        canvas.drawBitmap(bitmap, null, new RectF(x,y,x+width,y+height), paint);
        MonitorState.Eyes eyes = MonitorState.eyes;
        if (eyes != null && eyes.visibleAt(now, frame.received())) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
            paint.setColor(eyes.valid() ? 0xff4cff9c : 0xffffce54);
            float radius = Math.max(8, Math.abs(eyes.leftX()-eyes.rightX())*width*.17f);
            canvas.drawCircle(x+eyes.leftX()*width,y+eyes.leftY()*height,radius,paint);
            canvas.drawCircle(x+eyes.rightX()*width,y+eyes.rightY()*height,radius,paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
