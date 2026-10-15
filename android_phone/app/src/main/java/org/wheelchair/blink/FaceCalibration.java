package org.wheelchair.blink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Per-session guided reference capture. Stores numbers only, never camera images. */
final class FaceCalibration {
    private final long maxSampleGap;
    FaceCalibration(){this(350);}
    FaceCalibration(long maxSampleGap){this.maxSampleGap=maxSampleGap;}
    enum Stage { IDLE, RELAXED, WAIT_OPEN, OPEN }
    Stage stage=Stage.IDLE;
    String feedback="Calibrate your relaxed face and open mouth";
    Profile profile;
    private final List<double[]> samples=new ArrayList<>();
    private long expires, first=-1, previous=-1;
    private double closed;
    private double[] baseline;

    static final class Profile {
        final double closed, open;
        final double[] baseline;
        Profile(double closed,double open,double[] baseline) {
            if(!Double.isFinite(closed)||!Double.isFinite(open)||closed<0||open>2||open-closed<.10||baseline.length!=8)
                throw new IllegalArgumentException("Mouth references too close");
            for(int i=0;i<8;i++) if(!Double.isFinite(baseline[i])||baseline[i]<0||baseline[i]>=(i<2?.50:.85))
                throw new IllegalArgumentException("Relaxed reference invalid");
            this.closed=closed;this.open=open;this.baseline=baseline.clone();
        }
        double[] normalize(double ratio,double[] scores) {
            double[] out=scores.clone();
            if(out.length!=8)throw new IllegalArgumentException("Eight scores required");
            for(int i=0;i<8;i++) {
                double raw=out[i];
                out[i]=Double.isFinite(raw)&&raw>=0&&raw<=1?clamp((raw-baseline[i])/(1-baseline[i])):Double.NaN;
                if(i<2&&raw>=.50)out[i]=Math.max(.50,out[i]); // Never normalize a closed-eye score into open eyes.
            }
            out[4]=Double.isFinite(ratio)&&ratio>=0&&ratio<=2?clamp((ratio-closed)/(open-closed)):Double.NaN;
            return out;
        }
        String encode(){StringBuilder s=new StringBuilder("1,"+closed+","+open);for(double v:baseline)s.append(',').append(v);return s.toString();}
        static Profile decode(String encoded) {
            try {
                String[] parts=encoded.split(",");if(parts.length!=11||!parts[0].equals("1"))return null;
                double[] baseline=new double[8];for(int i=0;i<8;i++)baseline[i]=Double.parseDouble(parts[i+3]);
                return new Profile(Double.parseDouble(parts[1]),Double.parseDouble(parts[2]),baseline);
            }catch(RuntimeException error){return null;}
        }
    }
    static double clamp(double value){return Math.max(0,Math.min(1,value));}
    // Coordinates are in pixels, so non-square camera frames and image roll do
    // not distort the ratio. Callers also reject cropped/unstable faces.
    static double mouthRatio(double lx,double ly,double rx,double ry,double ux,double uy,double dx,double dy) {
        for(double v:new double[]{lx,ly,rx,ry,ux,uy,dx,dy})if(!Double.isFinite(v))return Double.NaN;
        double width=Math.hypot(rx-lx,ry-ly);
        if(width<18)return Double.NaN;
        double ratio=Math.hypot(dx-ux,dy-uy)/width;
        return ratio<=2?ratio:Double.NaN;
    }
    boolean active(){return stage!=Stage.IDLE;}
    void start(long now){stage=Stage.RELAXED;beginWindow(now);feedback="Keep your face relaxed and eyes open for 2 seconds";}
    void captureOpen(long now){if(stage==Stage.WAIT_OPEN){stage=Stage.OPEN;beginWindow(now);feedback="Hold your mouth comfortably open for 2 seconds";}}
    void cancel(){stage=Stage.IDLE;samples.clear();feedback=profile==null?"Calibration cancelled":"Using saved calibration";}
    void clear(){cancel();profile=null;feedback="Calibration cleared. Calibrate again for this user";}
    private void beginWindow(long now){samples.clear();first=previous=-1;expires=now+15000;}
    void tick(long now){if((stage==Stage.RELAXED||stage==Stage.OPEN)&&now>=expires){stage=Stage.IDLE;samples.clear();feedback="Calibration timed out. Show your whole face and try again";}}
    boolean update(long now,boolean valid,double ratio,double[] scores) {
        tick(now);
        if(stage!=Stage.RELAXED&&stage!=Stage.OPEN)return false;
        valid&=Double.isFinite(ratio)&&ratio>=0&&ratio<=2&&scores.length==8;
        for(double v:scores)if(!Double.isFinite(v)||v<0||v>1)valid=false;
        if(!valid||previous>=0&&(now<=previous||now-previous>maxSampleGap)) {
            samples.clear();first=previous=-1;feedback="Show one steady, complete face; capture restarts";return false;
        }
        previous=now;if(first<0)first=now;
        double[] row=Arrays.copyOf(scores,9);row[8]=ratio;samples.add(row);
        feedback=(stage==Stage.RELAXED?"Relax face, eyes open: ":"Hold mouth open: ")+Math.min(100,(now-first)*100/2000)+"%";
        if(now-first<2000||samples.size()<(maxSampleGap>350?6:12))return false;
        double low=quantile(8,.1),high=quantile(8,.9);
        if(high-low>.12) {fail("Mouth moved during capture. Hold still and recalibrate");return false;}
        if(stage==Stage.RELAXED) {
            baseline=new double[8];
            for(int i=0;i<8;i++) {
                baseline[i]=quantile(i,.5);
                if(baseline[i]>=(i<2?.50:.85)||quantile(i,.9)-quantile(i,.1)>.25) {
                    fail("Relax your face with eyes open and try again");return false;
                }
            }
            closed=high;stage=Stage.WAIT_OPEN;samples.clear();
            feedback="Now open your mouth comfortably and tap Capture open mouth";return false;
        }
        try {profile=new Profile(closed,low,baseline);}
        catch(IllegalArgumentException error){fail("Mouth positions too similar. Move camera closer and recalibrate");return false;}
        stage=Stage.IDLE;samples.clear();feedback="Calibration saved. Relax your face, then hold mouth open";return true;
    }
    private void fail(String message){stage=Stage.IDLE;samples.clear();feedback=message;}
    private double quantile(int column,double q){double[] values=new double[samples.size()];for(int i=0;i<values.length;i++)values[i]=samples.get(i)[column];Arrays.sort(values);return values[(int)Math.round((values.length-1)*q)];}
}
