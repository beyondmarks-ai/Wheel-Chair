package org.wheelchair.blink;

import android.Manifest;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** The worker owns all Bluetooth IO. No cloud dependency and no alert retry queue. */
final class BluetoothSpeaker implements AutoCloseable {
    enum TriggerResult { REQUESTED, NOT_CONNECTED, BUSY, NOT_READY }
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private final Context context;
    private final String address;
    private final SpeakerProtocol protocol = new SpeakerProtocol();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean active = true, ready;
    private volatile boolean faceReady,careReady;
    private volatile BluetoothSocket socket;
    private String outgoing;
    private long outgoingAt;
    private long lastHealthUptime=-1;
    private long lastMotionEvents=-1;
    private final ConcurrentHashMap<String,String> careRequests=new ConcurrentHashMap<>();
    synchronized TriggerResult reminder(String occurrence,int track,long notifyAt){
        String id=CareTime.playbackId(occurrence,notifyAt);careRequests.put(id,occurrence);
        TriggerResult result=trigger(id,track);if(result!=TriggerResult.REQUESTED)careRequests.remove(id);return result;
    }
    private void careReply(String message){
        String[] parts=message.split(" ",3);if(parts.length!=3)return;
        String occurrence=careRequests.get(parts[1]);if(occurrence==null)return;
        String result=parts[2];if(!java.util.List.of("CHECKING","PLAY_REQUESTED","FINISHED","UNCONFIRMED","ERROR","NOT_READY","BUSY","DUPLICATE","STOPPED").contains(result))return;
        CareStore.get(context).speaker(occurrence,"Wired speaker: "+result);
        if(!result.equals("CHECKING")&&!result.equals("PLAY_REQUESTED"))careRequests.remove(parts[1]);
    }

