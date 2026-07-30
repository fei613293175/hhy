#!/usr/bin/env python3
"""Verify the complete R05 Staging identity-sandbox journey without real identity data."""
from __future__ import annotations

import argparse
import base64
import json
import time
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit, urlunsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen

try:
    from check_r02_public_auth import challenge_answer, request_json
except ModuleNotFoundError:  # Imported as scripts.check_r05_staging_identity_sandbox.
    from scripts.check_r02_public_auth import challenge_answer, request_json


RETURN_URL = "https://h5.orbexa.cc/identity/callback"
ID_CHECKSUM = "10X98765432"
ID_WEIGHTS = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


def _require_data(status: int, payload: dict[str, object], label: str) -> dict[str, object]:
    data = payload.get("data")
    if status != 200 or payload.get("success") is not True or not isinstance(data, dict):
        raise RuntimeError(f"{label} did not return a success envelope")
    return data


def _request_html(url: str, timeout: int) -> str:
    try:
        with urlopen(Request(url, headers={"User-Agent": "hhy-r05-sandbox-gate/1"}), timeout=timeout) as response:
            if int(response.status) != 200:
                raise RuntimeError("sandbox liveness page did not return HTTP 200")
            return response.read().decode("utf-8")
    except (HTTPError, URLError, TimeoutError, UnicodeDecodeError) as exc:
        raise RuntimeError("sandbox liveness page was unavailable") from exc


