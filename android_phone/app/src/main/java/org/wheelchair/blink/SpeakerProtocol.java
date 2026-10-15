package org.wheelchair.blink;

/** One live command at a time; failed or disconnected commands are never replayed. */
final class SpeakerProtocol {
    private String pending;
    private long deadline;
    private int pendingTrack=1;
    private final java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    String status = "Speaker not connected";
    synchronized String begin(String id, long now) {
        return begin(id,now,1);
    }
    synchronized String begin(String id, long now, int track) {
        if(track!=1 && (track<3 || track>5) && (track<101 || track>2999)) return null;
        if(id==null || !id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) return null;
        if (seen.contains(id) || (pending != null && (track!=1 || pendingTrack==1))) return null;
        seen.add(id);
        if (seen.size() > 256) seen.remove(seen.iterator().next());
        pending = id; pendingTrack=track; deadline = now + (track>=101?30000:10000);
        status = "Sending speaker request";
        return (track==1 ? "SOS " : (track>=101?"REMIND ":"PHRASE ")+track+" ") + id + "\n";
    }
    synchronized void receive(String line) {
        if (pending == null) return;
        String prefix = "SPEAKER " + pending + " ";
        if (!line.startsWith(prefix)) return;
        String result = line.substring(prefix.length());
        switch (result) {
            case "CHECKING": status = "Controller received alert; checking speaker"; return;
            case "PLAY_REQUESTED": status = "Recording requested at maximum volume"; return;
            case "FINISHED": status = "Controller reports recording finished"; break;
            case "UNCONFIRMED": status = "Recording requested; completion not confirmed"; break;
            case "ERROR": status = "Speaker failed to respond. Check DFPlayer power and wires."; break;
            case "NOT_READY": status = "Facial recordings are not enabled on the controller"; break;
            case "BUSY": status = "Speaker busy; this alert was not played"; break;
            case "DUPLICATE": status = "Duplicate alert ignored by controller"; break;
            case "STOPPED": status = "Speaker stopped"; break;
            default: return;
        }
        pending = null;
    }
    synchronized boolean expire(long now) {
        if (pending == null || now < deadline) return false;
        pending = null;
        status = "Speaker reply timed out; playback unconfirmed";
        return true;
    }
    synchronized void disconnect() {
        status = pending == null ? "Speaker disconnected" : "Speaker disconnected; alert not confirmed";
        pending = null;
    }
    synchronized String status() { return status; }
    synchronized boolean busy() { return pending != null; }
    synchronized boolean emergencyBusy() { return pending != null && pendingTrack==1; }
}
