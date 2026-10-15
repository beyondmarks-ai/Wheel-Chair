package org.wheelchair.blink;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Network;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import org.json.JSONObject;

public final class EventStore extends SQLiteOpenHelper {
    private final Context context;
    public EventStore(Context context) { super(context, "events.db", null, 1); this.context = context.getApplicationContext(); }
    @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY,payload TEXT NOT NULL,sent INTEGER NOT NULL DEFAULT 0)"); }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    private File credentialFile() { return new File(context.getFilesDir(), "azure-device.json"); }
    public static JSONObject validateConfig(String data) throws Exception {
        JSONObject config = new JSONObject(data.replace("\uFEFF", ""));
        String host = config.getString("hostName"), device = config.getString("deviceId"), key = config.getString("sharedAccessKey");
        if (!host.matches("[a-zA-Z0-9-]+\\.azure-devices\\.net") || !device.matches("[a-zA-Z0-9_.-]{1,128}")
                || java.util.Base64.getDecoder().decode(key).length < 16) throw new IllegalArgumentException("Invalid device setup file");
        return config;
    }
    public void importConfig(String data) throws Exception {
        JSONObject config = validateConfig(data);
        Files.write(credentialFile().toPath(), config.toString().getBytes(StandardCharsets.UTF_8));
    }
    public boolean configured() { return credentialFile().isFile(); }
    public String add(String kind, long closureMs) throws Exception {
        String device = "phone-unconfigured";
        if (configured()) device = validateConfig(new String(Files.readAllBytes(credentialFile().toPath()), StandardCharsets.UTF_8)).getString("deviceId");
        JSONObject event = new JSONObject().put("eventId", UUID.randomUUID().toString()).put("eventType", kind)
                .put("testMode", true).put("deviceId", device).put("cameraId", "esp32-cam-mb")
                .put("observedAt", Instant.now().toString()).put("source", kind.equals("blink_sos_test") ? "android_camera_eye_landmarks" : "manual_connection_test");
        if (kind.equals("blink_sos_test")) event.put("gesture", "triple_blink").put("blinkCount", 3).put("closureSeconds", closureMs / 1000.0);
        ContentValues row = new ContentValues(); row.put("id", event.getString("eventId")); row.put("payload", event.toString());
        getWritableDatabase().insertOrThrow("events", null, row);
        return event.getString("eventId");
    }
    public int count(boolean unsentOnly) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM events" + (unsentOnly ? " WHERE sent=0" : ""), null)) {
            cursor.moveToFirst(); return cursor.getInt(0);
        }
    }
    public synchronized String upload(NetworkRoutes routes) {
        if (!configured()) return "Azure setup needed. Tests are saved on this phone.";
        int pending = count(true);
        if (pending == 0) return "Azure configured. No queued tests.";
        Network internet = routes.internet();
        if (internet == null) return pending + " queued. Waiting for internet or mobile data.";
        try {
            JSONObject config = validateConfig(new String(Files.readAllBytes(credentialFile().toPath()), StandardCharsets.UTF_8));
            String host = config.getString("hostName"), device = config.getString("deviceId");
            URL url = new URL("https://" + host + "/devices/" + AzureToken.encode(device) + "/messages/events?api-version=2021-04-12");
            String sas = AzureToken.make(host, device, config.getString("sharedAccessKey"), System.currentTimeMillis()/1000 + 3600);
            try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id,payload FROM events WHERE sent=0 ORDER BY rowid LIMIT 10", null)) {
                while (cursor.moveToNext()) {
                    HttpURLConnection connection = (HttpURLConnection) internet.openConnection(url);
                    try {
                        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                        connection.setInstanceFollowRedirects(false);
                        connection.setRequestMethod("POST"); connection.setDoOutput(true);
                        connection.setRequestProperty("Authorization", sas);
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        connection.setRequestProperty("iothub-contenttype", "application/json");
                        connection.setRequestProperty("iothub-contentencoding", "utf-8");
                        connection.setRequestProperty("iothub-messageid", cursor.getString(0));
                        byte[] body = cursor.getString(1).getBytes(StandardCharsets.UTF_8);
                        connection.setFixedLengthStreamingMode(body.length);
                        try (var output = connection.getOutputStream()) { output.write(body); }
                        int status = connection.getResponseCode();
                        if (status != 204) return "Azure returned HTTP " + status + ". Test remains queued.";
                        ContentValues row = new ContentValues(); row.put("sent", 1);
                        getWritableDatabase().update("events", row, "id=?", new String[]{cursor.getString(0)});
                    } finally { connection.disconnect(); }
                }
            }
            int left = count(true);
            return left == 0 ? "Azure received all queued tests." : left + " tests still queued.";
        } catch (Exception error) { return "Upload unavailable. Tests remain saved for retry."; }
    }
}
