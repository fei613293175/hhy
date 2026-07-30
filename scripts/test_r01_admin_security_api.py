#!/usr/bin/env python3
"""Exercise the frozen R01 administrator-security API workflow end to end."""

from __future__ import annotations

import base64
import concurrent.futures
import collections
import hashlib
import hmac
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid


BASE_URL = os.environ.get("HHY_R01_API_BASE_URL", "").rstrip("/")
USERNAME = os.environ.get("HHY_R01_ADMIN_USERNAME", "")
PASSWORD = os.environ.get("HHY_R01_ADMIN_PASSWORD", "")
NEW_PASSWORD = os.environ.get("HHY_R01_ADMIN_NEW_PASSWORD", "")
DATABASE_URL = os.environ.get("HHY_R01_DATABASE_URL", "")
DEVICE = "r01-api-smoke-" + uuid.uuid4().hex[:16]


def require_environment() -> None:
    missing = [
        name
        for name, value in (
            ("HHY_R01_API_BASE_URL", BASE_URL),
            ("HHY_R01_ADMIN_USERNAME", USERNAME),
            ("HHY_R01_ADMIN_PASSWORD", PASSWORD),
            ("HHY_R01_ADMIN_NEW_PASSWORD", NEW_PASSWORD),
            ("HHY_R01_DATABASE_URL", DATABASE_URL),
        )
        if not value
    ]
    if missing:
        raise SystemExit("Missing required environment: " + ", ".join(missing))
    if PASSWORD == NEW_PASSWORD:
        raise SystemExit("R01 smoke passwords must be different")
    if shutil.which("psql") is None:
        raise SystemExit("psql is required for isolated snapshot assertions")


def db_rows(sql: str) -> list[list[str]]:
    completed = subprocess.run(
        ["psql", DATABASE_URL, "-X", "-qAt", "-v", "ON_ERROR_STOP=1"],
        input=sql,
        text=True,
        capture_output=True,
        timeout=20,
        check=False,
    )
    if completed.returncode != 0:
        raise AssertionError("isolated PostgreSQL assertion failed")
    return [line.split("|") for line in completed.stdout.splitlines() if line]


def safe_sql_key(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9-]{16,128}", value):
        raise AssertionError("generated idempotency key is not SQL-assertion safe")
    return value


def assert_persisted_snapshots(keys: dict[str, str]) -> None:
    expected = {safe_sql_key(value) for value in keys.values()}
    literals = ",".join("'" + value + "'" for value in sorted(expected))
    rows = db_rows(f"""
        SELECT idem_key,scope,response_type,
               response_payload_ciphertext IS NOT NULL
                 AND btrim(response_payload_ciphertext)<>''
        FROM hhy.idempotency_records
        WHERE idem_key IN ({literals})
        ORDER BY idem_key;
    """)
    assert {row[0] for row in rows} == expected, "not all seven POST snapshots were persisted"
    expected_binding = {
        keys["login"]: ("admin.login:", "r01.admin-session.v1"),
        keys["verify"]: ("admin.mfa.verify:", "r01.admin-session.v1"),
        keys["logout"]: ("admin.logout:", "r01.command-result.v1"),
        keys["password_change"]: ("admin.password:", "r01.admin-self-security.v1"),
        keys["enroll"]: ("admin.mfa.enroll:", "r01.mfa-enrollment.v1"),
        keys["confirm"]: ("admin.mfa.confirm:", "r01.admin-self-security.v1"),
        keys["disable"]: ("admin.mfa.disable:", "r01.admin-self-security.v1"),
    }
    for row in rows:
        assert len(row) == 4 and row[3] == "t", "a POST snapshot has no ciphertext"
        prefix, response_type = expected_binding[row[0]]
        assert row[1].startswith(prefix) and row[2] == response_type, (
            "a POST snapshot has the wrong scope or response type"
        )


def idem(operation: str) -> str:
    return f"r01-{operation}-{uuid.uuid4()}"


