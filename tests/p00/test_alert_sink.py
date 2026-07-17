#!/usr/bin/env python3
"""Functional tests for the isolated P00 Alertmanager receipt sink."""
from __future__ import annotations

from http.server import ThreadingHTTPServer
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
from threading import Thread
from urllib.request import Request, urlopen
import json
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "infra/staging/alert-sink/alert_sink.py"


class AlertSinkTest(unittest.TestCase):
    def test_health_and_minimized_delivery_receipt(self) -> None:
        spec = spec_from_file_location("hhy_p00_alert_sink", MODULE_PATH)
        assert spec and spec.loader
        module = module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory(prefix="hhy-p00-alert-sink-") as temp:
            module.DELIVERIES = Path(temp) / "deliveries.jsonl"
            server = ThreadingHTTPServer(("127.0.0.1", 0), module.Handler)
            thread = Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            try:
                with urlopen(f"{base}/health", timeout=3) as response:
                    self.assertEqual(response.status, 200)
                payload = {
                    "status": "firing",
                    "groupKey": "{}:{alertname=\"HhyBackendDown\"}",
                    "alerts": [{
                        "status": "firing",
                        "labels": {
                            "alertname": "HhyBackendDown",
                            "release": "P00",
                            "severity": "critical",
                            "credential_probe": "must-not-be-persisted",
                        },
                    }],
                }
                body = json.dumps(payload).encode("utf-8")
                request = Request(
                    f"{base}/alerts", data=body, method="POST",
                    headers={"Content-Type": "application/json", "Authorization": "Bearer secret"},
                )
                with urlopen(request, timeout=3) as response:
                    self.assertEqual(response.status, 202)
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=3)

            raw = module.DELIVERIES.read_text(encoding="utf-8")
            receipt = json.loads(raw)
            self.assertEqual(receipt["group_status"], "firing")
            self.assertEqual(receipt["alerts"], [{
                "alertname": "HhyBackendDown",
                "release": "P00",
                "severity": "critical",
                "status": "firing",
            }])
            self.assertEqual(len(receipt["body_sha256"]), 64)
            self.assertNotIn("must-not-be-persisted", raw)
            self.assertNotIn("Bearer secret", raw)


if __name__ == "__main__":
    unittest.main()
