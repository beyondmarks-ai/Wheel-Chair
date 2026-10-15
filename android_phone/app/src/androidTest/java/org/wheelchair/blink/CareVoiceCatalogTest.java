package org.wheelchair.blink;
import android.media.MediaMetadataRetriever;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CareVoiceCatalogTest {
    @Test public void everySelectableNaturalVoiceIsPackagedAndDecodable()throws Exception{
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();int count=0;
        for(var sound:CareSound.values())if(sound!=CareSound.CUSTOM){
            try(var resource=context.getResources().openRawResourceFd(sound.resource);var metadata=new MediaMetadataRetriever()){
                metadata.setDataSource(resource.getFileDescriptor(),resource.getStartOffset(),resource.getLength());
                long duration=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue(sound.key+" duration "+duration,duration>=200&&duration<10000);count++;
            }
        }
        assertEquals(15,count);
    }
}
