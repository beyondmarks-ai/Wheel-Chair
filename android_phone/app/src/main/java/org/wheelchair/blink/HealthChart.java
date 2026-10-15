package org.wheelchair.blink;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Time-series graph with explicit gaps for absent or invalid measurements. */
final class HealthChart extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private java.util.List<HealthStore.Point> points=java.util.List.of();
    private long start,end;
    HealthChart(Context context){super(context);setContentDescription("Sensor history graph; gaps indicate unavailable readings");}
    void data(java.util.List<HealthStore.Point> values,long from,long to){points=values;start=from;end=to;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float d=getResources().getDisplayMetrics().density;
        float l=48*d,r=getWidth()-12*d,t=18*d,b=getHeight()-36*d;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);c.drawPaint(paint);
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(var p:points)if(p.value()!=null){min=Math.min(min,p.value());max=Math.max(max,p.value());}
        paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);paint.setColor(0xff526675);
        if(!Double.isFinite(min)||end<=start){c.drawText("No recorded readings in this period",12*d,(t+b)/2,paint);return;}
        double padding=Math.max(1,(max-min)*.15);min-=padding;max+=padding;
        for(int i=0;i<4;i++){
            float y=t+(b-t)*i/3;paint.setColor(0xffe2e8ed);c.drawLine(l,y,r,y,paint);
            paint.setColor(0xff526675);c.drawText(String.format(Locale.US,max-min<5?"%.1f":"%.0f",max-(max-min)*i/3),2*d,y+4*d,paint);
        }
        path.reset();boolean drawing=false;long previous=0;
        long gap=Math.max(15000,(end-start)/720*2);
        for(var p:points){
            if(p.value()==null){drawing=false;continue;}
            float x=l+(r-l)*(p.at()-start)/(float)(end-start);
            float y=(float)(b-(b-t)*(p.value()-min)/(max-min));
            if(!drawing||p.at()-previous>gap)path.moveTo(x,y);else path.lineTo(x,y);
            paint.setColor(0xff087e8b);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,2*d,paint);
            drawing=true;previous=p.at();
        }
        paint.setColor(0xff087e8b);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        DateTimeFormatter format=DateTimeFormatter.ofPattern(end-start>86400000?"dd MMM":"HH:mm");
        String a=format.format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()));
        String z=format.format(Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()));
        paint.setColor(0xff526675);c.drawText(a,l,getHeight()-10*d,paint);c.drawText(z,r-paint.measureText(z),getHeight()-10*d,paint);
    }
}