def call(
    method: str,
    path: str,
    *,
    body: dict | None = None,
    token: str | None = None,
    mfa_ticket: str | None = None,
    idempotency_key: str | None = None,
) -> dict:
    headers = {
        "Accept": "application/json",
        "X-Request-Id": "r01-smoke-" + uuid.uuid4().hex,
        "X-Device-Fingerprint": DEVICE,
    }
    if token:
        headers["Authorization"] = "Bearer " + token
    if mfa_ticket:
        headers["X-MFA-Ticket"] = mfa_ticket
    if idempotency_key:
        headers["X-Idempotency-Key"] = idempotency_key
    payload = None
    if body is not None:
        payload = json.dumps(body, separators=(",", ":")).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(
        BASE_URL + path, data=payload, headers=headers, method=method
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status = response.status
            parsed = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise AssertionError(f"{method} {path} returned {error.code}: {detail}") from error
    if status != 200:
        raise AssertionError(f"{method} {path} returned unexpected status {status}")
    assert set(parsed) == {"success", "requestId", "timestamp", "data"}, parsed
    assert parsed["success"] is True
    assert parsed["requestId"].startswith("r01-smoke-")
    assert parsed["timestamp"]
    assert isinstance(parsed["data"], dict)
    return parsed["data"]


def expect_error(
    method: str,
    path: str,
    status: int,
    *,
    body: dict,
    token: str,
    idempotency_key: str,
) -> dict:
    request_id = "r01-smoke-" + uuid.uuid4().hex
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Authorization": "Bearer " + token,
        "X-Request-Id": request_id,
        "X-Device-Fingerprint": DEVICE,
        "X-Idempotency-Key": idempotency_key,
    }
    request = urllib.request.Request(
        BASE_URL + path,
        data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
        headers=headers,
        method=method,
    )
    try:
        urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        parsed = json.loads(error.read().decode("utf-8"))
        assert error.code == status, parsed
        assert parsed.get("success") is False and parsed.get("requestId") == request_id
        return parsed
    raise AssertionError(f"{method} {path} unexpectedly succeeded")


def totp(secret: str, at: int | None = None) -> str:
    counter = (int(time.time()) if at is None else at) // 30
    return totp_step(secret, counter)


def totp_step(secret: str, counter: int) -> str:
    key = base64.b32decode(secret, casefold=False)
    digest = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    binary = struct.unpack(">I", digest[offset : offset + 4])[0] & 0x7FFFFFFF
    return f"{binary % 1_000_000:06d}"


def wait_until_step(target: int) -> None:
    deadline = time.monotonic() + 35
    while int(time.time()) // 30 < target:
        if time.monotonic() >= deadline:
            raise AssertionError(f"timed out waiting for TOTP step {target}")
        time.sleep(0.2)


def stable_previous_step() -> int:
    while 30 - (time.time() % 30) < 15:
        time.sleep(0.2)
    return int(time.time()) // 30 - 1


def qr_secret(qr_url: str) -> str:
    marker = "secret="
    if marker not in qr_url:
        raise AssertionError("MFA enrollment response has no TOTP secret")
    return qr_url.split(marker, 1)[1].split("&", 1)[0]


def login(password: str, idempotency_key: str) -> dict:
    return call(
        "POST",
        "/admin-api/v1/auth/login",
        body={"username": USERNAME, "password": password},
        idempotency_key=idempotency_key,
    )


def verify_ticket(
    session: dict, secret: str, step: int, idempotency_key: str
) -> tuple[str, dict]:
    ticket = session.get("mfaTicket")
    assert session.get("mfaRequired") == "TOTP" and ticket
    body = {"mfaTicket": ticket, "code": totp_step(secret, step)}
    verified = call("POST", "/admin-api/v1/auth/mfa/verify", body=body,
                    mfa_ticket=ticket, idempotency_key=idempotency_key)
    replayed = call("POST", "/admin-api/v1/auth/mfa/verify", body=body,
                    mfa_ticket=ticket, idempotency_key=idempotency_key)
    assert replayed == verified, "MFA verify did not replay the exact first data response"
    access_token = verified.get("accessToken")
    assert access_token and verified.get("mfaRequired") == "NONE"
    return access_token, verified


def invalid_login(attempt: int, username: str) -> tuple[int, str | None]:
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "X-Request-Id": f"r01-pool-{uuid.uuid4().hex}",
        "X-Device-Fingerprint": "r01-pool-shared-device",
        "X-Idempotency-Key": f"r01-pool-{attempt}-{uuid.uuid4()}",
    }
    payload = json.dumps(
        {"username": username, "password": "Invalid!PoolPassword17"},
        separators=(",", ":"),
    ).encode("utf-8")
    request = urllib.request.Request(
        BASE_URL + "/admin-api/v1/auth/login",
        data=payload,
        headers=headers,
        method="POST",
    )
    try:
        urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        error.read()
        return error.code, error.headers.get("Retry-After")
    raise AssertionError("invalid concurrent login unexpectedly succeeded")


def verify_pool_saturation_does_not_deadlock() -> None:
    username = "r01-pool-unknown-" + uuid.uuid4().hex[:12]
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as executor:
        results = list(executor.map(lambda n: invalid_login(n, username), range(20)))
    elapsed = time.monotonic() - started
    statuses = collections.Counter(status for status, _ in results)
    assert elapsed < 20, f"pool-sized concurrent login batch took {elapsed:.2f}s"
    assert statuses == {422: 5, 429: 15}, statuses
    assert all(retry and retry.isdigit() for status, retry in results if status == 429)


