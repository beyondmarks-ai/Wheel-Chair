"""Eye gestures for supervised prototype tests, not emergency-intent inference."""
from dataclasses import dataclass
import math


@dataclass
class BlinkGesture:
    mode: str = "triple_blink"
    closed_threshold: float = 0.50
    open_threshold: float = 0.35
    hold_seconds: float = 2.0
    max_closed_seconds: float = 5.0
    max_frame_gap: float = 0.6
    min_blink_seconds: float = 0.06
    max_blink_seconds: float = 1.2
    max_pause_seconds: float = 2.0
    sequence_seconds: float = 5.0
    cooldown_seconds: float = 3.0

    def __post_init__(self):
        if self.mode not in ("long_close", "triple_blink"):
            raise ValueError("Unknown gesture")
        self.previous_time = None
        self.cooldown_until = 0.0
        self.completed_blinks = 0
        self.closed_duration = 0.0
        self.reset()

    def reset(self, reason="Look at camera with eyes open"):
        self.state = "WAIT_OPEN"
        self.since = None
        self.blinks = []
        self.sequence_start = None
        self.clear_current_blink()
        self.feedback = reason

    def clear_current_blink(self):
        self.uncertain_since = None
        self.reopen_since = None
        self.open_frames = 0
        self.closed_frames = 0

    def cancel_current_blink(self, reason):
        # Preserve confirmed blinks while the original sequence deadline applies.
        self.state, self.since = "READY", None
        self.clear_current_blink()
        self.feedback = reason

    def update(self, now, left=None, right=None, valid=True):
        gap = self.previous_time is not None and (now <= self.previous_time or now - self.previous_time > self.max_frame_gap)
        self.previous_time = now
        if not valid or left is None or right is None or not all(math.isfinite(x) and 0 <= x <= 1 for x in (left, right)) or gap:
            self.reset("Tracking interrupted; start again")
            return None
        if now < self.cooldown_until:
            self.state = "COOLDOWN"
            self.feedback = f"Test saved. Ready again in {self.cooldown_until - now:.1f}s"
            return None
        if self.state == "COOLDOWN":
            self.reset()
        if self.blinks and ((self.sequence_start is not None and now - self.sequence_start > self.sequence_seconds)
                            or (self.state == "READY" and now - self.blinks[-1] > self.max_pause_seconds)):
            self.blinks.clear()
            self.sequence_start = None
            self.feedback = "Sequence timed out; start three blinks again"

        is_open = left <= self.open_threshold and right <= self.open_threshold
        is_closed = left >= self.closed_threshold and right >= self.closed_threshold
        wink = (left >= self.closed_threshold and right <= self.open_threshold) or (right >= self.closed_threshold and left <= self.open_threshold)

        if self.state == "WAIT_OPEN":
            if not is_open:
                self.since = None
            elif self.since is None:
                self.since = now
            elif now - self.since >= 0.4:
                self.state, self.since = "READY", None
                self.feedback = "Ready: blink both eyes three times" if self.mode == "triple_blink" else "Ready for eye closure"
        elif self.state == "READY":
            if is_closed:
                self.clear_current_blink()
                self.state, self.since = "CLOSED", now
                self.closed_frames = 1
        elif self.state == "CLOSED":
            limit = self.max_blink_seconds if self.mode == "triple_blink" else self.max_closed_seconds
            duration = (self.reopen_since if self.reopen_since is not None else now) - self.since
            if wink:
                self.cancel_current_blink("Both eyes need to blink together")
            elif duration > limit:
                # Do not split one sustained closure into several short blinks.
                self.reset("Closure too long; reopen and blink again")
            elif is_open:
                if self.reopen_since is None:
                    self.reopen_since = now
                self.open_frames += 1
                if now - self.reopen_since < 0.06 or self.open_frames < 2:
                    return None
                self.closed_duration = self.reopen_since - self.since
                minimum = self.min_blink_seconds if self.mode == "triple_blink" else self.hold_seconds
                if self.closed_duration < minimum or self.closed_frames < 2:
                    self.cancel_current_blink("Very brief change ignored; blink again")
                    return None
                if self.mode == "triple_blink":
                    if not self.blinks:
                        self.sequence_start = self.since
                    self.blinks.append(now)
                    self.completed_blinks += 1
                    accepted = len(self.blinks) == 3
                    self.feedback = f"Blink {len(self.blinks)} of 3 counted"
                else:
                    accepted = True
                self.state = "CONFIRM_OPEN" if accepted else "READY"
                self.since = now if accepted else None
                self.clear_current_blink()
            elif is_closed:
                self.closed_frames += 1
                self.reopen_since, self.open_frames, self.uncertain_since = None, 0, None
            else:
                self.reopen_since, self.open_frames = None, 0
                if self.uncertain_since is None:
                    self.uncertain_since = now
                elif now - self.uncertain_since > 0.30:
                    self.cancel_current_blink("Eye position unclear; blink again")
        elif self.state == "CONFIRM_OPEN":
            if not is_open:
                self.reset("Keep eyes open briefly after the third blink")
            elif self.mode == "triple_blink" and len(self.blinks) != 3:
                self.reset("Sequence timed out; start again")
            elif now - self.since >= 0.15:
                self.cooldown_until = now + self.cooldown_seconds
                self.state = "COOLDOWN"
                self.feedback = "Three blinks confirmed - TEST saved" if self.mode == "triple_blink" else "Eye closure confirmed - TEST saved"
                return {"gesture": self.mode, "closureSeconds": round(self.closed_duration, 2),
                        "blinkCount": 3 if self.mode == "triple_blink" else 1}
        return None
