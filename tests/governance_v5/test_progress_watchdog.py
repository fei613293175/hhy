from __future__ import annotations

import sys
from tools.governance.gov50.util import run_with_progress_timeout


def test_worker_idle_timeout_terminates_without_product_progress(tmp_path):
    result = run_with_progress_timeout(
        [sys.executable, "-c", "import time; time.sleep(5)"],
        tmp_path,
        timeout=10,
        idle_timeout=1,
    )

    assert result["status"] == "FAIL"
    assert result["exit_code"] == 124
    assert "IDLE_TIMEOUT" in result["stderr_tail"]


def test_runtime_only_writes_do_not_count_as_product_progress(tmp_path):
    runtime = tmp_path / "governance" / "runtime"
    runtime.mkdir(parents=True)
    runtime.joinpath("worker-result.json").write_text("{}", encoding="utf-8")
    result = run_with_progress_timeout(
        [sys.executable, "-c", "import time; time.sleep(5)"],
        tmp_path,
        timeout=10,
        idle_timeout=1,
    )

    assert result["status"] == "FAIL"
    assert "IDLE_TIMEOUT" in result["stderr_tail"]


def test_worker_startup_timeout_requires_first_product_change(tmp_path):
    result = run_with_progress_timeout(
        [sys.executable, "-c", "import time; time.sleep(5)"],
        tmp_path,
        timeout=10,
        startup_timeout=1,
        idle_timeout=5,
    )

    assert result["status"] == "FAIL"
    assert "STARTUP_IDLE_TIMEOUT" in result["stderr_tail"]


def test_worker_uses_idle_timeout_after_first_product_change(tmp_path):
    result = run_with_progress_timeout(
        [
            sys.executable,
            "-c",
            "import pathlib,time; time.sleep(.2); pathlib.Path('product.txt').write_text('ok'); time.sleep(12)",
        ],
        tmp_path,
        timeout=20,
        startup_timeout=6,
        idle_timeout=1,
    )

    assert result["status"] == "FAIL"
    assert "IDLE_TIMEOUT" in result["stderr_tail"]
    assert "STARTUP_IDLE_TIMEOUT" not in result["stderr_tail"]
