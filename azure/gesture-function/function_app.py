import json
import os
import threading
import time
import urllib.error
import urllib.request
from collections import deque

import azure.functions as func
from azure.identity import ManagedIdentityCredential
from gesture_protocol import payload, decision

app = func.FunctionApp(http_auth_level=func.AuthLevel.FUNCTION)
credential = ManagedIdentityCredential()
slots = threading.BoundedSemaphore(2)
lock = threading.Lock()
requests = deque()

def reply(body, status=200):
    return func.HttpResponse(json.dumps(body), status_code=status, mimetype="application/json",
        headers={"Cache-Control": "no-store"})

@app.route(route="gesture", methods=["POST"])
def gesture(req: func.HttpRequest) -> func.HttpResponse:
    # Keys stay in the function auth header, and frames/results are never logged or stored here.
    raw = req.get_body()
    if len(raw) > 310000:
        return reply({"error": "too_large"}, 413)
    try:
        request_body = payload(json.loads(raw))
    except (ValueError, TypeError):
        return reply({"error": "invalid_frames"}, 400)
    with lock:
        now = time.monotonic()
        while requests and requests[0] < now - 60:
            requests.popleft()
        if len(requests) >= 30:
            return reply({"error": "rate_limited"}, 429)
        requests.append(now)
    if not slots.acquire(blocking=False):
        return reply({"error": "busy"}, 429)
    try:
        started = time.monotonic()
        token = credential.get_token("https://cognitiveservices.azure.com/.default").token
        url = os.environ["FOUNDRY_ENDPOINT"].rstrip("/") + "/openai/deployments/gesture-gpt41mini/chat/completions?api-version=2024-10-21"
        request = urllib.request.Request(url, data=json.dumps(request_body).encode(), headers={
            "Authorization": "Bearer " + token, "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=8) as response:
            result = json.load(response)
        value = decision(json.loads(result["choices"][0]["message"]["content"]))
        value["inference_ms"] = round((time.monotonic() - started) * 1000)
        return reply(value)
    except urllib.error.HTTPError as error:
        return reply({"error": "rate_limited" if error.code == 429 else "model_unavailable"}, 503)
    except Exception:
        return reply({"error": "model_unavailable"}, 503)
    finally:
        slots.release()
