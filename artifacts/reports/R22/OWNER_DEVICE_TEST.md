# R22 owner physical-device test

R22 is release-candidate ready but cannot be closed until this checklist has real-device evidence. Emulator evidence is intentionally not substituted.

1. Copy `apk/hhy-r22-e10d705-test.apk` to an Android 16/API 36 compatible physical device and install it. Record the installation result and device model/OS.
2. Launch the app and capture a screenshot showing the app opens without crash or ANR.
3. Sign in with an authorized test account, open the red-packet home page, and capture the list.
4. Complete the eligibility flow and keep the browsing page visible for at least 20 seconds. Confirm the claim action appears only after the server-driven timer completes.
5. Claim once, then retry the same request/action. Confirm a single claim result (no double credit); capture the result.
6. Record APK SHA-256, version name/code and signer from `APK_MANIFEST.md` alongside the screenshots.

Report every item as PASS, FAIL or BLOCKED with its evidence. A failure is a R22 defect; do not advance the version.
