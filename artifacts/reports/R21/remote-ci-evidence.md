# R21 Remote CI Evidence

Date: 2026-08-05

- GitHub Actions: [Run 31037022921](https://github.com/fei613293175/hhy/actions/runs/31037022921)
- Source Commit: `f049e672dd32a0fdf399780a73203977fc0846a8`.
- Run status: `completed / success`; the run HEAD SHA matches the source Commit.
- Web products: success; web/admin/H5 checks and production builds passed.
- Backend: success; PostgreSQL, migration/invariant, contract and R21 observability checks passed.
- Android build and unit tests: success; `verifyApiBaseUrl`, unit tests, lint and Debug APK assembly passed.
- R20/R21 Android emulator interactions: success; the historical visual audit produced the two R21 screenshots at the required 1080x2400 viewport.
- Fixed `obx-test` toolchain: `hhy-android-toolchain:r01-46fb273` (image id recorded in `.scratch/R21-obx-final/evidence/toolchain-image-id.txt`).
- Fixed test signing: `hhy-staging-test-v2`; v2/v3 signature, zipalign, package identity and embedded API/WSS checks passed.

The four job conclusions were independently read from the GitHub Actions run API. Owner physical-device verification remains an asynchronous external step and is documented in the R21 test guide.
