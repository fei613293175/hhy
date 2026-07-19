#!/usr/bin/env python3
from __future__ import annotations

from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any, Mapping, Sequence
from urllib.request import Request
import hashlib
import importlib.util
import json
import sys
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "deliver_android_test_apk", ROOT / "scripts" / "deliver_android_test_apk.py"
)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load APK delivery module")
delivery = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = delivery
SPEC.loader.exec_module(delivery)


FINGERPRINT = "ab" * 32
COMMIT = "1234567" + "8" * 33


class FakePublisher:
    def __init__(self, *, state: str = "CREATED") -> None:
        self.state = state
        self.publish_calls = 0
        self.verify_calls = 0
        self.retract_calls = 0

    def publish(self, apk_path: Path, identity: Any) -> Any:
        self.publish_calls += 1
        return delivery.RemotePublication(
            self.state,
            f"/www/download/{identity.release.lower()}-artifacts/{identity.apk_file}",
            identity.sha256,
            identity.size_bytes,
        )

    def verify(self, publication: Any) -> dict[str, Any]:
        self.verify_calls += 1
        return {
            "status": "PASS",
            "sha256": publication.sha256,
            "size_bytes": publication.size_bytes,
        }

    def retract_if_created(self, publication: Any) -> None:
        if publication.state == "CREATED":
            self.retract_calls += 1


def passing_https(url: str, path: Path, sha256: str, size: int) -> dict[str, Any]:
    if delivery.stream_sha256(path) != sha256 or path.stat().st_size != size:
        raise delivery.DeliveryError("local fixture mismatch")
    return {
        "status": "PASS",
        "http_status": 200,
        "range_status": 206,
        "content_type": "application/vnd.android.package-archive",
        "sha256": sha256,
        "size_bytes": size,
        "verified_at": "2026-07-18T00:00:00Z",
    }


class FakeHttpResponse(BytesIO):
    def __init__(
        self,
        body: bytes,
        status: int,
        headers: Mapping[str, str],
        url: str,
    ) -> None:
        super().__init__(body)
        self.status = status
        self.headers = dict(headers)
        self._url = url

    def geturl(self) -> str:
        return self._url

    def __enter__(self) -> "FakeHttpResponse":
        return self

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        self.close()


class DeliveryFixture:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.apk = root / "input" / "app-debug.apk"
        self.apk.parent.mkdir(parents=True)
        self.apk.write_bytes((b"hhy-android-test-apk\n" * 5000) + b"end")
        self.build_evidence = root / "input" / "build-evidence.json"
        self.artifact_root = root / "repo" / "artifacts" / "apk"
        self.evidence_root = root / "repo" / "artifacts" / "validation"
        self.desktop = root / "Desktop"
        self.write_build_evidence()

    def evidence_value(self) -> dict[str, Any]:
        return {
            "release": "R02",
            "commit": COMMIT,
            "version_name": "1.2.2-debug",
            "version_code": 10202,
            "build_status": "PASS",
            "checks": ["testDebugUnitTest", "lintDebug", "assembleDebug", "apksigner"],
            "stable_signing": True,
            "signing_profile_id": "staging-test-profile-v1",
            "signing_fingerprint": FINGERPRINT,
            "api_base_url": "https://api.orbexa.cc",
            "built_at": "2026-07-18T00:00:00Z",
        }

    def write_build_evidence(self, **updates: Any) -> None:
        value = self.evidence_value()
        value.update(updates)
        self.build_evidence.write_text(json.dumps(value), encoding="utf-8")

    def config(self, **updates: Any) -> Any:
        value = {
            "release": "R02",
            "commit": COMMIT,
            "version_name": "1.2.2-debug",
            "version_code": 10202,
            "apk": self.apk,
            "build_evidence": self.build_evidence,
            "expected_signing_fingerprint": FINGERPRINT,
            "desktop_dir": self.desktop,
            "artifact_root": self.artifact_root,
            "evidence_root": self.evidence_root,
            "public_base_url": "https://download.orbexa.cc",
        }
        value.update(updates)
        return delivery.PrepareConfig(**value)


