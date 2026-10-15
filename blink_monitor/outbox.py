"""Persist test events until Azure acknowledges them; never store camera frames."""
import argparse
import asyncio
from datetime import datetime, timezone
import json
from pathlib import Path
import sqlite3
import uuid

ROOT = Path(__file__).resolve().parent
DATABASE = ROOT / "local" / "events.sqlite3"
CREDENTIAL = ROOT / "local" / "connection-string.txt"


def database():
    DATABASE.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DATABASE)
    connection.execute("CREATE TABLE IF NOT EXISTS events (id TEXT PRIMARY KEY, payload TEXT, sent INTEGER DEFAULT 0)")
    return connection


def enqueue(kind, details=None):
    event = {"eventId": str(uuid.uuid4()), "eventType": kind, "testMode": True,
             "deviceId": "wheelchair-blink-gateway-01", "cameraId": "esp32-cam-mb",
             "observedAt": datetime.now(timezone.utc).isoformat(), **(details or {})}
    with database() as connection:
        connection.execute("INSERT INTO events (id,payload) VALUES (?,?)",
                           (event["eventId"], json.dumps(event)))
    return event


def pending():
    with database() as connection:
        return connection.execute("SELECT id,payload FROM events WHERE sent=0 ORDER BY rowid").fetchall()


async def flush():
    from azure.iot.device.aio import IoTHubDeviceClient
    from azure.iot.device import Message
    events = pending()
    if not events:
        print("No pending Azure events", flush=True)
        return
    client = IoTHubDeviceClient.create_from_connection_string(
        CREDENTIAL.read_text().strip(), websockets=True, connection_retry=False)
    try:
        await asyncio.wait_for(client.connect(), 15)
        for event_id, payload in events:
            message = Message(payload)
            message.message_id = event_id
            message.content_type = "application/json"
            message.content_encoding = "utf-8"
            await asyncio.wait_for(client.send_message(message), 10)
            with database() as connection:
                connection.execute("UPDATE events SET sent=1 WHERE id=?", (event_id,))
            print("Azure acknowledged event:", event_id, flush=True)
    finally:
        await asyncio.wait_for(client.shutdown(), 5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--connection-test", action="store_true")
    args = parser.parse_args()
    if args.connection_test:
        print(json.dumps(enqueue("connectivity_test", {"source": "manual_connection_test"})))
    try:
        asyncio.run(flush())
    except Exception as error:
        print("Azure upload incomplete; unsent events remain queued:", type(error).__name__)
        raise SystemExit(1)
