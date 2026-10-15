package org.wheelchair.blink;

/** A cloud answer can confirm only the same gesture still visible in fresh local frames. */
final class FoundryGestureGate {
    enum Kind { NONE, MOUTH, BROWS }
    record Ticket(Kind kind,long generation,long captured) { }
    final double neutralMouth,neutralBrow;
    private Kind kind=Kind.NONE;
    private long generation,previous=-1,neutralSince=-1,candidateSince=-1,cooldownUntil;
    private boolean armed;
    FoundryGestureGate(double mouth,double brow){neutralMouth=mouth;neutralBrow=brow;}
    void cancel(){generation++;kind=Kind.NONE;armed=false;neutralSince=candidateSince=-1;}
    void observe(long now,boolean valid,double mouth,double brow){
        if(previous>=0&&(now<=previous||now-previous>500))cancel();
        previous=now;
        if(!valid||!Double.isFinite(mouth)||!Double.isFinite(brow)||now<cooldownUntil){cancel();return;}
        // Lowering brows is also neutral for an upward-only gesture.
        boolean isNeutral=mouth<neutralMouth+.018&&brow<neutralBrow+.015;
        if(!armed){
            if(!isNeutral)neutralSince=-1;
            else if(neutralSince<0)neutralSince=now;
            else if(now-neutralSince>=700)armed=true;
            return;
        }
        boolean mouthOpen=mouth>neutralMouth+(kind==Kind.MOUTH?.020:.035),
                browsUp=brow>neutralBrow+(kind==Kind.BROWS?.012:.020);
        Kind next=mouthOpen==browsUp?Kind.NONE:mouthOpen?Kind.MOUTH:Kind.BROWS;
        if(next!=kind){generation++;kind=next;candidateSince=now;}
    }
    Ticket request(long now){
        return armed&&kind!=Kind.NONE&&now-candidateSince>=250&&now-previous<=500?
                new Ticket(kind,generation,previous):null;
    }
    FaceMessages.Message accept(Ticket ticket,long now,String mouth,String eyebrows,boolean visible){
        if(ticket==null||!armed||!visible||ticket.generation!=generation||ticket.kind!=kind||
                now<ticket.captured||now-ticket.captured>4000||now-previous>500)return null;
        boolean open="open".equals(mouth),brows="raised".equals(eyebrows);
        // Ambiguous combined gestures, unknown features and stale frames never speak.
        FaceMessages.Message message=kind==Kind.MOUTH&&open&&"neutral".equals(eyebrows)?FaceMessages.Message.HELP:
                kind==Kind.BROWS&&brows&&"closed".equals(mouth)?FaceMessages.Message.COME:null;
        if(message!=null){cooldownUntil=now+5000;cancel();}
        return message;
    }
    String feedback(long now){
        if(now<cooldownUntil)return "Message sent. Relax your face.";
        if(!armed)return "Relax your face to get ready";
        return kind==Kind.NONE?"Ready: open your mouth or raise your eyebrows":
                kind==Kind.MOUTH?"Mouth opening seen; checking with Azure":"Eyebrow movement seen; checking with Azure";
    }
}
