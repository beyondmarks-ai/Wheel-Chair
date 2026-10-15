package org.wheelchair.blink;

/** Pixel geometry from ML Kit contours. Values are distances, not emotion probabilities. */
final class ContourGeometry {
    record Point(double x, double y) { }
    static void requireVisible(int width,int height,Point[]... features){
        for(Point[] feature:features){
            center(feature);
            for(Point p:feature)if(p.x<1||p.y<1||p.x>width-2||p.y>height-2)
                throw new IllegalArgumentException("Keep eyes, eyebrows and mouth inside the video");
        }
    }
    record Features(double mouth, double leftBrow, double rightBrow) {
        double[] scores(double[] mediaPipe) {
            double[] out=mediaPipe.clone();
            out[4]=0; // Mouth is calibrated separately using the lip aperture.
            out[5]=(leftBrow+rightBrow)/2;
            out[6]=leftBrow; out[7]=rightBrow;
            return out;
        }
    }
    static Features measure(Point[] leftEye, Point[] rightEye, Point[] leftBrow,
                            Point[] rightBrow, Point[] upperLip, Point[] lowerLip) {
        Point le=center(leftEye), re=center(rightEye), lb=center(leftBrow), rb=center(rightBrow);
        double span=Math.hypot(re.x-le.x,re.y-le.y);
        if(span<35)throw new IllegalArgumentException("Move closer: eyes too small");
        double ux=(re.x-le.x)/span, uy=(re.y-le.y)/span;
        // Point arrays may label anatomical left/right. Choose image-up normal.
        double nx=-uy, ny=ux;
        if(ny>0){nx=-nx;ny=-ny;}
        double l=((lb.x-le.x)*nx+(lb.y-le.y)*ny)/span;
        double r=((rb.x-re.x)*nx+(rb.y-re.y)*ny)/span;
        if(l<0||r<0||l>=.85||r>=.85)throw new IllegalArgumentException("Eyebrows not clear");
        center(upperLip);center(lowerLip); // Validate all coordinates and point counts.
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(Point[] lip:new Point[][]{upperLip,lowerLip})for(Point p:lip){
            double along=p.x*ux+p.y*uy;min=Math.min(min,along);max=Math.max(max,along);
        }
        double width=max-min;
        if(width<18)throw new IllegalArgumentException("Move closer: mouth too small");
        double mid=(min+max)/2;
        double top=middleHeight(upperLip,mid,width,ux,uy,nx,ny);
        double bottom=middleHeight(lowerLip,mid,width,ux,uy,nx,ny);
        double aperture=Math.abs(top-bottom)/width;
        if(!Double.isFinite(aperture)||aperture>2)throw new IllegalArgumentException("Mouth not clear");
        return new Features(aperture,l,r);
    }
    private static Point center(Point[] points) {
        if(points==null||points.length<3)throw new IllegalArgumentException("Face contours missing");
        double x=0,y=0;
        for(Point p:points){
            if(p==null||!Double.isFinite(p.x)||!Double.isFinite(p.y))throw new IllegalArgumentException("Invalid contours");
            x+=p.x;y+=p.y;
        }
        return new Point(x/points.length,y/points.length);
    }
    private static double middleHeight(Point[] lip,double mid,double width,double ux,double uy,double nx,double ny) {
        double sum=0;int count=0;
        for(Point p:lip)if(Math.abs(p.x*ux+p.y*uy-mid)<=width*.2){sum+=p.x*nx+p.y*ny;count++;}
        if(count==0)throw new IllegalArgumentException("Center of mouth missing");
        return sum/count;
    }
    static String profileKey(boolean contours){return contours?"faceCalibrationMlKitV1":"faceCalibration";}
    static double[] normalize(FaceCalibration.Profile profile,double mouth,double[] raw) {
        double[] out=profile.normalize(mouth,raw);
        // A lift of 7% of eye-center spacing is full scale. Neutral comes from
        // this user's ML Kit capture; MediaPipe calibration is never reused.
        for(int i=5;i<8;i++)out[i]=Double.isFinite(raw[i])&&raw[i]>=0&&raw[i]<=1
                ?FaceCalibration.clamp((raw[i]-profile.baseline[i])/.07):Double.NaN;
        return out;
    }
}