def _post_form_no_redirect(url: str, values: dict[str, str], timeout: int) -> tuple[int, str | None]:
    request = Request(
        url,
        data=urlencode(values).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "User-Agent": "hhy-r05-sandbox-gate/1",
        },
    )
    opener = build_opener(_NoRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            return int(response.status), response.headers.get("Location")
    except HTTPError as exc:
        return int(exc.code), exc.headers.get("Location")
    except (URLError, TimeoutError) as exc:
        raise RuntimeError("sandbox completion request failed safely") from exc


def _validated_base(api_base_url: str, candidate: bool) -> str:
    base = api_base_url.rstrip("/")
    parsed = urlsplit(base)
    common_invalid = parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path
    if common_invalid:
        raise RuntimeError("sandbox gate API base URL is not an origin")
    if candidate:
        if parsed.scheme != "http" or parsed.hostname != "127.0.0.1" or parsed.port is None:
            raise RuntimeError("candidate mode only accepts an explicit http://127.0.0.1:<port> origin")
    elif parsed.scheme != "https" or parsed.hostname != "api.orbexa.cc" or parsed.port not in (None, 443):
        raise RuntimeError("public mode only accepts canonical https://api.orbexa.cc")
    return base


def _synthetic_id_number() -> str:
    """Create a checksum-valid, non-persisted test input with a randomized birth/sequence tuple."""
    seed = uuid.uuid4().int
    year = 1980 + seed % 21
    month = 1 + (seed // 21) % 12
    day = 1 + (seed // (21 * 12)) % 28
    sequence = (seed // (21 * 12 * 28)) % 1000
    first_seventeen = f"110105{year:04d}{month:02d}{day:02d}{sequence:03d}"
    checksum = ID_CHECKSUM[sum(int(value) * weight for value, weight in zip(first_seventeen, ID_WEIGHTS)) % 11]
    return first_seventeen + checksum


def verify(api_base_url: str, invite_code: str, timeout: int, candidate: bool) -> dict[str, object]:
    base = _validated_base(api_base_url, candidate)
    nonce = f"r05-sandbox-challenge-{uuid.uuid4()}"
    challenge_status, challenge = request_json(Request(
        f"{base}/api/v1/auth/security-challenges",
        data=json.dumps({
            "scene": "REGISTER",
            "clientNonce": nonce,
            "deviceFingerprint": "r05-sandbox-gate",
        }).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "hhy-r05-sandbox-gate/1",
            "X-Idempotency-Key": nonce,
        },
    ), timeout)
    challenge_data = _require_data(challenge_status, challenge, "registration challenge")
    challenge_id = challenge_data.get("challengeId")
    image_base64 = challenge_data.get("imageBase64")
    if not isinstance(challenge_id, str) or not isinstance(image_base64, str):
        raise RuntimeError("registration challenge was incomplete")
    answer = challenge_answer(base64.b64decode(image_base64, validate=True))

    phone = "194" + str(uuid.uuid4().int % 100_000_000).zfill(8)
    registration_status, registration = request_json(Request(
        f"{base}/api/v1/auth/register",
        data=json.dumps({
            "phone": phone,
            "password": "ValidPass99",
            "inviteCode": invite_code,
            "challengeId": challenge_id,
            "challengeProof": answer,
            "device": {
                "deviceFingerprint": "r05-sandbox-gate",
                "model": "release-gate",
                "platform": "ANDROID",
                "osVersion": "1",
                "appVersion": "r05-sandbox-gate",
            },
        }).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "hhy-r05-sandbox-gate/1",
            "X-Idempotency-Key": f"r05-sandbox-register-{uuid.uuid4()}",
        },
    ), timeout)
    registration_data = _require_data(registration_status, registration, "test registration")
    access_token = registration_data.get("accessToken")
    if not isinstance(access_token, str) or not access_token:
        raise RuntimeError("test registration did not return an access token")
    auth_headers = {
        "Authorization": f"Bearer {access_token}",
        "User-Agent": "hhy-r05-sandbox-gate/1",
    }

    initial_overview_status, initial_overview = request_json(Request(
        f"{base}/api/v1/identity/overview", headers=auth_headers,
    ), timeout)
    initial_overview_data = _require_data(initial_overview_status, initial_overview, "initial identity overview")
    if initial_overview_data.get("status") != "NOT_STARTED" or initial_overview_data.get("activeSession") is not None:
        raise RuntimeError("initial identity overview did not report NOT_STARTED")

    consent_status, consent = request_json(Request(
        f"{base}/api/v1/identity/consent", headers=auth_headers,
    ), timeout)
    consent_data = _require_data(consent_status, consent, "identity consent")
    consent_version = consent_data.get("consentVersion")
    if not isinstance(consent_version, str) or not consent_version:
        raise RuntimeError("identity consent version was missing")

    synthetic_id = _synthetic_id_number()
    create_status, created = request_json(Request(
        f"{base}/api/v1/identity/sessions",
        data=json.dumps({
            "realName": "测试用户",
            "idNumber": synthetic_id,
            "consentVersion": consent_version,
        }).encode("utf-8"),
        method="POST",
        headers={
            **auth_headers,
            "Content-Type": "application/json",
            "X-Idempotency-Key": f"r05-sandbox-create-{uuid.uuid4()}",
        },
    ), timeout)
    created_data = _require_data(create_status, created, "identity session creation")
    session_id = created_data.get("id")
    if not isinstance(session_id, str) or not session_id:
        raise RuntimeError("identity session ID was missing")

    active_overview_status, active_overview = request_json(Request(
        f"{base}/api/v1/identity/overview", headers=auth_headers,
    ), timeout)
    active_overview_data = _require_data(active_overview_status, active_overview, "active identity overview")
    active_session = active_overview_data.get("activeSession")
    if (
        active_overview_data.get("status") != "IN_PROGRESS"
        or not isinstance(active_session, dict)
        or active_session.get("id") != session_id
    ):
        raise RuntimeError("active identity overview did not restore the created session")

    liveness_status, liveness = request_json(Request(
        f"{base}/api/v1/identity/sessions/{session_id}/liveness-token",
        data=json.dumps({"returnUrl": RETURN_URL}).encode("utf-8"),
        method="POST",
        headers={
            **auth_headers,
            "Content-Type": "application/json",
            "X-Idempotency-Key": f"r05-sandbox-liveness-{uuid.uuid4()}",
        },
    ), timeout)
    liveness_data = _require_data(liveness_status, liveness, "liveness handoff")
    liveness_url = liveness_data.get("livenessUrl")
    if not isinstance(liveness_url, str):
        raise RuntimeError("liveness handoff URL was missing")
    parsed_liveness = urlsplit(liveness_url)
    if parsed_liveness.scheme != "https" or parsed_liveness.hostname != "api.orbexa.cc":
        raise RuntimeError("liveness handoff escaped the canonical HTTPS origin")
    request_origin = urlsplit(base) if candidate else parsed_liveness
    page_url = urlunsplit((
        request_origin.scheme,
        request_origin.netloc,
        parsed_liveness.path,
        parsed_liveness.query,
        "",
    ))
    page = _request_html(page_url, timeout)
    for forbidden in ("测试用户", synthetic_id, "requestId", "请求编号", "provider"):
        if forbidden.lower() in page.lower():
            raise RuntimeError("sandbox liveness page exposed technical or identity data")

    query = dict(item.split("=", 1) for item in parsed_liveness.query.split("&") if "=" in item)
    from urllib.parse import unquote_plus
    state = unquote_plus(query.get("state", ""))
    return_url = unquote_plus(query.get("returnUrl", ""))
    if not state or return_url != RETURN_URL:
        raise RuntimeError("sandbox liveness handoff parameters were incomplete")
    completion_url = f"{base}/public-api/v1/identity/sandbox/complete"
    completion_status, location = _post_form_no_redirect(completion_url, {
        "state": state,
        "returnUrl": return_url,
        "decision": "PASS",
    }, timeout)
    if completion_status != 303 or location != RETURN_URL:
        raise RuntimeError("sandbox completion did not return the allowed app callback")

    result_status, result = request_json(Request(
        f"{base}/api/v1/identity/sessions/{session_id}", headers=auth_headers,
    ), timeout)
    result_data = _require_data(result_status, result, "identity result")
    if result_data.get("status") != "VERIFIED":
        raise RuntimeError("sandbox completion did not reach VERIFIED")

    verified_overview_status, verified_overview = request_json(Request(
        f"{base}/api/v1/identity/overview", headers=auth_headers,
    ), timeout)
    verified_overview_data = _require_data(verified_overview_status, verified_overview, "verified identity overview")
    if verified_overview_data.get("status") != "VERIFIED" or verified_overview_data.get("activeSession") is not None:
        raise RuntimeError("verified identity overview did not report terminal VERIFIED state")

    replay_status, _ = _post_form_no_redirect(completion_url, {
        "state": state,
        "returnUrl": return_url,
        "decision": "PASS",
    }, timeout)
    if replay_status != 422:
        raise RuntimeError("sandbox one-time state accepted a replay")

    return {
        "status": "PASS",
        "api_base_url": base,
        "candidate_mode": candidate,
        "registration_http": registration_status,
        "initial_overview_status": initial_overview_data["status"],
        "identity_session_http": create_status,
        "active_overview_status": active_overview_data["status"],
        "liveness_page_http": 200,
        "completion_http": completion_status,
        "identity_status": result_data["status"],
        "verified_overview_status": verified_overview_data["status"],
        "replay_http": replay_status,
        "verified_at_epoch": int(time.time()),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-base-url", required=True)
    parser.add_argument("--invite-code", default="HHYTEST2026")
    parser.add_argument("--timeout", type=int, default=20)
    parser.add_argument("--candidate", action="store_true")
    args = parser.parse_args()
    try:
        result = verify(args.api_base_url, args.invite_code, args.timeout, args.candidate)
    except (RuntimeError, ValueError) as exc:
        print(f"R05_STAGING_SANDBOX_FAILED {exc}")
        return 1
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