    BluetoothSpeaker(Context context, String address) {
        this.context = context.getApplicationContext(); this.address = address;
        new Thread(this::run, "wheelchair-speaker").start();
    }
    static boolean permitted(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    synchronized TriggerResult trigger(String eventId) {
        return trigger(eventId,1);
    }
    synchronized TriggerResult trigger(String eventId, int track) {
        if (!active || !ready) {
            MonitorState.speaker = "Speaker not connected; this alert was not sent";
            return TriggerResult.NOT_CONNECTED;
        }
        if(track>=101 && !careReady)return TriggerResult.NOT_READY;
        if(track>=3 && track<=5 && !faceReady) {
            MonitorState.speaker="Facial messages need the updated controller and installed recordings";
            return TriggerResult.NOT_READY;
        }
        String command = protocol.begin(eventId, SystemClock.elapsedRealtime(),track);
        if (command == null) {
            MonitorState.speaker = "Speaker busy; wait a few seconds before another alert";
            return TriggerResult.BUSY;
        }
        outgoing = command; outgoingAt = SystemClock.elapsedRealtime();
        MonitorState.speaker = protocol.status();
        return TriggerResult.REQUESTED;
    }
    boolean busy() { return protocol.busy(); }
    boolean emergencyBusy() { return protocol.emergencyBusy(); }
    boolean faceReady() { return ready && faceReady; }
    boolean ready() { return active && ready; }
    boolean careReady() { return ready && careReady; }
    private void run() {
        HealthState.latest=null;HealthState.connected=false;
        HealthState.status="Waiting for controller sensor data";
        if (address.isEmpty()) { MonitorState.speaker = "Select the paired Wheelchair-Speaker before starting"; return; }
        while (active) {
            BluetoothSocket current = null;
            ScheduledFuture<?> timeout = null;
            try {
                if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    throw new IOException("Allow Nearby devices for the speaker");
                BluetoothManager manager = context.getSystemService(BluetoothManager.class);
                BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
                if (adapter == null || !adapter.isEnabled()) throw new IOException("Turn on Bluetooth for the speaker");
                BluetoothDevice device = adapter.getRemoteDevice(address);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) throw new IOException("Pair Wheelchair-Speaker in Bluetooth settings");
                MonitorState.speaker = "Connecting to Wheelchair-Speaker";
                current = device.createRfcommSocketToServiceRecord(SPP);
                synchronized (this) { if (!active) { current.close(); break; } socket = current; }
                BluetoothSocket connecting = current;
                timeout = timer.schedule(() -> closeSocket(connecting), 7, TimeUnit.SECONDS);
                current.connect();
                InputStream input = current.getInputStream(); OutputStream output = current.getOutputStream();
                output.write("HELLO\nFACE HELLO\nCARE HELLO\nHEALTH?\n".getBytes(StandardCharsets.US_ASCII));
                StringBuilder line = new StringBuilder(); boolean dropping = false;
                long lastReply = SystemClock.elapsedRealtime(), pingAt = lastReply;
                long connectedAt=lastReply;lastHealthUptime=-1;lastMotionEvents=-1;
                HealthState.latest=null;
                while (active && socket == current) {
                    for (int n = 0; n < 4096 && input.available() > 0; n++) {
                        int b = input.read(); if (b < 0) throw new EOFException();
                        if (b == '\r') continue;
                        if (b == '\n') {
                            if (!dropping) {
                                String message = line.toString();
                                if (message.equals("WHEELCHAIR_SPEAKER 1")) {
                                    lastReply = SystemClock.elapsedRealtime();
                                    if (!ready) {
                                        timeout.cancel(false); ready = true;
                                        MonitorState.speaker = "Speaker connected and ready.";
                                    }
                                } else if (ready && message.startsWith("HEALTH ")) {
                                    HealthReading reading=HealthReading.parse(message);
                                    if(reading!=null&&reading.uptime()!=lastHealthUptime){
                                        boolean restarted=lastHealthUptime>reading.uptime();
                                        lastHealthUptime=reading.uptime();
                                        long received=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
                                        HealthState.latest=new HealthState.Live(reading,received,wall);
                                        HealthState.connected=true;HealthState.status="Live sensor readings";
                                        try{
                                            HealthStore.get(context).capture(reading,received,wall);
                                        }
                                        catch(Exception storage){HealthState.status="Live readings; history could not be saved";}
                                        if(!restarted&&lastMotionEvents>=0&&reading.motionEvents()>lastMotionEvents){
                                            try{HealthStore.get(context).event("Movement",(reading.motionEvents()-lastMotionEvents)+" movement event(s) detected by controller");}catch(Exception ignored){}
                                            // A disabled phone notification must not prevent guardian delivery.
                                            try{CareAlerts.movement(context);}catch(Exception ignored){MonitorState.notification="Movement notification failed; check Alerts & sounds";}
                                            try{GuardianSms.motion(context);}catch(Exception ignored){MonitorState.guardian="Movement SMS failed; check Guardian alerts";}
                                        }
                                        lastMotionEvents=reading.motionEvents();
                                    }else if(reading==null){
                                        HealthState.connected=false;HealthState.status="Controller returned invalid sensor data";
                                    }
                                } else if (ready && message.equals("CARE_MESSAGES 1 READY")) { careReady=true;
                                } else if (ready && message.startsWith("FACE_MESSAGES 1 ")) {
                                    faceReady=message.equals("FACE_MESSAGES 1 READY");
                                    MonitorState.faceSetup=faceReady ? "Facial recordings enabled on controller" : "Copy the three recordings and enable them on the controller";
                                } else if (ready) {
                                    protocol.receive(message); MonitorState.speaker = protocol.status();
                                    if(message.startsWith("SPEAKER "))try{careReply(message);}catch(Exception ignored){}
                                }
                            }
                            line.setLength(0); dropping = false;
                        } else if (line.length() < 160) line.append((char)b);
                        else dropping = true;
                    }
                    synchronized (this) {
                        if (outgoing != null) {
                            if (SystemClock.elapsedRealtime() - outgoingAt > 1000) throw new IOException("Speaker request expired");
                            output.write(outgoing.getBytes(StandardCharsets.US_ASCII)); outgoing = null;
                        }
                    }
                    if (protocol.expire(SystemClock.elapsedRealtime())) {
                        MonitorState.speaker = protocol.status();
                        throw new IOException("Speaker reply timed out; playback unconfirmed");
                    }
                    long now = SystemClock.elapsedRealtime();
                    if(ready&&HealthState.latest==null&&now-connectedAt>7000)
                        HealthState.status="Sensor data unavailable: install the health controller firmware";
                    if (now - lastReply > 6000) throw new IOException("Speaker connection lost; reconnecting");
                    if (ready && now - pingAt > 2000) {
                        output.write((protocol.busy()?"HELLO\nFACE HELLO\nCARE HELLO\n":"HELLO\nFACE HELLO\nCARE HELLO\nHEALTH?\n").getBytes(StandardCharsets.US_ASCII)); pingAt = now;
                    }
                    // available() alone cannot detect a closed RFCOMM socket reliably.
                    if (!current.isConnected()) throw new EOFException();
                    SystemClock.sleep(30);
                }
            } catch (Exception error) {
                if (active) MonitorState.speaker = error.getMessage() == null ? "Speaker disconnected; reconnecting" : error.getMessage();
            } finally {
                for(String occurrence:careRequests.values())try{CareStore.get(context).speaker(occurrence,"Wired speaker disconnected; playback not confirmed");}catch(Exception ignored){}
                careRequests.clear();
                if (timeout != null) timeout.cancel(false);
                synchronized (this) { ready = faceReady = careReady = false; outgoing = null; socket = null; protocol.disconnect(); }
                MonitorState.faceSetup="Connect the updated controller to use facial messages";
                HealthState.connected=false;HealthState.status="Controller disconnected; showing saved history";
                closeSocket(current);
            }
            if (active) SystemClock.sleep(2000);
        }
    }
    private static void closeSocket(BluetoothSocket socket) { if (socket != null) try { socket.close(); } catch (IOException ignored) { } }
    @Override public void close() {
        BluetoothSocket current;
        synchronized (this) { active = false; ready = faceReady = careReady = false; outgoing = null; current = socket; socket = null; protocol.disconnect(); }
        // Closing the connection also cancels this phone's active playback in firmware.
        closeSocket(current); timer.shutdownNow(); MonitorState.speaker = "Speaker disconnected";
        HealthState.connected=false;HealthState.status="Not monitoring; showing saved history";
    }
}
