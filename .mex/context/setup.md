---
name: setup
description: Development environment setup and commands.
triggers:
  - "setup"
  - "install"
  - "environment"
  - "getting started"
  - "local development"
edges:
  - target: context/stack.md
    condition: when specific technology versions or libraries are needed
  - target: context/architecture.md
    condition: when understanding components during setup
  - target: context/offline-first.md
    condition: when testing local storage, APK install, or device behavior
  - target: context/conventions.md
    condition: when setup introduces project-wide tooling or scripts
grounds_to: []
last_updated: 2026-09-16
---

# Setup

## Prerequisites

- Node.js 22.5+ (required by MEX; current environment: Node 24).
- Git.
- PowerShell 7. Android Studio is optional because the bootstrap installs a portable JDK, Gradle and Android SDK.
- An Android device or emulator with geolocation, notifications and WebGL/GLB support.
- Blender only when creating or editing 3D assets.

## First-time Setup

1. Run `pwsh -NoProfile -File scripts/bootstrap-android.ps1` once. It downloads checksum-verified pinned tools under `%LOCALAPPDATA%\Faunavia\toolchains`.
2. In each PowerShell session run `. scripts/android-env.ps1`.
3. Run `.\gradlew.bat verifyAll --no-daemon` for the complete F0–F2 gate, including Room integrity and migrations.
4. Open the project in Android Studio only for interactive development; use the JDK and SDK paths from the manifest generated in the portable toolchain root.
5. For MEX maintenance, use `mex graph status` and `mex hub`.

## Environment Variables

- `FAUNAVIA_TOOLCHAIN_ROOT` optionally relocates the portable toolchain.
- `FAUNAVIA_BUILD_ROOT` optionally relocates generated build output; `android-env.ps1` defaults it outside OneDrive.
- `FAUNAVIA_SHORT_TEMP` optionally selects a short temporary directory when a sandboxed Windows Java process cannot open its loopback socket.
- Provider keys are not assumed; if a provider later requires one, document it as optional and keep a no-key fallback.

## Common Commands

- `.\gradlew.bat verifyFast --no-daemon` — build debug APK, format/static checks, lint and JVM tests.
- `.\gradlew.bat verifyDevice --no-daemon` — install, launch and test on the API 36 managed device.
- `.\gradlew.bat verifyVisual --no-daemon` — execute and confirm the visual golden test.
- `.\gradlew.bat verifyAll --no-daemon` — full F0–F2 regression and report index, including Room tests in the app's managed-device suite.
- `pwsh -NoProfile -File scripts/verify-failure-report.ps1` — prove that a failed gate still publishes diagnostics.
- `mex graph status` — inspect the MEX graph.
- `mex wiki rebuild-index` — rebuild the MEX wiki index after context changes.

## Common Issues

- **Provider limits or downtime:** show cached results and the provider error; do not retry in a tight loop.
- **Geolocation denied:** allow GPX/GeoJSON import or manual place search; never block at the permission dialog.
- **MEX indexes stale:** run explicit graph/wiki maintenance after reviewing working-tree changes.
- **OneDrive file locks:** always source `scripts/android-env.ps1`; it places generated Gradle outputs outside the synchronized checkout.
- **Sandboxed Java loopback failure:** set `$env:FAUNAVIA_SHORT_TEMP='C:\ftmp'` before sourcing `android-env.ps1`.
