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
last_updated: 2026-10-03
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
3. Run `.\gradlew.bat verifyAll --no-daemon` for the complete F0–F12 gate, including Room integrity, migrations, private photos, reminders and SAF backup UI/IO.
4. Open the project in Android Studio only for interactive development; use the JDK and SDK paths from the manifest generated in the portable toolchain root.
5. For MEX maintenance, use `mex graph status` and `mex hub`.

## Environment Variables

- `FAUNAVIA_TOOLCHAIN_ROOT` optionally relocates the portable toolchain.
- `FAUNAVIA_BUILD_ROOT` optionally relocates generated build output; `android-env.ps1` defaults it outside OneDrive.
- `FAUNAVIA_SHORT_TEMP` optionally selects a short temporary directory for Windows path/temp issues; it does not bypass sandbox network isolation.
- `android-env.ps1` normalizes `JAVA_OPTS` to the heap and encoding expected by `gradle.properties`, allowing `--no-daemon` to run in-process on Windows. It also sets `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=NUL`, inherited by test workers, so the JDK uses its TCP fallback instead of the blocked Unix-domain socket.
- Provider keys are not assumed; if a provider later requires one, document it as optional and keep a no-key fallback.

## Common Commands

- `.\gradlew.bat verifyFast --no-daemon` — build debug APK, format/static checks, lint and JVM tests.
- `.\gradlew.bat verifyDevice --no-daemon` — install, launch and test on the API 36 managed device.
- `.\gradlew.bat verifyVisual --no-daemon` — execute and confirm the visual golden test.
- `.\gradlew.bat verifyAll --no-daemon` — full F0–F14 automated regression and report index, including Room, photos, reminders, backup, profiles, native model pixels and UI tests. F14 human approval/physical-device review are separate required gates.
- `.\gradlew.bat verifyOccurrenceSmoke --no-daemon` — explicit live GBIF/NNB health check; it saves no response and is never part of `verifyFast`.
- `pwsh -NoProfile -File scripts/verify-failure-report.ps1` — prove that a failed gate still publishes diagnostics.
- `mex graph status` — inspect the MEX graph.
- `mex wiki rebuild-index` — rebuild the MEX wiki index after context changes.
- F13 `verifyVisual` additionally requires `speciesProfileMatchesVersionedSignatureAndReadableTitleContrast`; four visual signatures cover home, exploration, trips and species detail. `artifacts/f13-profile.png` is a synthetic emulator capture; `GUIDA-FASE-13.md` documents the profile gate and offline limits.
- F14 `verifyFast` runs `node assets-3d/validate.cjs --check`; install its pinned dev dependency using `npm --prefix assets-3d ci`. A fifth `verifyVisual` signature requires actual GLB pixels/gestures/disposal. `scripts/build-3d-assets.ps1` uses portable official bpy 4.5.3 and bundled Python 3.11 to regenerate source/preview/deterministic GLB/manifest; see `GUIDA-FASE-14.md`.

## Common Issues

- **Windows emulator native GLB crash:** SwiftShader OpenGL surface rendering crashed qemu with native access violation. The F14 viewer selects Vulkan when advertised by Android, which renders successfully on this emulator. Preflight parsing alone did not reveal that surface issue; keep actual pixel and lifecycle tests.
- **Portable Blender SideBySide:** this local `blender.exe` launcher fails before Python runs. Official `bpy==4.5.3` in the portable toolchain, launched by its included Python, executes the same authoring script without global installation.

- **Provider limits or downtime:** show cached results and the provider error; do not retry in a tight loop.
- **Geolocation denied:** allow GPX/GeoJSON import or manual place search; never block at the permission dialog.
- **MEX indexes stale:** run explicit graph/wiki maintenance after reviewing working-tree changes.
- **OneDrive file locks:** always source `scripts/android-env.ps1`; it places generated Gradle outputs outside the synchronized checkout.
- **Java/Gradle loopback failure:** source `scripts/android-env.ps1` again, then run `gradlew.bat help --no-daemon`. The project aligns `-Xms64m`, `-Xmx3g`, encoding and the instrumentation-agent setting so Gradle can remain in-process; `jdk.net.unixdomain.tmpdir=NUL` makes the JDK use its TCP fallback for Gradle test-worker channels. If the sandbox instead denies toolchain file access, repeat the canonical command in a normal PowerShell session or an explicitly permitted out-of-sandbox execution. See `GUIDA-GRADLE-LOOPBACK.md`.
