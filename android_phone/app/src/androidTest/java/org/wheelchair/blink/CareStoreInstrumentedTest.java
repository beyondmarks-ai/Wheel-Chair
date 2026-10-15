package org.wheelchair.blink;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Isolated SQLite database: no alarms, speaker commands, notifications or SMS. */
@RunWith(AndroidJUnit4.class)
public class CareStoreInstrumentedTest {
    private Context context;private CareStore store;private static final String DATABASE="care-validation-only.db";
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase(DATABASE);store=new CareStore(context,DATABASE);}
    @After public void cleanup(){store.close();context.deleteDatabase(DATABASE);}
    private CareStore.Plan plan(){long id=store.save(0,"Validation only","Activity",8,15,127,"",false,10,true);store.next(id,1000);return store.getPlan(id);}
    @Test public void duplicateAlarmAndDuplicateActionAreIgnored(){var p=plan();var o=store.begin(p,1000,1000);assertNotNull(o);assertNull(store.begin(p,1000,1000));assertTrue(store.action(o.id(),"DONE",1100));assertFalse(store.action(o.id(),"SKIPPED",1200));assertFalse(store.claimEscalation(o.id(),1000,601000));assertEquals("DONE",store.getOccurrence(o.id()).state());}
    @Test public void editInvalidatesOldAlarmAndRecording(){var p=plan();store.save(p.id(),"Changed title","Activity",9,15,127,"",true,10,true);assertNull(store.begin(p,1000,1000));assertFalse(store.getPlan(p.id()).audioReady());assertTrue(store.getPlan(p.id()).revision()>p.revision());}
    @Test public void disabledPlanCannotFire(){var p=plan();store.disable(p.id());assertNull(store.begin(p,1000,1000));assertTrue(store.plans().isEmpty());}
    @Test public void lateAlarmIsHistoryOnly(){var p=plan();var o=store.begin(p,1000,121001);assertEquals("DELAYED",o.state());assertTrue(store.pending().isEmpty());assertFalse(store.action(o.id(),"DONE",122000));}
    @Test public void snoozeInvalidatesOldDeadline(){var p=plan();var o=store.begin(p,1000,1000);assertTrue(store.action(o.id(),"SNOOZED",2000));assertFalse(store.claimEscalation(o.id(),1000,601000));assertNull(store.resume(o.id(),1000,602000));assertNotNull(store.resume(o.id(),602000,602000));assertNull(store.resume(o.id(),602000,602000));assertFalse(store.claimEscalation(o.id(),602000,1201999));assertTrue(store.claimEscalation(o.id(),602000,1202000));assertFalse(store.claimEscalation(o.id(),602000,1202001));}
    @Test public void tracksAreNeverReusedAfterDeletion(){var first=plan();store.disable(first.id());var second=plan();assertNotEquals(first.track(),second.track());assertEquals(101,first.track());}
    @Test public void preparedSameTitleKeepsRecordingButChangeClearsIt(){var p=plan();store.save(p.id(),p.title(),"Activity",9,15,127,"",true,10,true);assertTrue(store.getPlan(p.id()).audioReady());store.save(p.id(),"New words","Activity",9,15,127,"",true,10,true);assertFalse(store.getPlan(p.id()).audioReady());}
    @Test public void presetVoiceIsSnapshottedAndChangingVoiceClearsSdReadiness(){
        long id=store.save(0,"Morning care","Medicine",8,15,127,"",true,10,false,"tablet");
        store.next(id,1000);var p=store.getPlan(id);assertEquals(2990,p.track());assertTrue(p.audioReady());
        var occurrence=store.begin(p,1000,1000);assertEquals("tablet",occurrence.voiceKey());assertEquals(2990,occurrence.track());
        store.save(id,"Evening care","Medicine",20,15,127,"",true,10,false,"tablet");assertTrue(store.getPlan(id).audioReady());
        store.save(id,"Evening care","Medicine",20,15,127,"",true,10,false,"medicine");assertFalse(store.getPlan(id).audioReady());
        assertEquals("tablet",store.getOccurrence(occurrence.id()).voiceKey());
    }
    @Test public void versionOneUpgradePreservesHistoryAndDisablesCollidingOldTracks(){
        store.close();context.deleteDatabase(DATABASE);
        try(var db=context.openOrCreateDatabase(DATABASE,0,null)){
            db.execSQL("CREATE TABLE plans(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,kind TEXT NOT NULL,hour INTEGER,minute INTEGER,days INTEGER,once_date TEXT,enabled INTEGER,revision INTEGER,audio_ready INTEGER,grace INTEGER,guardian INTEGER,next_at INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE occurrences(id TEXT PRIMARY KEY,plan INTEGER,title TEXT,kind TEXT,due_at INTEGER,notify_at INTEGER,grace INTEGER,track INTEGER,audio_ready INTEGER,guardian INTEGER,state TEXT,action_at INTEGER,escalated INTEGER DEFAULT 0,speaker TEXT DEFAULT 'Not requested')");
            db.execSQL("INSERT INTO plans VALUES(1,'Saved medicine','Medicine',8,0,127,'',1,1,1,10,0,1000)");
            db.execSQL("INSERT INTO plans VALUES(2890,'Old high track','Activity',9,0,127,'',1,1,1,10,0,2000)");
            db.execSQL("INSERT INTO occurrences VALUES('old-history',1,'Saved medicine','Medicine',1000,1000,10,101,1,0,'DONE',2000,0,'Phone speech completed')");
            db.setVersion(1);
        }
        store=new CareStore(context,DATABASE);
        assertEquals("custom",store.getPlan(1).voiceKey());assertTrue(store.getPlan(1).audioReady());
        assertEquals(101,store.getPlan(1).track());assertEquals("DONE",store.getOccurrence("old-history").state());
        assertFalse(store.getPlan(2890).audioReady());assertEquals(0,store.getPlan(2890).track());
    }
    @Test public void versionTwoUpgradePreservesPresetReadinessAndReservesExpandedTracks(){
        long preset=store.save(0,"Medicine","Medicine",8,0,127,"",true,10,false,"medicine");
        var db=store.getWritableDatabase();
        db.execSQL("INSERT INTO plans VALUES(2800,'Old custom','Activity',9,0,127,'',1,1,1,10,0,1000,'custom')");
        db.execSQL("INSERT INTO occurrences(id,plan,title,kind,due_at,notify_at,grace,track,audio_ready,guardian,state,action_at,voice_key) VALUES('legacy-custom',2800,'Old custom','Activity',1000,1000,10,2900,1,0,'PENDING',1000,'custom')");
        db.setVersion(2);store.close();store=new CareStore(context,DATABASE);
        assertTrue(store.getPlan(preset).audioReady());assertEquals(2991,store.getPlan(preset).track());
        assertFalse(store.getPlan(2800).audioReady());assertEquals(0,store.getPlan(2800).track());
        assertFalse(store.getOccurrence("legacy-custom").audioReady());assertEquals("Old custom",store.getOccurrence("legacy-custom").title());
        long next=store.save(0,"Position change","Activity",10,0,127,"",true,10,false,"position_change");
        store.next(next,2000);var o=store.begin(store.getPlan(next),2000,2000);
        assertEquals(2904,o.track());assertEquals("position_change",o.voiceKey());assertTrue(o.audioReady());
    }
}