def main() -> int:
    require_environment()
    smoke_started_epoch = int(time.time()) - 1
    login_key = idem("login-first")
    first_session = login(PASSWORD, login_key)
    assert login(PASSWORD, login_key) == first_session, (
        "login did not replay the exact first data response"
    )
    access_token = first_session.get("accessToken")
    assert access_token and first_session.get("mfaRequired") == "NONE"

    overview = call("GET", "/admin-api/v1/me/security", token=access_token)
    assert overview.get("mfaEnabled") is False

    enroll_key = idem("enroll")
    enrollment = call(
        "POST",
        "/admin-api/v1/me/security/mfa/enroll",
        token=access_token,
        idempotency_key=enroll_key,
    )
    enrollment_replay = call(
        "POST", "/admin-api/v1/me/security/mfa/enroll",
        token=access_token, idempotency_key=enroll_key,
    )
    assert enrollment_replay == enrollment, (
        "MFA enroll did not replay the exact first data response"
    )
    enrollment_id = enrollment.get("enrollmentId")
    totp_seed = qr_secret(enrollment.get("secretQrCodeUrl", ""))
    assert enrollment_id and len(totp_seed) >= 32
    first_step = stable_previous_step()
    admin_fact = db_rows(f"""
        SELECT split_part(scope,':',2),m.version,m.status
        FROM hhy.idempotency_records i
        JOIN hhy.admin_mfa_methods m
          ON m.admin_user_id=split_part(i.scope,':',2)::bigint
        WHERE i.idem_key='{safe_sql_key(enroll_key)}';
    """)
    assert len(admin_fact) == 1 and admin_fact[0][2] == "PENDING", admin_fact
    admin_id = int(admin_fact[0][0])
    version_before_confirm = int(admin_fact[0][1])

    rejected_confirmation_key = idem("confirm-rejected")
    valid_window_codes = {
        totp_step(totp_seed, step)
        for step in range(first_step, first_step + 3)
    }
    rejected_code = next(
        f"{candidate:06d}"
        for candidate in range(1_000_000)
        if f"{candidate:06d}" not in valid_window_codes
    )
    rejected_confirmation = {
        "enrollmentId": enrollment_id,
        "code": rejected_code,
    }
    for _ in range(2):
        rejected = expect_error(
            "POST",
            "/admin-api/v1/me/security/mfa/confirm",
            422,
            body=rejected_confirmation,
            token=access_token,
            idempotency_key=rejected_confirmation_key,
        )
        assert (
            rejected.get("error", {}).get("code") == "COMMON-422-BUSINESS_RULE"
        ), rejected

    confirm_key = idem("confirm-concurrent")
    confirm_body = {
        "enrollmentId": enrollment_id,
        "code": totp_step(totp_seed, first_step),
    }
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        confirmations = list(
            executor.map(
                lambda _: call(
                    "POST",
                    "/admin-api/v1/me/security/mfa/confirm",
                    body=confirm_body,
                    token=access_token,
                    idempotency_key=confirm_key,
                ),
                range(2),
            )
        )
    assert all(value.get("mfaEnabled") is True for value in confirmations)
    assert confirmations[0] == confirmations[1], (
        "concurrent MFA confirm did not return one immutable first response"
    )
    confirm_fact = db_rows(f"""
        SELECT m.version,m.status,
               (SELECT count(*) FROM hhy.admin_operation_logs l
                WHERE l.admin_id={admin_id} AND l.action='ADMIN_MFA_ENABLED'
                  AND l.created_at>=to_timestamp({smoke_started_epoch}))
        FROM hhy.admin_mfa_methods m
        WHERE m.admin_user_id={admin_id} AND m.method='TOTP';
    """)
    assert len(confirm_fact) == 1, confirm_fact
    assert int(confirm_fact[0][0]) == version_before_confirm + 2, confirm_fact
    assert confirm_fact[0][1] == "ACTIVE" and confirm_fact[0][2] == "1", confirm_fact
    login_step = first_step + 1
    mfa_login_key = idem("login-mfa")
    mfa_session = login(PASSWORD, mfa_login_key)
    assert login(PASSWORD, mfa_login_key) == mfa_session, (
        "MFA-required login did not replay its first ticket response"
    )
    verify_key = idem("verify-first")
    access_token, _ = verify_ticket(
        mfa_session, totp_seed, login_step, verify_key
    )
    assert call(
        "POST", "/admin-api/v1/me/security/mfa/enroll",
        token=access_token, idempotency_key=enroll_key,
    ) == enrollment, "MFA enroll snapshot changed after confirm"
    replayed_change = expect_error(
        "POST",
        "/admin-api/v1/me/security/password/change",
        422,
        body={
            "currentPassword": PASSWORD,
            "newPassword": NEW_PASSWORD,
            "mfaCode": totp_step(totp_seed, login_step),
        },
        token=access_token,
        idempotency_key=idem("password-replay"),
    )
    assert replayed_change.get("error", {}).get("code") == "COMMON-422-BUSINESS_RULE"

    password_step = first_step + 2
    password_key = idem("password")
    password_body = {
        "currentPassword": PASSWORD,
        "newPassword": NEW_PASSWORD,
        "mfaCode": totp_step(totp_seed, password_step),
    }
    password_bearer = access_token
    changed = call(
        "POST",
        "/admin-api/v1/me/security/password/change",
        body=password_body,
        token=password_bearer,
        idempotency_key=password_key,
    )
    assert changed.get("mfaEnabled") is True
    assert call(
        "POST", "/admin-api/v1/me/security/password/change",
        body=password_body, token=password_bearer, idempotency_key=password_key,
    ) == changed, "password change was not replayable through its revoked old bearer"
    password_conflict = expect_error(
        "POST", "/admin-api/v1/me/security/password/change", 409,
        body={**password_body, "currentPassword": "Different!234"},
        token=password_bearer, idempotency_key=password_key,
    )
    assert password_conflict.get("error", {}).get("code") == (
        "COMMON-409-IDEMPOTENCY_CONFLICT"
    ), password_conflict

    second_login_step = first_step + 3
    wait_until_step(second_login_step - 1)
    second_session = login(NEW_PASSWORD, idem("login-after-password"))
    access_token, _ = verify_ticket(
        second_session, totp_seed, second_login_step, idem("verify-after-password")
    )
    replayed_disable = expect_error(
        "POST",
        "/admin-api/v1/me/security/mfa/disable",
        422,
        body={"code": totp_step(totp_seed, second_login_step), "reason": "replay check"},
        token=access_token,
        idempotency_key=idem("disable-replay"),
    )
    assert replayed_disable.get("error", {}).get("code") == "COMMON-422-BUSINESS_RULE"

    disable_step = first_step + 4
    wait_until_step(disable_step - 1)
    disable_key = idem("disable")
    disable_body = {
        "code": totp_step(totp_seed, disable_step),
        "reason": "R01 isolated API smoke",
    }
    disabled = call(
        "POST",
        "/admin-api/v1/me/security/mfa/disable",
        body=disable_body,
        token=access_token,
        idempotency_key=disable_key,
    )
    assert disabled.get("mfaEnabled") is False
    assert call(
        "POST", "/admin-api/v1/me/security/mfa/disable",
        body=disable_body, token=access_token, idempotency_key=disable_key,
    ) == disabled, "MFA disable did not replay the exact first data response"
    assert call(
        "POST", "/admin-api/v1/me/security/mfa/confirm",
        body=confirm_body, token=access_token, idempotency_key=confirm_key,
    ) == confirmations[0], "MFA confirm snapshot changed after disable"

    no_snapshot = expect_error(
        "POST", "/admin-api/v1/me/security/password/change", 401,
        body=password_body, token=password_bearer,
        idempotency_key=idem("password-no-snapshot"),
    )
    assert no_snapshot.get("error", {}).get("code") == (
        "COMMON-401-UNAUTHENTICATED"
    ), no_snapshot
    unchanged_session = login(NEW_PASSWORD, idem("login-zero-side-effect"))
    assert unchanged_session.get("accessToken") and unchanged_session.get("mfaRequired") == "NONE"

    logout_key = idem("logout")
    logout_bearer = access_token
    logged_out = call(
        "POST",
        "/admin-api/v1/auth/logout",
        token=logout_bearer,
        idempotency_key=logout_key,
    )
    assert logged_out.get("status") == "REVOKED"
    assert call(
        "POST", "/admin-api/v1/auth/logout",
        token=logout_bearer, idempotency_key=logout_key,
    ) == logged_out, "logout was not replayable through its revoked old bearer"
    assert_persisted_snapshots({
        "login": login_key,
        "verify": verify_key,
        "logout": logout_key,
        "password_change": password_key,
        "enroll": enroll_key,
        "confirm": confirm_key,
        "disable": disable_key,
    })
    verify_pool_saturation_does_not_deadlock()
    print(
        "R01_ADMIN_SECURITY_API_SMOKE PASS endpoints=8 envelope=PASS "
        "mfa=PASS totp_replay=BLOCKED concurrent_idempotency=PASS "
        "seven_post_snapshots=PASS revoked_bearer_replay=PASS "
        "digest_conflict=PASS replay_zero_side_effect=PASS "
        "rejected_idempotency_retry=PASS pg_pool_concurrency=20"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"R01_ADMIN_SECURITY_API_SMOKE FAIL: {error}", file=sys.stderr)
        raise SystemExit(1) from error
