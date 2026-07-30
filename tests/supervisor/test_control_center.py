from __future__ import annotations

import json
import threading
import urllib.request
from pathlib import Path

from tools.supervisor.supervisor_control_center import (
    Handler,
    Server,
    ControlCenter,
    ROOT,
)


def request_json(url: str, *, method: str = "GET", payload: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        request.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode("utf-8"))


def test_control_center_status_is_read_only() -> None:
    center = ControlCenter(ROOT)
    before = center.status()
    after = center.status()
    assert before["schema"] == "hhy.supervisor-control-center-status/v5.0"
    assert after["governance"]["project"]["active_task"] == "TASK-R14-RECOVERY-002"
    assert "revision" in after["governance"]["project"]
    assert after["configuration"]["values"]["runtime"]["max_task_minutes"] == 30


def test_control_center_logs_are_friendly_and_config_rejects_unsafe_values() -> None:
    center = ControlCenter(ROOT)
    logs = center.logs("events")
    assert logs["status"] == "PASS"
    assert "items" in logs and "entries" not in logs
    chat = center.chat()
    assert chat["status"] == "PASS"
    assert chat["mode"] == "lifecycle-observer"
    assert chat["messages"]
    config_before = (ROOT / "tools/supervisor/supervisor_config.yaml").read_text(encoding="utf-8")
    result = center.update_configuration({"runtime": {"max_task_minutes": 999}})
    assert result["status"] == "FAIL"
    assert (ROOT / "tools/supervisor/supervisor_config.yaml").read_text(encoding="utf-8") == config_before


def test_control_center_http_routes_and_confirmation() -> None:
    class FakeCenter:
        def status(self):
            return {"schema": "fake-status", "repo": "test"}

        def logs(self, name, tail=200):
            return {"status": "PASS", "name": name, "tail": tail}

        def action(self, action, *, confirmed=False, authorization=None):
            return {"status": "CONFIRMATION_REQUIRED", "action": action} if not confirmed else {"status": "STARTED", "action": action}

    server = Server(("127.0.0.1", 0), FakeCenter())
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_port}"
    try:
        assert request_json(base + "/api/status")["schema"] == "fake-status"
        assert request_json(base + "/api/logs?name=events&tail=7")["tail"] == 7
        confirmation = request_json(base + "/api/action", method="POST", payload={"action": "start"})
        assert confirmation["status"] == "CONFIRMATION_REQUIRED"
        started = request_json(base + "/api/action", method="POST", payload={"action": "start", "confirmed": True})
        assert started["status"] == "STARTED"
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def test_control_center_is_loopback_only_by_default() -> None:
    source = (ROOT / "tools/supervisor/supervisor_control_center.py").read_text(encoding="utf-8")
    assert 'parser.add_argument("--bind", default="127.0.0.1"' in source
    assert "subprocess.Popen" in source
    assert "hhy_supervisor.py" in source
