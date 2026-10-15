package org.wheelchair.blink;

import android.app.*;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Explicit device verification: one labelled test reminder, no guardian recipient or SMS. */
@RunWith(AndroidJUnit4.class)
public class CareAlarmDeviceTest {
    @Test public void exactAlarmAnnouncesOnceOnPhone()throws Exception{
        verifyVoice("custom");
    }
    @Test public void exactAlarmPlaysNaturalVoiceWithoutMarkingMedicineTaken()throws Exception{
        verifyVoice("doctor");
    }
    @Test public void exactAlarmPlaysNewSchedulingVoice()throws Exception{
        verifyVoice("health_check");
    }
    private void verifyVoice(String voice)throws Exception{
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Allow Alarms & reminders first",CareScheduler.permitted(c));
        var store=CareStore.get(c);String title="TEST ONLY. Your scheduled reminder is working.";long started=System.currentTimeMillis();
        long plan=store.save(0,title,"Activity",0,0,127,"",false,10,false,voice),due=started+4000;
        String id=CareTime.occurrenceId(plan,due);
        try{
            store.next(plan,due);var p=store.getPlan(plan);
            var intent=CareScheduler.alarm(c,"DUE",""+plan,plan,p.revision(),due);
            c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(due,null),intent);
            CareStore.Occurrence o=null;
            long deadline=System.currentTimeMillis()+55000;
            while(System.currentTimeMillis()<deadline){o=store.getOccurrence(id);if(o!=null&&o.speaker().equals("Phone speech completed"))break;Thread.sleep(150);}
            assertNotNull("Exact alarm did not create a reminder",o);
            assertEquals("Phone speech completed",o.speaker());
            assertEquals("PENDING",o.state()); // Speech never falsely marks a task taken/done.
            assertFalse(o.guardian());
            CareReceiver.handle(c,new Intent().setAction("DUE").putExtra("id",""+plan).putExtra("plan",plan).putExtra("revision",p.revision()).putExtra("time",due));
            assertEquals("Phone speech completed",store.getOccurrence(id).speaker());
            assertNotNull(c.getSystemService(NotificationManager.class).getNotificationChannel("care_chime_v1").getSound());
        }finally{
            synchronized(CareScheduler.class){CareScheduler.cancelPlan(c,plan);store.getWritableDatabase().delete("occurrences","plan=?",new String[]{""+plan});store.getWritableDatabase().delete("plans","id=?",new String[]{""+plan});}
            HealthStore.get(c).getWritableDatabase().delete("activity","detail=? AND at>=?",new String[]{title,""+started});
        }
    }
    @Test public void installedSpeechEngineExportsCompatibleWav()throws Exception{
        var instrumentation=InstrumentationRegistry.getInstrumentation();Context c=instrumentation.getTargetContext();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<java.io.File> file=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();AtomicReference<CareRecording> recording=new AtomicReference<>();
        instrumentation.runOnMainSync(()->recording.set(new CareRecording(c,"Test only. Time for your activity.",(result,message)->{file.set(result);error.set(message);done.countDown();})));
        try{
            assertTrue("Voice export timeout",done.await(35,TimeUnit.SECONDS));assertNotNull(error.get(),file.get());
            byte[] bytes=java.nio.file.Files.readAllBytes(file.get().toPath());java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            assertEquals(22050,b.getInt(24));assertEquals(1,b.getShort(22));assertEquals(16,b.getShort(34));assertTrue(bytes.length>4410);
        }finally{instrumentation.runOnMainSync(()->recording.get().close());if(file.get()!=null)file.get().delete();}
    }
}
