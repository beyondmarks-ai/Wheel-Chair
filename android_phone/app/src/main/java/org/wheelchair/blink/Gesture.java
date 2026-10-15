package org.wheelchair.blink;

import java.util.ArrayList;
import java.util.List;

/** Same triple-blink timings as the desktop prototype, in milliseconds. */
public final class Gesture {
    public String state = "WAIT_OPEN", feedback = "Look at the camera with eyes open";
    public final List<Long> blinks = new ArrayList<>();
    public int completed = 0;
    public long closedDuration = 0;
    private long previous = -1, since = -1, sequenceStart = -1, cooldown = 0;
    private long uncertain = -1, reopen = -1;
    private int closedFrames = 0, openFrames = 0;
    private void clearCurrent() { uncertain = reopen = -1; closedFrames = openFrames = 0; }
    public void reset(String reason) {
        state = "WAIT_OPEN"; since = sequenceStart = -1; blinks.clear(); clearCurrent(); feedback = reason;
    }
    private void cancel(String reason) { state = "READY"; since = -1; clearCurrent(); feedback = reason; }
    public boolean update(long now, double left, double right, boolean valid) {
        boolean gap = previous >= 0 && (now <= previous || now - previous > 600);
        previous = now;
        if (!valid || !Double.isFinite(left) || !Double.isFinite(right) || left < 0 || left > 1 || right < 0 || right > 1 || gap) {
            reset("Tracking interrupted. Look at the camera again."); return false;
        }
        if (now < cooldown) { state = "COOLDOWN"; feedback = "Three blinks detected. Ready again shortly."; return false; }
        if (state.equals("COOLDOWN")) reset("Look at the camera with eyes open");
        if (!blinks.isEmpty() && ((sequenceStart >= 0 && now - sequenceStart > 5000)
                || (state.equals("READY") && now - blinks.get(blinks.size() - 1) > 2000))) {
            blinks.clear(); sequenceStart = -1; feedback = "Sequence timed out. Start three blinks again.";
        }
        boolean open = left <= .35 && right <= .35, closed = left >= .50 && right >= .50;
        boolean wink = (left >= .50 && right <= .35) || (right >= .50 && left <= .35);
        switch (state) {
            case "WAIT_OPEN":
                if (!open) since = -1;
                else if (since < 0) since = now;
                else if (now - since >= 400) { state = "READY"; since = -1; feedback = "Ready. Blink both eyes three times."; }
                break;
            case "READY":
                if (closed) { clearCurrent(); state = "CLOSED"; since = now; closedFrames = 1; }
                break;
            case "CLOSED":
                long duration = (reopen >= 0 ? reopen : now) - since;
                if (wink) cancel("Both eyes need to blink together.");
                else if (duration > 1200) reset("Closure too long. Reopen, then blink again.");
                else if (open) {
                    if (reopen < 0) reopen = now;
                    openFrames++;
                    if (now - reopen < 60 || openFrames < 2) break;
                    closedDuration = reopen - since;
                    if (closedDuration < 60 || closedFrames < 2) { cancel("Very brief change ignored. Blink again."); break; }
                    if (blinks.isEmpty()) sequenceStart = since;
                    blinks.add(now); completed++; feedback = "Blink " + blinks.size() + " of 3 counted";
                    clearCurrent();
                    // Each counted blink already includes >=60 ms and two frames
                    // of reopening. Do not display 3/3 then cancel it in another
                    // confirmation phase: counting the third blink emits the event.
                    if (blinks.size() == 3) {
                        cooldown = now + 3000; state = "COOLDOWN";
                        feedback = "Three blinks detected. Check speaker status.";
                        return true;
                    }
                    state = "READY"; since = -1;
                } else if (closed) { closedFrames++; reopen = uncertain = -1; openFrames = 0; }
                else {
                    reopen = -1; openFrames = 0;
                    if (uncertain < 0) uncertain = now;
                    else if (now - uncertain > 300) cancel("Eye position unclear. Blink again.");
                }
                break;
        }
        return false;
    }
}