class AndroidApkDeliveryTest(unittest.TestCase):
    def test_identity_rules_are_strict_and_deterministic(self) -> None:
        self.assertEqual(10202, delivery.expected_version_code("R02"))
        self.assertEqual("hhy-r02-1234567-debug.apk", delivery.canonical_apk_name("R02", COMMIT))
        for invalid in ["R2", "R002", "02", "R 02"]:
            with self.subTest(invalid=invalid):
                with self.assertRaises(delivery.DeliveryError):
                    delivery.validate_release(invalid)
        with self.assertRaises(delivery.DeliveryError):
            delivery.validate_commit("1234567")
        with self.assertRaises(delivery.DeliveryError):
            delivery.validate_commit("0" * 40)
        with self.assertRaises(delivery.DeliveryError):
            delivery.validate_version_code("R02", 10201)
        self.assertEqual(10204, delivery.validate_version_code("R03", 10204))
        with self.assertRaises(delivery.DeliveryError):
            delivery.validate_version_code("R03", True)
        with self.assertRaises(delivery.DeliveryError):
            delivery.validate_version_name("1.2.2")

    def test_public_url_and_ssh_inputs_reject_unsafe_values(self) -> None:
        self.assertEqual(
            "https://download.orbexa.cc",
            delivery.validate_public_base_url("https://download.orbexa.cc/"),
        )
        for invalid in [
            "http://download.orbexa.cc",
            "https://other.example.com",
            "https://user:secret@download.orbexa.cc",
            "https://download.orbexa.cc/path",
        ]:
            with self.subTest(invalid=invalid):
                with self.assertRaises(delivery.DeliveryError):
                    delivery.validate_public_base_url(invalid)
        for invalid in ["-oProxyCommand=x", "alias with spaces", "alias;rm"]:
            with self.subTest(invalid=invalid):
                with self.assertRaises(delivery.DeliveryError):
                    delivery.validate_ssh_alias(invalid)
        with self.assertRaises(delivery.DeliveryError):
            delivery.validate_remote_root("/www/../secret")

    def test_stream_sha256_handles_multiple_blocks(self) -> None:
        with TemporaryDirectory() as temporary:
            path = Path(temporary) / "large.apk"
            value = b"0123456789abcdef" * 200_000
            path.write_bytes(value)
            self.assertEqual(hashlib.sha256(value).hexdigest(), delivery.stream_sha256(path, 8191))

    def test_prepare_dry_run_validates_but_writes_and_publishes_nothing(self) -> None:
        with TemporaryDirectory() as temporary:
            fixture = DeliveryFixture(Path(temporary))
            publisher = FakePublisher()
            result = delivery.prepare_delivery(
                fixture.config(), publisher, https_verifier=passing_https, dry_run=True
            )
            self.assertTrue(result["dry_run"])
            self.assertEqual(0, publisher.publish_calls)
            self.assertFalse(fixture.artifact_root.exists())
            self.assertFalse(fixture.desktop.exists())

    def test_prepare_creates_pending_state_and_verified_copies(self) -> None:
        with TemporaryDirectory() as temporary:
            fixture = DeliveryFixture(Path(temporary))
            publisher = FakePublisher()
            result = delivery.prepare_delivery(
                fixture.config(), publisher, https_verifier=passing_https
            )
            self.assertEqual("PENDING_OWNER_ACCEPTANCE", result["status"])
            self.assertEqual(1, publisher.publish_calls)
            manifest_path = fixture.artifact_root / "R02" / "APK_MANIFEST.yaml"
            manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8"))
            self.assertEqual("PENDING", manifest["test_status"])
            self.assertEqual("PENDING", manifest["owner_physical_test"])
            self.assertEqual("PASS", manifest["delivery_status"])
            artifact = fixture.artifact_root / "R02" / manifest["apk_file"]
            desktop = fixture.desktop / manifest["apk_file"]
            self.assertEqual(manifest["sha256"], delivery.stream_sha256(artifact))
            self.assertEqual(manifest["sha256"], delivery.stream_sha256(desktop))

    def test_prepare_rejects_unstable_or_wrong_signing_before_publish(self) -> None:
        for updates in [
            {"stable_signing": False},
            {"signing_fingerprint": "cd" * 32},
            {"signing_profile_id": "default-debug"},
        ]:
            with self.subTest(updates=updates), TemporaryDirectory() as temporary:
                fixture = DeliveryFixture(Path(temporary))
                fixture.write_build_evidence(**updates)
                publisher = FakePublisher()
                with self.assertRaises(delivery.DeliveryError):
                    delivery.prepare_delivery(
                        fixture.config(), publisher, https_verifier=passing_https
                    )
                self.assertEqual(0, publisher.publish_calls)
                self.assertFalse((fixture.artifact_root / "R02" / "APK_MANIFEST.yaml").exists())

    def test_prepare_rejects_wrong_version_before_publish(self) -> None:
        with TemporaryDirectory() as temporary:
            fixture = DeliveryFixture(Path(temporary))
            publisher = FakePublisher()
            with self.assertRaises(delivery.DeliveryError):
                delivery.prepare_delivery(
                    fixture.config(version_code=10201), publisher, https_verifier=passing_https
                )
            self.assertEqual(0, publisher.publish_calls)

    def test_https_failure_retracts_only_new_publication_and_writes_no_state(self) -> None:
        with TemporaryDirectory() as temporary:
            fixture = DeliveryFixture(Path(temporary))
            publisher = FakePublisher(state="CREATED")

            def fail_https(url: str, path: Path, sha256: str, size: int) -> dict[str, Any]:
                raise delivery.DeliveryError("simulated HTTPS failure")

            with self.assertRaises(delivery.DeliveryError):
                delivery.prepare_delivery(
                    fixture.config(), publisher, https_verifier=fail_https
                )
            self.assertEqual(1, publisher.retract_calls)
            self.assertFalse((fixture.artifact_root / "R02" / "APK_MANIFEST.yaml").exists())
            self.assertFalse(
                (fixture.evidence_root / "r02-apk-delivery" / "delivery-evidence.json").exists()
            )

    def test_prepare_never_resets_an_existing_delivery(self) -> None:
        with TemporaryDirectory() as temporary:
            fixture = DeliveryFixture(Path(temporary))
            first = FakePublisher()
            delivery.prepare_delivery(fixture.config(), first, https_verifier=passing_https)
            second = FakePublisher()
            with self.assertRaises(delivery.DeliveryError):
                delivery.prepare_delivery(fixture.config(), second, https_verifier=passing_https)
            self.assertEqual(0, second.publish_calls)

    def test_accept_transitions_pending_to_pass_and_is_idempotent(self) -> None:
        with TemporaryDirectory() as temporary:
            fixture = DeliveryFixture(Path(temporary))
            delivery.prepare_delivery(
                fixture.config(), FakePublisher(), https_verifier=passing_https
            )
            confirmation = "项目所有者明确确认真机安装、启动与核心导航通过"
            result = delivery.accept_owner_test(
                "R02",
                fixture.artifact_root,
                fixture.evidence_root,
                confirmation,
                evidence_reference="owner-message-2026-07-18",
            )
            self.assertEqual("PASS", result["status"])
            manifest_path = fixture.artifact_root / "R02" / "APK_MANIFEST.yaml"
            manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8"))
            evidence_path = fixture.evidence_root / "r02-apk-delivery" / "delivery-evidence.json"
            evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
            self.assertEqual("PASS", manifest["test_status"])
            self.assertEqual("PASS", manifest["owner_physical_test"])
            self.assertEqual("PASS", evidence["owner_physical_test"]["status"])
            again = delivery.accept_owner_test(
                "R02", fixture.artifact_root, fixture.evidence_root, confirmation
            )
            self.assertTrue(again["idempotent"])
            with self.assertRaises(delivery.DeliveryError):
                delivery.accept_owner_test(
                    "R02",
                    fixture.artifact_root,
                    fixture.evidence_root,
                    "项目所有者给出了另一个不同的验收结论文本",
                )

    def test_accept_rejects_incomplete_machine_delivery(self) -> None:
        with TemporaryDirectory() as temporary:
            fixture = DeliveryFixture(Path(temporary))
            delivery.prepare_delivery(
                fixture.config(), FakePublisher(), https_verifier=passing_https
            )
            evidence_path = fixture.evidence_root / "r02-apk-delivery" / "delivery-evidence.json"
            evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
            evidence["https"]["status"] = "FAIL"
            evidence_path.write_text(json.dumps(evidence), encoding="utf-8")
            with self.assertRaises(delivery.DeliveryError):
                delivery.accept_owner_test(
                    "R02",
                    fixture.artifact_root,
                    fixture.evidence_root,
                    "项目所有者明确确认真机安装和启动已经通过",
                )

    def test_remote_chunk_failure_never_runs_atomic_publish(self) -> None:
        with TemporaryDirectory() as temporary:
            apk = Path(temporary) / "hhy-r02-1234567-debug.apk"
            apk.write_bytes(b"abcdefghijk")
            commands: list[list[str]] = []

            def failing_runner(args: Sequence[str], timeout: int) -> Any:
                commands.append(list(args))
                if args[0] == "scp":
                    return delivery.CommandResult(1, "", "simulated upload failure")
                return delivery.CommandResult(0, "", "")

            identity = delivery.ArtifactIdentity(
                "R02",
                COMMIT,
                "1.2.2-debug",
                10202,
                apk.name,
                delivery.stream_sha256(apk),
                apk.stat().st_size,
                FINGERPRINT,
            )
            publisher = delivery.RemotePublisher(
                "obx-test",
                "/www/wwwroot/download.orbexa.cc",
                runner=failing_runner,
                chunk_size=4,
                retries=2,
                retry_delay=0,
                nonce_factory=lambda: "abcdef1234567890",
            )
            with self.assertRaises(delivery.DeliveryError):
                publisher.publish(apk, identity)
            remote_commands = [args[-1] for args in commands if args and args[0] == "ssh"]
            self.assertIn("chmod 0755 /www/wwwroot/download.orbexa.cc/r02-artifacts", remote_commands[0])
            self.assertIn("umask 077", remote_commands[0])
            self.assertFalse(any("HHY_ATOMIC_PUBLISH" in command for command in remote_commands))

    def test_https_verifier_checks_full_hash_and_range_body(self) -> None:
        with TemporaryDirectory() as temporary:
            apk = Path(temporary) / "test.apk"
            body = b"0123456789" * 300
            apk.write_bytes(body)
            url = "https://download.orbexa.cc/r02-artifacts/hhy-r02-1234567-debug.apk"

            observed_requests: list[Request] = []

            def opener(request: Request, timeout: int) -> FakeHttpResponse:
                observed_requests.append(request)
                range_header = request.headers.get("Range")
                if range_header:
                    end = min(1023, len(body) - 1)
                    return FakeHttpResponse(
                        body[: end + 1],
                        206,
                        {"Content-Range": f"bytes 0-{end}/{len(body)}"},
                        url,
                    )
                return FakeHttpResponse(
                    body,
                    200,
                    {
                        "Content-Type": "application/vnd.android.package-archive",
                        "Content-Length": str(len(body)),
                        "Content-Disposition": 'attachment; filename="test.apk"',
                    },
                    url,
                )

            result = delivery.verify_https_download(
                url,
                apk,
                hashlib.sha256(body).hexdigest(),
                len(body),
                opener=opener,
            )
            self.assertEqual("PASS", result["status"])
            self.assertEqual(2, len(observed_requests))
            self.assertTrue(all(request.headers.get("Cache-control") == "no-cache" for request in observed_requests))
            with self.assertRaises(delivery.DeliveryError):
                delivery.verify_https_download(
                    url,
                    apk,
                    "cd" * 32,
                    len(body),
                    opener=opener,
                )

    def test_credential_options_are_rejected_without_parsing_values(self) -> None:
        with self.assertRaises(delivery.DeliveryError):
            delivery.reject_credential_arguments(["prepare", "--password", "do-not-print"])
        with self.assertRaises(delivery.DeliveryError):
            delivery.reject_credential_arguments(["prepare", "--identity-file=private-key"])


if __name__ == "__main__":
    unittest.main()
