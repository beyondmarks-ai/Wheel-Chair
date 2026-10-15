import unittest
from gestures import BlinkGesture


class GestureTests(unittest.TestCase):
    def setUp(self):
        self.gesture = BlinkGesture(mode="long_close")
        self.now = 0.0
        self.events = []

    def feed(self, seconds, left=0.1, right=0.1, valid=True):
        for _ in range(round(seconds * 20)):
            self.now += 0.05
            event = self.gesture.update(self.now, left, right, valid)
            if event:
                self.events.append(event)

    def test_long_close_needs_open_then_close_then_reopen(self):
        self.feed(1)
        self.feed(2.3, 0.9, 0.9)
        self.assertEqual(self.events, [])
        self.feed(0.5)
        self.assertEqual(len(self.events), 1)

    def test_normal_blinks_do_not_trigger(self):
        self.feed(1)
        for _ in range(5):
            self.feed(0.2, 0.9, 0.9)
            self.feed(1)
        self.assertEqual(self.events, [])

    def test_starting_closed_does_not_arm(self):
        self.feed(3, 0.9, 0.9)
        self.feed(1)
        self.assertEqual(self.events, [])

    def test_wink_is_not_both_eyes_closed(self):
        self.feed(1)
        self.feed(3, 0.9, 0.1)
        self.feed(1)
        self.assertEqual(self.events, [])

    def test_face_loss_cancels_pending_closure(self):
        self.feed(1)
        self.feed(1.5, 0.9, 0.9)
        self.feed(0.1, valid=False)
        self.feed(1, 0.9, 0.9)
        self.feed(1)
        self.assertEqual(self.events, [])

    def test_frame_gap_cancels_pending_closure(self):
        self.feed(1)
        self.feed(1.5, 0.9, 0.9)
        self.now += 2
        self.feed(1, 0.9, 0.9)
        self.feed(1)
        self.assertEqual(self.events, [])

    def test_prolonged_closure_is_not_emergency_intent(self):
        self.feed(1)
        self.feed(9, 0.9, 0.9)
        self.feed(1)
        self.assertEqual(self.events, [])

    def test_cooldown_prevents_repeated_events(self):
        for _ in range(2):
            self.feed(1)
            self.feed(2.3, 0.9, 0.9)
            self.feed(0.5)
        self.assertEqual(len(self.events), 1)

    def test_triple_blink(self):
        self.gesture = BlinkGesture()
        self.feed(1)
        for _ in range(3):
            self.feed(0.2, 0.9, 0.9)
            self.feed(0.4)
        self.feed(0.4)
        self.assertEqual(len(self.events), 1)

    def test_face_loss_clears_partial_triple_blink(self):
        self.gesture = BlinkGesture()
        self.feed(1)
        for _ in range(2):
            self.feed(0.2, 0.9, 0.9)
            self.feed(0.4)
        self.feed(0.1, valid=False)
        self.feed(1)
        self.feed(0.2, 0.9, 0.9)
        self.feed(0.5)
        self.assertEqual(self.events, [])

    def test_spaced_blinks_do_not_form_triple(self):
        self.gesture = BlinkGesture(mode="triple_blink")
        self.feed(1)
        for _ in range(3):
            self.feed(0.2, 0.9, 0.9)
            self.feed(2.2)
        self.assertEqual(self.events, [])

    def test_short_score_transitions_allow_deliberate_gesture(self):
        self.feed(1)
        self.feed(0.1, 0.4, 0.4)
        self.feed(2.3, 0.9, 0.9)
        self.feed(0.1, 0.4, 0.4)
        self.feed(0.5)
        self.assertEqual(len(self.events), 1)

    def test_uncertain_eye_scores_cancel_gesture(self):
        self.feed(1)
        self.feed(1.5, 0.9, 0.9)
        self.feed(0.4, 0.4, 0.4)
        self.feed(1, 0.9, 0.9)
        self.feed(1)
        self.assertEqual(self.events, [])

    def test_user_observed_closure_scores_can_form_triple(self):
        self.gesture = BlinkGesture()
        self.feed(1, 0.06, 0.02)
        for _ in range(3):
            self.feed(0.2, 0.60, 0.61)
            self.feed(0.4, 0.06, 0.02)
        self.feed(0.4, 0.06, 0.02)
        self.assertEqual(len(self.events), 1)


    def test_comfortable_pauses_allow_triple(self):
        self.gesture = BlinkGesture()
        self.feed(1)
        for _ in range(3):
            self.feed(0.3, 0.6, 0.6)
            self.feed(1.3)
        self.assertEqual(len(self.events), 1)

    def test_single_frame_spikes_do_not_count(self):
        self.gesture = BlinkGesture()
        self.feed(1)
        for _ in range(3):
            self.feed(0.05, 0.8, 0.8)
            self.feed(0.4)
        self.assertEqual(self.gesture.completed_blinks, 0)
        self.assertEqual(self.events, [])

    def test_slow_deliberate_blinks_allow_triple(self):
        self.gesture = BlinkGesture()
        self.feed(1)
        for _ in range(3):
            self.feed(0.95, 0.6, 0.6)
            self.feed(0.4)
        self.assertEqual(len(self.events), 1)

    def test_sustained_closure_is_not_counted_as_short_tail(self):
        self.gesture = BlinkGesture()
        self.feed(1)
        self.feed(2, 0.6, 0.6)
        self.feed(0.5)
        self.assertEqual(self.gesture.completed_blinks, 0)
        self.assertEqual(self.events, [])


if __name__ == "__main__":
    unittest.main()
