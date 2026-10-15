package org.wheelchair.blink;

/** Deliberate communication gestures, not emotion or intention inference. */
final class FaceMessages {
    static final double SMILE=.55, MOUTH=.65, BROW_INNER=.55, BROW_OUTER=.35;
    private final ExpressionStability stability=new ExpressionStability();
    private final boolean quick;
    private final long maxSampleGap;
    FaceMessages(){this(false);}
    FaceMessages(boolean quick){this(quick,350);}
    FaceMessages(boolean quick,long maxSampleGap){this.quick=quick;this.maxSampleGap=maxSampleGap;}
    private String readyText(){return quick?"Ready: mouth 0.5 s, eyebrows 0.7 s, smile 1.5 s":"Ready: hold one gesture for 1.5 seconds";}
    String signals(){return stability.signals();}
    enum Message {
        YES(3, "Smile", "Yes"), HELP(4, "Open mouth", "Please help me"),
        COME(5, "Raise eyebrows", "Please come here");
        final int track; final String gesture, phrase;
        Message(int track, String gesture, String phrase) { this.track=track; this.gesture=gesture; this.phrase=phrase; }
    }
    String feedback = "Relax your face to get ready";
    private long previous=-1, neutralSince=-1, holdSince=-1, cooldownUntil=0;
    private boolean armed;
    private Message candidate;
    void reset(String reason) {
        stability.reset();clearGesture(reason);
    }
    private void clearGesture(String reason) {
        armed=false; candidate=null; neutralSince=holdSince=-1; feedback=reason;
    }
    Message update(long now, boolean valid, boolean available, double leftEye, double rightEye,
                   double smileLeft, double smileRight, double jaw, double innerBrow,
                   double outerLeft, double outerRight) {
        boolean gap=previous>=0 && (now<=previous || now-previous>maxSampleGap); previous=now;
        double[] values={leftEye,rightEye,smileLeft,smileRight,jaw,innerBrow,outerLeft,outerRight};
        for(double v:values) if(!Double.isFinite(v)||v<0||v>1) valid=false;
        if(!valid || gap) { reset("Show one steady face"); return null; }
        stability.update(smileLeft,smileRight,jaw,innerBrow,outerLeft,outerRight);
        if(!available) {clearGesture("Facial messages paused");return null;}
        if(!stability.ready) {clearGesture("Stabilizing facial movement");return null;}
        if(now<cooldownUntil) { clearGesture("Gesture detected. Relax your face and wait."); return null; }
        if(leftEye>.35 || rightEye>.35) { clearGesture("Open both eyes; blinks take priority"); return null; }
        boolean neutral=stability.neutral;
        if(!armed) {
            if(!neutral) neutralSince=-1;
            else if(neutralSince<0) neutralSince=now;
            else if(now-neutralSince>=700) { armed=true; feedback=readyText(); }
            return null;
        }
        boolean smile=stability.smile;
        boolean mouth=stability.mouth;
        boolean brow=stability.brows;
        int count=(smile?1:0)+(mouth?1:0)+(brow?1:0);
        if(count!=1) {
            candidate=null; holdSince=-1;
            feedback=count>1 ? "Use one gesture at a time" : readyText();
            return null;
        }
        Message next=smile?Message.YES:mouth?Message.HELP:Message.COME;
        if(next!=candidate) { candidate=next; holdSince=now; }
        long held=now-holdSince;
        long hold=quick?(next==Message.HELP?500:next==Message.COME?700:1500):1500;
        feedback=next.gesture+": "+Math.min(100,held*100/hold)+"% — "+next.phrase;
        if(held<hold) return null;
        cooldownUntil=now+5000;
        clearGesture("Gesture detected: "+next.phrase);
        return next;
    }
}
