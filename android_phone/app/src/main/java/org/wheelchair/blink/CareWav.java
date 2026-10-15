package org.wheelchair.blink;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** Converts Android's PCM WAV speech to conservative DFPlayer mono 22.05 kHz PCM. */
final class CareWav {
    static byte[] normalize(byte[] source)throws IOException{
        if(source.length<44||source.length>4000000)throw new IOException("Unsupported voice recording size");
        ByteBuffer b=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
        if(!tag(source,0,"RIFF")||!tag(source,8,"WAVE"))throw new IOException("Speech engine did not produce WAV audio");
        int channels=0,rate=0,bits=0,format=0,start=-1,size=0;
        for(int pos=12;pos+8<=source.length;){
            int length=b.getInt(pos+4);if(length<0||length>source.length-pos-8)throw new IOException("Invalid WAV chunk");
            if(tag(source,pos,"fmt ")&&length>=16){format=b.getShort(pos+8)&65535;channels=b.getShort(pos+10)&65535;rate=b.getInt(pos+12);bits=b.getShort(pos+22)&65535;}
            if(tag(source,pos,"data")){start=pos+8;size=length;}
            pos+=8+length+(length&1);
        }
        if(format!=1||(channels!=1&&channels!=2)||bits!=16||rate<8000||rate>48000||start<0||size==0||size%(channels*2)!=0)throw new IOException("Speech engine must support 16-bit PCM WAV");
        int frames=size/(channels*2);if(frames>rate*18L)throw new IOException("Recording exceeds 18 seconds; shorten the reminder");
        int count=(int)((long)frames*22050/rate);ByteBuffer out=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+count*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(22050).putInt(44100).putShort((short)2).putShort((short)16).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(count*2);
        for(int n=0;n<count;n++){double at=(double)n*rate/22050;int left=(int)at,right=Math.min(left+1,frames-1);double a=sample(b,start,left,channels),z=sample(b,start,right,channels);out.putShort((short)Math.round(a+(z-a)*(at-left)));}
        return out.array();
    }
    private static double sample(ByteBuffer b,int start,int frame,int channels){int p=start+frame*channels*2;return channels==1?b.getShort(p):(b.getShort(p)+b.getShort(p+2))/2.0;}
    private static boolean tag(byte[] b,int at,String s){return new String(b,at,4,StandardCharsets.US_ASCII).equals(s);}
}
