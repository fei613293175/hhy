#!/usr/bin/env python3
"""Persist privacy-minimized Alertmanager delivery receipts for P00 staging."""
from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json


DELIVERIES = Path("/data/deliveries.jsonl")
MAX_BODY_BYTES = 1024 * 1024


class Handler(BaseHTTPRequestHandler):
    server_version = "hhy-p00-alert-sink/1"

    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/health":
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status":"UP"}\n')

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/alerts":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_error(400, "invalid content length")
            return
        if length <= 0 or length > MAX_BODY_BYTES:
            self.send_error(413, "invalid body size")
            return
        body = self.rfile.read(length)
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            self.send_error(400, "invalid json")
            return
        alerts = payload.get("alerts") if isinstance(payload, dict) else None
        if not isinstance(alerts, list):
            self.send_error(400, "alerts must be a list")
            return
        receipt = {
            "received_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "body_sha256": sha256(body).hexdigest(),
            "group_status": payload.get("status"),
            "group_key": payload.get("groupKey"),
            "alerts": [
                {
                    "alertname": (item.get("labels") or {}).get("alertname"),
                    "release": (item.get("labels") or {}).get("release"),
                    "severity": (item.get("labels") or {}).get("severity"),
                    "status": item.get("status"),
                }
                for item in alerts
                if isinstance(item, dict)
            ],
        }
        with DELIVERIES.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(receipt, ensure_ascii=False, separators=(",", ":")) + "\n")
        print(json.dumps(receipt, ensure_ascii=False, separators=(",", ":")), flush=True)
        self.send_response(202)
        self.end_headers()

    def log_message(self, format: str, *args: object) -> None:
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
