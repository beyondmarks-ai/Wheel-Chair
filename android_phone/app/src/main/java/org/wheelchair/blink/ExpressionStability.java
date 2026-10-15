package org.wheelchair.blink;

import java.util.Arrays;

/** Three-frame median plus separate entry/release thresholds. No eye filtering. */
final class ExpressionStability {
    private final double[][] history=new double[3][6];
    private final double[] filtered=new double[6];
    private int count, next;
    boolean ready, mouth, smile, brows, neutral;
    void reset(){count=next=0;ready=mouth=smile=brows=neutral=false;}
    void update(double smileLeft,double smileRight,double jaw,double inner,double outerLeft,double outerRight) {
        double[] values={smileLeft,smileRight,jaw,inner,outerLeft,outerRight};
        for(double value:values)if(!Double.isFinite(value)||value<0||value>1){reset();return;}
        history[next]=values;next=(next+1)%3;count=Math.min(3,count+1);ready=count==3;
        if(!ready)return;
        for(int i=0;i<6;i++){
            double[] column={history[0][i],history[1][i],history[2][i]};
            Arrays.sort(column);filtered[i]=column[1];
        }
        mouth=mouth ? filtered[2]>.45 : filtered[2]>=FaceMessages.MOUTH;
        smile=smile ? Math.min(filtered[0],filtered[1])>.40 : Math.min(filtered[0],filtered[1])>=FaceMessages.SMILE;
        brows=brows ? filtered[3]>.40&&filtered[4]>.25&&filtered[5]>.25 :
                filtered[3]>=FaceMessages.BROW_INNER&&filtered[4]>=FaceMessages.BROW_OUTER&&filtered[5]>=FaceMessages.BROW_OUTER;
        neutral=true;for(double value:filtered)if(value>=.30)neutral=false;
    }
    String signals(){
        if(!ready)return "Mouth / smile / eyebrows: stabilizing — keep your face in view";
        return "Mouth: "+(mouth?"open":"closed / opening")+" · Smile: "+(smile?"detected":"not detected")+
                " · Brows: "+(brows?"raised":"relaxed");
    }
}
