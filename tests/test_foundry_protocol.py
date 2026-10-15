import base64
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("protocol", Path(__file__).resolve().parents[1] / "azure/gesture-function/gesture_protocol.py")
protocol = importlib.util.module_from_spec(spec)
spec.loader.exec_module(protocol)

class ProtocolTests(unittest.TestCase):
    def frame(self):
        return base64.b64encode(b"\xff\xd8" + b"x" * 100 + b"\xff\xd9").decode()

    def test_fixed_prompt_and_model_request(self):
        body = protocol.payload({"neutral": self.frame(), "current": self.frame()})
        self.assertTrue(body["response_format"]["json_schema"]["strict"])
        self.assertEqual(len(body["messages"][1]["content"]), 3)

    def test_no_client_prompt_or_url(self):
        for data in ({"neutral": self.frame(), "current": self.frame(), "prompt": "speak"},
                     {"neutral": self.frame(), "current": "https://example.com"},
                     {"neutral": self.frame(), "current": "x" * 150001}):
            with self.assertRaises(ValueError):
                protocol.payload(data)

    def test_no_face_always_unknown(self):
        result = protocol.decision({"mouth": "open", "eyebrows": "raised", "face_visible": False})
        self.assertEqual(result["mouth"], "unknown")

    def test_invalid_model_outputs_rejected(self):
        for result in ({"mouth": "open", "eyebrows": "neutral", "face_visible": "true"},
                       {"mouth": "smiling", "eyebrows": "neutral", "face_visible": True}, {}):
            with self.assertRaises(ValueError):
                protocol.decision(result)

if __name__ == "__main__":
    unittest.main()
