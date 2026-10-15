package org.wheelchair.blink;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

final class CareStore extends SQLiteOpenHelper {
    private static CareStore instance;
    static synchronized CareStore get(Context c){if(instance==null)instance=new CareStore(c.getApplicationContext());return instance;}
    private CareStore(Context c){this(c,"care.db");}
    CareStore(Context c,String database){super(c,database,null,3);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE plans(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,kind TEXT NOT NULL,hour INTEGER,minute INTEGER,days INTEGER,once_date TEXT,enabled INTEGER,revision INTEGER,audio_ready INTEGER,grace INTEGER,guardian INTEGER,next_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE occurrences(id TEXT PRIMARY KEY,plan INTEGER,title TEXT,kind TEXT,due_at INTEGER,notify_at INTEGER,grace INTEGER,track INTEGER,audio_ready INTEGER,guardian INTEGER,state TEXT,action_at INTEGER,escalated INTEGER DEFAULT 0,speaker TEXT DEFAULT 'Not requested')");
        db.execSQL("CREATE INDEX occurrence_time ON occurrences(due_at)");
        addSounds(db);
        reserveSchedulingTracks(db);
    }
    private void addSounds(SQLiteDatabase db){
        db.execSQL("ALTER TABLE plans ADD COLUMN voice_key TEXT NOT NULL DEFAULT 'custom'");
        db.execSQL("ALTER TABLE occurrences ADD COLUMN voice_key TEXT NOT NULL DEFAULT 'custom'");
        // Reserve 2990..2999 for the shared pack. Old high-numbered custom plans
        // fall back to phone speech rather than playing a different recording.
        db.execSQL("UPDATE plans SET audio_ready=0 WHERE id>=2890");
        db.execSQL("UPDATE occurrences SET audio_ready=0 WHERE track>=2990");
    }
    private void reserveSchedulingTracks(SQLiteDatabase db){
        // Never play a newly reserved preset in place of an older custom recording.
        db.execSQL("UPDATE plans SET audio_ready=0 WHERE voice_key='custom' AND id>=2800");
        db.execSQL("UPDATE occurrences SET audio_ready=0 WHERE voice_key='custom' AND track>=2900");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){if(old<2)addSounds(db);if(old<3)reserveSchedulingTracks(db);}
    record Plan(long id,String title,String kind,int hour,int minute,int days,String once,boolean enabled,long revision,boolean audioReady,int grace,boolean guardian,long next,String voiceKey){
        int track(){var sound=CareSound.from(voiceKey);return sound==CareSound.CUSTOM?CareSound.customTrack(id):sound.track;}
    }
    record Occurrence(String id,long plan,String title,String kind,long due,long notifyAt,int grace,int track,boolean audioReady,boolean guardian,String state,long actionAt,boolean escalated,String speaker,String voiceKey){}
    private Plan plan(Cursor c){return new Plan(c.getLong(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4),c.getInt(5),c.getString(6),c.getInt(7)==1,c.getLong(8),c.getInt(9)==1,c.getInt(10),c.getInt(11)==1,c.getLong(12),c.getString(13));}
    private Occurrence occurrence(Cursor c){return new Occurrence(c.getString(0),c.getLong(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getInt(6),c.getInt(7),c.getInt(8)==1,c.getInt(9)==1,c.getString(10),c.getLong(11),c.getInt(12)==1,c.getString(13),c.getString(14));}
    synchronized long save(long id,String title,String kind,int hour,int minute,int days,String once,boolean ready,int grace,boolean guardian){
        return save(id,title,kind,hour,minute,days,once,ready,grace,guardian,"custom");
    }
    synchronized long save(long id,String title,String kind,int hour,int minute,int days,String once,boolean ready,int grace,boolean guardian,String voiceKey){
        if(!CareSound.from(voiceKey).key.equals(voiceKey))throw new IllegalArgumentException("Choose a reminder voice");
        if(title==null||title.trim().isEmpty()||title.length()>80||hour<0||hour>23||minute<0||minute>59||days<0||days>127||grace<5||grace>120)throw new IllegalArgumentException("Check title, time and grace period");
        if(!java.util.List.of("Medicine","Meal","Appointment","Activity").contains(kind))throw new IllegalArgumentException();
        if((once==null||once.isEmpty())&&days==0)throw new IllegalArgumentException("Select at least one day");
        if(once!=null&&!once.isEmpty())java.time.LocalDate.parse(once);
        Plan old=id>0?getPlan(id):null;
        ContentValues v=new ContentValues();v.put("title",title.trim());v.put("kind",kind);v.put("hour",hour);v.put("minute",minute);v.put("days",days);v.put("once_date",once);
        boolean matching=old==null||old.voiceKey.equals(voiceKey)&&(!voiceKey.equals("custom")||old.title.equals(title.trim()));
        v.put("enabled",1);v.put("revision",old==null?1:old.revision+1);v.put("audio_ready",ready&&matching?1:0);v.put("voice_key",voiceKey);
        v.put("grace",grace);v.put("guardian",guardian?1:0);v.put("next_at",0);
        if(old==null)return getWritableDatabase().insertOrThrow("plans",null,v);
        getWritableDatabase().update("plans",v,"id=?",new String[]{""+id});return id;
    }
    Plan getPlan(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM plans WHERE id=?",new String[]{""+id})){return c.moveToFirst()?plan(c):null;}}
    java.util.List<Plan> plans(){var out=new ArrayList<Plan>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM plans WHERE enabled=1 ORDER BY hour,minute",null)){while(c.moveToNext())out.add(plan(c));}return out;}
    void next(long id,long at){ContentValues v=new ContentValues();v.put("next_at",at);getWritableDatabase().update("plans",v,"id=?",new String[]{""+id});}
    void disable(long id){ContentValues v=new ContentValues();v.put("enabled",0);getWritableDatabase().update("plans",v,"id=?",new String[]{""+id});}
    synchronized Occurrence begin(Plan p,long due,long now){
        Plan current=getPlan(p.id);if(current==null||!current.enabled||current.revision!=p.revision||current.next!=due)return null;
        ContentValues v=new ContentValues();String id=CareTime.occurrenceId(p.id,due);
        v.put("id",id);v.put("plan",p.id);v.put("title",p.title);v.put("kind",p.kind);v.put("due_at",due);v.put("notify_at",due);v.put("grace",p.grace);v.put("track",p.track());
        v.put("voice_key",p.voiceKey);
        v.put("audio_ready",p.audioReady?1:0);v.put("guardian",p.guardian?1:0);v.put("state",CareTime.timely(now,due)?"PENDING":"DELAYED");v.put("action_at",now);
        if(getWritableDatabase().insertWithOnConflict("occurrences",null,v,SQLiteDatabase.CONFLICT_IGNORE)==-1)return null;
        return getOccurrence(id);
    }
    Occurrence getOccurrence(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM occurrences WHERE id=?",new String[]{id})){return c.moveToFirst()?occurrence(c):null;}}
    java.util.List<Occurrence> pending(){return query("state IN ('PENDING','SNOOZED')",new String[]{});}
    java.util.List<Occurrence> recent(long since){return query("due_at>=?",new String[]{""+since});}
    private java.util.List<Occurrence> query(String where,String[] args){var out=new ArrayList<Occurrence>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM occurrences WHERE "+where+" ORDER BY due_at DESC LIMIT 300",args)){while(c.moveToNext())out.add(occurrence(c));}return out;}
    synchronized boolean action(String id,String action,long now){
        if(!java.util.List.of("DONE","SKIPPED","SNOOZED","CANCELLED").contains(action))return false;
        ContentValues v=new ContentValues();v.put("state",action);v.put("action_at",now);
        if(action.equals("SNOOZED"))v.put("notify_at",now+600000);
        return getWritableDatabase().update("occurrences",v,"id=? AND state IN ('PENDING','SNOOZED')",new String[]{id})==1;
    }
    synchronized Occurrence resume(String id,long expected,long now){
        ContentValues v=new ContentValues();v.put("state",CareTime.timely(now,expected)?"PENDING":"DELAYED");v.put("action_at",now);
        if(getWritableDatabase().update("occurrences",v,"id=? AND state='SNOOZED' AND notify_at=?",new String[]{id,""+expected})!=1)return null;
        return getOccurrence(id);
    }
    synchronized boolean claimEscalation(String id,long expected,long now){
        Occurrence o=getOccurrence(id);
        if(o==null||!o.state.equals("PENDING")||o.notifyAt!=expected||now<o.notifyAt+o.grace*60000L)return false;
        ContentValues v=new ContentValues();v.put("escalated",1);
        return getWritableDatabase().update("occurrences",v,"id=? AND escalated=0 AND state='PENDING'",new String[]{id})==1;
    }
    void speaker(String id,String result){ContentValues v=new ContentValues();v.put("speaker",result);getWritableDatabase().update("occurrences",v,"id=?",new String[]{id});}
}
