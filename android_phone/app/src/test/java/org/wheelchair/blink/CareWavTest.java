package org.wheelchair.blink;
import org.junit.Test;
import java.io.*;
import java.nio.*;
import static org.junit.Assert.*;

public class CareWavTest {
    private byte[] wav(int channels,int rate,int frames){ByteBuffer b=ByteBuffer.allocate(44+frames*channels*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes()).putInt(b.capacity()-8).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)channels).putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16).put("data".getBytes()).putInt(frames*channels*2);for(int i=0;i<frames;i++)for(int c=0;c<channels;c++)b.putShort((short)(c==0?1000:3000));return b.array();}
    @Test public void downmixAndResamplePreserveDuration()throws Exception{byte[] result=CareWav.normalize(wav(2,44100,44100));ByteBuffer b=ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);assertEquals(44144,result.length);assertEquals(1,b.getShort(22));assertEquals(22050,b.getInt(24));assertEquals(2000,b.getShort(44));}
    @Test public void monoVoicePreservesAmplitude()throws Exception{byte[] result=CareWav.normalize(wav(1,16000,16000));assertEquals(44144,result.length);assertEquals(1000,ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).getShort(100));}
    @Test(expected=IOException.class)public void tooLongMustBeShortened()throws Exception{CareWav.normalize(wav(1,16000,19*16000));}
    @Test(expected=IOException.class)public void rejectsTruncatedChunk()throws Exception{byte[] raw=wav(1,16000,100);ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putInt(40,999999);CareWav.normalize(raw);}
    @Test(expected=IOException.class)public void rejectsFloatFormat()throws Exception{byte[] raw=wav(1,16000,100);ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putShort(20,(short)3);CareWav.normalize(raw);}
}
