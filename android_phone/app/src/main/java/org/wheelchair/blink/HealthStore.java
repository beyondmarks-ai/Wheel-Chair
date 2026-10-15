package org.wheelchair.blink;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Local history. Raw camera/audio recordings are not stored. */
final class HealthStore extends SQLiteOpenHelper {
    String dailySummary(long start,long end){
        StringBuilder out=new StringBuilder("Health readings (prototype estimates):\n");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(bpm),avg(bpm),min(bpm),max(bpm),avg(oxygen) FROM samples WHERE at>=? AND at<=?",new String[]{""+start,""+end})){
            if(c.moveToFirst()&&c.getLong(0)>0)out.append(String.format(java.util.Locale.US,"BPM average %.0f, range %.0f–%.0f (%d samples).\n",c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getLong(0)));
            else out.append("No valid BPM samples today.\n");
            if(!c.isNull(4))out.append(String.format(java.util.Locale.US,"Average oxygen estimate %.1f%%.\n",c.getDouble(4)));
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT kind,count(*) FROM activity WHERE at>=? AND at<=? GROUP BY kind",new String[]{""+start,""+end})){while(c.moveToNext())out.append(c.getString(0)).append(": ").append(c.getLong(1)).append('\n');}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM alerts WHERE at>=? AND at<=? AND test=0",new String[]{""+start,""+end})){if(c.moveToFirst())out.append("Guardian alert attempts: ").append(c.getLong(0)).append(" (see Health & history for delivery).\n");}
        return out.toString();
    }
    private static HealthStore instance;
    static synchronized HealthStore get(Context context){
        if(instance==null)instance=new HealthStore(context.getApplicationContext());return instance;
    }
    private long savedAt=-1;
    private HealthStore(Context context){super(context,"health.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY,at INTEGER NOT NULL,bpm REAL,oxygen REAL,room REAL,humidity REAL,distance REAL,moving INTEGER,finger INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX samples_at ON samples(at)");
        db.execSQL("CREATE TABLE activity(id TEXT PRIMARY KEY,at INTEGER NOT NULL,kind TEXT NOT NULL,detail TEXT NOT NULL)");
        db.execSQL("CREATE TABLE alerts(id TEXT PRIMARY KEY,at INTEGER NOT NULL,status TEXT NOT NULL,test INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){ }
    synchronized void capture(HealthReading r,long elapsed,long wall){
        if(savedAt>=0&&elapsed>=savedAt&&elapsed-savedAt<5000)return;
        ContentValues row=new ContentValues();row.put("at",wall);
        put(row,"bpm",r.bpm());put(row,"oxygen",r.oxygen());put(row,"room",r.roomC());
        put(row,"humidity",r.humidity());put(row,"distance",r.distance());
        if(r.moving()==null)row.putNull("moving");else row.put("moving",r.moving()?1:0);
        row.put("finger",r.finger()?1:0);getWritableDatabase().insertOrThrow("samples",null,row);savedAt=elapsed;
    }
    private static void put(ContentValues row,String key,Double value){if(value==null)row.putNull(key);else row.put(key,value);}
    void event(String kind,String detail){
        ContentValues row=new ContentValues();row.put("id",UUID.randomUUID().toString());row.put("at",System.currentTimeMillis());
        row.put("kind",kind);row.put("detail",detail);getWritableDatabase().insertOrThrow("activity",null,row);
    }
    boolean beginAlert(String id,boolean test){
        ContentValues row=new ContentValues();row.put("id",id);row.put("at",System.currentTimeMillis());
        row.put("status","REQUESTED");row.put("test",test?1:0);
        return getWritableDatabase().insertWithOnConflict("alerts",null,row,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    synchronized void alertStatus(String id,String status){
        // Delivery can arrive before the sent callback. Never downgrade a final delivery.
        ContentValues row=new ContentValues();row.put("status",status);
        getWritableDatabase().update("alerts",row,"id=? AND status NOT IN ('DELIVERED','DELIVERY_FAILED')",new String[]{id});
    }
    record Point(long at,Double value) { }
    long firstReading(long fallback){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT MIN(at) FROM samples",null)){
            return c.moveToFirst()&&!c.isNull(0)?Math.min(c.getLong(0),fallback):fallback;
        }
    }
    java.util.List<Point> points(long since,int metric){
        String column=new String[]{"bpm","oxygen","room","humidity","distance"}[metric];
        java.util.List<Point> out=new ArrayList<>();
        // At most about 720 time buckets, keeping missing samples as gaps.
        long bucket=Math.max(5000,(System.currentTimeMillis()-since)/720);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT MIN(at),AVG("+column+"),COUNT("+column+"),COUNT(*) FROM samples WHERE at>=? GROUP BY at/? ORDER BY MIN(at)",new String[]{""+since,""+bucket})){
            while(c.moveToNext())out.add(new Point(c.getLong(0),c.isNull(1)||c.getInt(2)!=c.getInt(3)?null:c.getDouble(1)));
        }return out;
    }
    record Entry(long at,String text) { }
    java.util.List<Entry> history(){
        java.util.List<Entry> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT at,kind,detail FROM activity ORDER BY at DESC LIMIT 40",null)){
            while(c.moveToNext())out.add(new Entry(c.getLong(0),c.getString(1)+": "+c.getString(2)));
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT at,status,test FROM alerts ORDER BY at DESC LIMIT 40",null)){
            while(c.moveToNext())out.add(new Entry(c.getLong(0),(c.getInt(2)==1?"Guardian test SMS: ":"Guardian alert: ")+smsLabel(c.getString(1))));
        }return out;
    }
    static String smsLabel(String status){return switch(status){
        case "REQUESTED"->"requested; result not confirmed";
        case "SENT"->"sent to carrier; phone delivery not confirmed";
        case "DELIVERED"->"delivered to phone (not a read receipt)";
        case "DELIVERY_FAILED"->"carrier reported delivery failure";
        default->status;
    };}
    void exportCsv(OutputStream output)throws IOException{
        try(Writer w=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8));
            Cursor c=getReadableDatabase().rawQuery("SELECT at,bpm,oxygen,room,humidity,distance,moving,finger FROM samples ORDER BY at",null)){
            w.write("observed_at_utc,bpm,oxygen_estimate_percent,room_temperature_c,humidity_percent,obstacle_cm,moving,finger_present\n");
            while(c.moveToNext()){
                w.write(Instant.ofEpochMilli(c.getLong(0)).toString());
                for(int i=1;i<8;i++){w.write(',');if(!c.isNull(i))w.write(c.getString(i));}w.write('\n');
            }
        }
    }
}
