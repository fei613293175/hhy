#!/usr/bin/env python3
"""Validate a durable Android candidate request and expose CI outputs."""

from __future__ import annotations

from argparse import ArgumentParser
from pathlib import Path
import json
import re

import yaml


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REQUEST = ROOT / "config/android-candidate-request.yaml"
RELEASE_PATTERN = re.compile(r"^R(?:0[6-9]|[12][0-9]|3[0-2])$")
HISTORICAL_VISUAL_RELEASE = "HISTORICAL-UI"


class CandidateRequestError(ValueError):
    pass


def load_request(path: Path) -> dict[str, object]:
    payload = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(payload, dict):
        raise CandidateRequestError("candidate request must be a YAML object")
    if payload.get("schema_version") != 1:
        raise CandidateRequestError("schema_version must be 1")
    if payload.get("status") != "REQUESTED":
        raise CandidateRequestError("status must be REQUESTED")
    release = str(payload.get("release") or "")
    candidate = payload.get("candidate")
    if candidate is True:
        if not RELEASE_PATTERN.fullmatch(release):
            raise CandidateRequestError("candidate release must be R06 through R32")
    elif candidate is False:
        if release != HISTORICAL_VISUAL_RELEASE:
            raise CandidateRequestError("candidate=false is reserved for HISTORICAL-UI")
    else:
        raise CandidateRequestError("candidate must be a boolean")
    attempt = payload.get("remediation_attempt")
    if not isinstance(attempt, int) or isinstance(attempt, bool) or not 1 <= attempt <= 3:
        raise CandidateRequestError("remediation_attempt must be an integer from 1 through 3")
    request_id = str(payload.get("request_id") or "").strip()
    if not request_id or not re.fullmatch(r"[A-Z0-9][A-Z0-9._-]{5,79}", request_id):
        raise CandidateRequestError("request_id must be a stable 6-80 character identifier")
    return {
        "schema": "hhy.android-candidate-request/v1",
        "enabled": True,
        "release": release,
        "candidate": candidate,
        "remediation_attempt": attempt,
        "request_id": request_id,
        "reason": str(payload.get("reason") or "").strip(),
    }


def write_github_output(path: Path, payload: dict[str, object]) -> None:
    values = {
        "enabled": "true",
        "release": payload["release"],
        "candidate": str(payload["candidate"]).lower(),
        "remediation_attempt": payload["remediation_attempt"],
        "request_id": payload["request_id"],
    }
    with path.open("a", encoding="utf-8", newline="\n") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def main() -> int:
    parser = ArgumentParser()
    parser.add_argument("--request", default=str(DEFAULT_REQUEST))
    parser.add_argument("--github-output")
    parser.add_argument("--json-output")
    args = parser.parse_args()
    payload = load_request(Path(args.request))
    if args.github_output:
        write_github_output(Path(args.github_output), payload)
    if args.json_output:
        output = Path(args.json_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
