#!/usr/bin/env python3
"""Verify the anonymous R02 auth endpoints required by the Android APK."""
from __future__ import annotations

import argparse
import json
import ssl
import sys
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def request_json(request: Request, timeout: int) -> tuple[int, dict[str, object]]:
    try:
        with urlopen(request, timeout=timeout, context=ssl.create_default_context()) as response:
            status = int(response.status)
            payload = json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        raise RuntimeError(f"{request.method} {request.full_url} returned HTTP {exc.code}") from exc
    except (URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"{request.method} {request.full_url} failed safely") from exc
    if not isinstance(payload, dict):
        raise RuntimeError(f"{request.method} {request.full_url} returned a non-object body")
    return status, payload


def verify(api_base_url: str, timeout: int) -> dict[str, object]:
    base = api_base_url.rstrip("/")
    parsed = urlsplit(base)
    if parsed.scheme != "https" or parsed.hostname != "api.orbexa.cc" or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise RuntimeError("R02 public auth gate only accepts canonical https://api.orbexa.cc")

    registration_status, registration = request_json(
        Request(f"{base}/api/v1/auth/registration-config", headers={"User-Agent": "hhy-r02-public-gate/1"}),
        timeout,
    )
    nonce = f"r02-public-gate-{uuid.uuid4()}"
    challenge_body = json.dumps({
        "scene": "LOGIN",
        "clientNonce": nonce,
        "deviceFingerprint": "release-gate",
    }).encode("utf-8")
    challenge_status, challenge = request_json(
        Request(
            f"{base}/api/v1/auth/security-challenges",
            data=challenge_body,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "User-Agent": "hhy-r02-public-gate/1",
                "X-Idempotency-Key": nonce,
            },
        ),
        timeout,
    )
    if registration_status != 200 or registration.get("success") is not True:
        raise RuntimeError("registration-config did not return the frozen success envelope")
    challenge_data = challenge.get("data")
    if challenge_status != 200 or challenge.get("success") is not True or not isinstance(challenge_data, dict) or not challenge_data.get("challengeId"):
        raise RuntimeError("security-challenges did not return a usable frozen success envelope")
    return {
        "status": "PASS",
        "api_base_url": base,
        "registration_config_http": registration_status,
        "security_challenge_http": challenge_status,
        "registration_request_id": registration.get("requestId"),
        "challenge_request_id": challenge.get("requestId"),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", required=True)
    parser.add_argument("--timeout", type=int, default=20)
    args = parser.parse_args()
    try:
        result = verify(args.api_base_url, args.timeout)
    except RuntimeError as exc:
        print(f"R02_PUBLIC_AUTH_FAILED {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
