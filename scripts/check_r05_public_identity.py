#!/usr/bin/env python3
"""Verify the public R05 registration-to-identity-consent path."""
from __future__ import annotations

import argparse
import base64
import json
import time
import uuid
from urllib.parse import urlsplit
from urllib.request import Request

try:
    from check_r02_public_auth import challenge_answer, request_json
except ModuleNotFoundError:  # Imported as scripts.check_r05_public_identity in tests.
    from scripts.check_r02_public_auth import challenge_answer, request_json


def verify(api_base_url: str, invite_code: str, timeout: int) -> dict[str, object]:
    base = api_base_url.rstrip("/")
    parsed = urlsplit(base)
    if parsed.scheme != "https" or parsed.hostname != "api.orbexa.cc" \
            or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise RuntimeError("R05 public identity gate only accepts canonical https://api.orbexa.cc")

    nonce = f"r05-public-identity-{uuid.uuid4()}"
    challenge_status, challenge = request_json(Request(
        f"{base}/api/v1/auth/security-challenges",
        data=json.dumps({
            "scene": "REGISTER",
            "clientNonce": nonce,
            "deviceFingerprint": "r05-public-identity-gate",
        }).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "hhy-r05-public-identity-gate/1",
            "X-Idempotency-Key": nonce,
        },
    ), timeout)
    challenge_data = challenge.get("data")
    if challenge_status != 200 or challenge.get("success") is not True \
            or not isinstance(challenge_data, dict):
        raise RuntimeError("R05 registration challenge was unavailable")
    challenge_id = challenge_data.get("challengeId")
    image_base64 = challenge_data.get("imageBase64")
    if not isinstance(challenge_id, str) or not isinstance(image_base64, str):
        raise RuntimeError("R05 registration challenge was incomplete")
    answer = challenge_answer(base64.b64decode(image_base64, validate=True))

    phone = "194" + str(uuid.uuid4().int % 100_000_000).zfill(8)
    registration_key = f"r05-public-register-{uuid.uuid4()}"
    registration_status, registration = request_json(Request(
        f"{base}/api/v1/auth/register",
        data=json.dumps({
            "phone": phone,
            "password": "ValidPass99",
            "inviteCode": invite_code,
            "challengeId": challenge_id,
            "challengeProof": answer,
            "device": {
                "deviceFingerprint": "r05-public-identity-gate",
                "model": "release-gate",
                "platform": "ANDROID",
                "osVersion": "1",
                "appVersion": "r05-gate",
            },
        }).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "hhy-r05-public-identity-gate/1",
            "X-Idempotency-Key": registration_key,
        },
    ), timeout)
    registration_data = registration.get("data")
    if registration_status != 200 or registration.get("success") is not True \
            or not isinstance(registration_data, dict):
        raise RuntimeError("R05 public test registration did not succeed")
    access_token = registration_data.get("accessToken")
    if not isinstance(access_token, str) or not access_token:
        raise RuntimeError("R05 public test registration did not return an access token")

    consent_status, consent = request_json(Request(
        f"{base}/api/v1/identity/consent",
        headers={
            "Authorization": f"Bearer {access_token}",
            "User-Agent": "hhy-r05-public-identity-gate/1",
        },
    ), timeout)
    consent_data = consent.get("data")
    if consent_status != 200 or consent.get("success") is not True \
            or not isinstance(consent_data, dict):
        raise RuntimeError("R05 identity consent endpoint did not return a success envelope")
    if not isinstance(consent_data.get("consentVersion"), str) \
            or consent_data.get("title") != "实名认证授权说明" \
            or not isinstance(consent_data.get("content"), str) \
            or len(consent_data["content"].strip()) < 100:
        raise RuntimeError("R05 identity consent content was incomplete")

    return {
        "status": "PASS",
        "api_base_url": base,
        "registration_http": registration_status,
        "identity_consent_http": consent_status,
        "identity_consent_title": consent_data["title"],
        "identity_consent_content_length": len(consent_data["content"]),
        "verified_at_epoch": int(time.time()),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", required=True)
    parser.add_argument("--invite-code", default="HHYTEST2026")
    parser.add_argument("--timeout", type=int, default=20)
    args = parser.parse_args()
    try:
        result = verify(args.api_base_url, args.invite_code, args.timeout)
    except (RuntimeError, ValueError) as exc:
        print(f"R05_PUBLIC_IDENTITY_FAILED {exc}")
        return 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
