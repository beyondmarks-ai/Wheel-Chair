import unittest
from gestures import BlinkGesture
from speaker import SpeakerLink


class FakeSerial:
    def __init__(self):
        self.sent = []
        self.received = bytearray()
        self.fail = False
        self.closed = False

    @property
    def in_waiting(self):
        return len(self.received)

    def read(self, count):
        result = bytes(self.received[:count])
        del self.received[:count]
        return result

    def write(self, value):
        if self.fail:
            raise OSError("Disconnected")
        self.sent.append(value)
        return len(value)

    def close(self):
        self.closed = True


class SpeakerTests(unittest.TestCase):
    def setUp(self):
        self.now = 0
        self.serial = FakeSerial()
        self.link = SpeakerLink("FAKE", connection=self.serial, clock=lambda: self.now)

    def test_three_blinks_send_one_command(self):
        gesture = BlinkGesture(mode="triple_blink")
        def feed(count, score):
            for _ in range(count):
                self.now += .05
                event = gesture.update(self.now, score, score, True)
                if event:
                    self.link.trigger("camera-event")
        feed(20, .05)
        for _ in range(2):
            feed(5, .7); feed(10, .05)
        self.assertEqual(self.serial.sent, [])
        feed(5, .7); feed(10, .05)
        self.assertEqual(self.serial.sent, [b"AUDIO TEST 30\n"])

    def test_no_audio_on_connect_or_idle_close(self):
        self.link.poll(); self.link.close()
        self.assertEqual(self.serial.sent, [])

    def test_duplicate_and_busy_commands_are_not_replayed(self):
        self.assertTrue(self.link.trigger("one"))
        self.assertFalse(self.link.trigger("one"))
        self.assertFalse(self.link.trigger("two"))
        self.serial.received.extend(b"AUDIO stopped after 5s; playback completion not confirmed\n")
        self.link.poll()
        self.assertFalse(self.link.trigger("two"))
        self.assertTrue(self.link.trigger("three"))
        self.assertEqual(len(self.serial.sent), 2)

    def test_query_reply_is_not_playback(self):
        self.link.trigger("one")
        self.serial.received.extend(b"AUDIO reply command=0x43 value=30\n")
        self.link.poll()
        self.assertEqual(self.link.playback_requests, 0)
        self.serial.received.extend(b"AUDIO playback requested: /mp3/0001.mp3 volume=30/30\n")
        self.link.poll()
        self.assertEqual(self.link.playback_requests, 1)

    def test_missing_module_reply_ends_pending_command(self):
        self.link.trigger("one")
        self.serial.received.extend(b"AUDIO no expected DFPlayer reply; playback not started\n")
        self.link.poll()
        self.assertFalse(self.link.active)
        self.assertEqual(self.link.playback_requests, 0)

    def test_serial_failure_is_not_retried(self):
        self.serial.fail = True
        self.assertFalse(self.link.trigger("one"))
        self.serial.fail = False
        self.assertFalse(self.link.trigger("two"))
        self.assertEqual(self.serial.sent, [])

    def test_close_stops_active_audio(self):
        self.link.trigger("one"); self.link.close(); self.link.close()
        self.assertEqual(self.serial.sent, [b"AUDIO TEST 30\n", b"AUDIO STOP\n"])

    def test_timeout_does_not_replay(self):
        self.link.trigger("one"); self.now = 11; self.link.poll()
        self.assertFalse(self.link.active)
        self.assertEqual(self.serial.sent, [b"AUDIO TEST 30\n"])


if __name__ == "__main__":
    unittest.main()
