---
name: debug-gradle-android-gates
description: Diagnose Gradle startup, JVM worker, managed-device, UTP and Compose UI gate failures without weakening verification.
triggers:
  - "Gradle failure"
  - "loopback"
  - "verifyDevice"
  - "verifyVisual"
  - "managed device"
  - "Compose UI test"
edges:
  - target: context/setup.md
    condition: when checking the portable JDK, SDK, build root or shell environment
  - target: context/android-local.md
    condition: when checking managed-device, UTP or Android test behavior
  - target: context/conventions.md
    condition: before accepting a verification result
grounds_to: []
last_updated: 2026-09-21
---

# Debug Gradle and Android gates

## Context

Source `scripts/android-env.ps1` before every Gradle command. Generated output belongs under `FAUNAVIA_BUILD_ROOT`, outside the OneDrive checkout. The canonical gates are `verifyFast`, `verifyDevice`, `verifyVisual` and `verifyAll`; temporary workarounds must not replace their final execution.

## Steps

1. Reproduce the smallest failing command with `--no-daemon --stacktrace`; start with `help`, then the failed gate.
2. If Java reports `Unable to establish loopback connection`, source `scripts/android-env.ps1` again and reproduce with `gradlew.bat help --no-daemon --debug`. The debug output must not say that heap options or instrumentation-agent status differ. The checked-in fix aligns wrapper `-Xms64m`, `-Xmx3g`, encoding and disables the Gradle agent for this project so execution can remain in-process.
3. If startup passes but a test worker has the same error, verify that the shell prints `Picked up JAVA_TOOL_OPTIONS: -Djdk.net.unixdomain.tmpdir=NUL`. On Windows this deliberately prevents the restricted Unix-domain socket from binding, so the JDK's built-in TCP fallback handles the worker channel.
4. If in-process Gradle instead reports `AccessDeniedException` for a toolchain JAR, repeat outside the restricted Codex sandbox. This is a filesystem boundary, not an application failure.
5. Use `$env:FAUNAVIA_SHORT_TEMP='C:\ftmp'` only for Windows path/temp failures, then source `scripts/android-env.ps1` again. It does not grant network or loopback permission.
6. After infrastructure starts, treat test failures as real. Read `app/outputs/androidTest-results/managedDevice/debug/pixel2Api36/TEST-pixel2Api36.xml` under the external build root and map every failure to its source test.
7. For Compose content inside a `LazyColumn`, tag the scroll container and use `performScrollToNode(hasTestTag(...))` before asserting or clicking a dynamic or off-screen item. Close the soft keyboard before scrolling to controls below text fields.
8. When checking only a fragment, pass `substring = true` to `assertTextContains`; its default is an exact text match.
9. Re-run the focused class if useful, then finish with a full canonical gate. `verifyDevice` also checks that every declared instrumented test ran; `verifyVisual` requires the golden test result.

## Gotchas

- `--no-daemon` still uses a single-use daemon if wrapper/build JVM arguments or instrumentation-agent status differ. Keep `JAVA_OPTS`, `JAVA_TOOL_OPTIONS` and the related `gradle.properties` settings synchronized.
- PowerShell arguments beginning with `-P` should be quoted when passed to `gradlew.bat`.
- A generated APK or test APK does not prove instrumentation ran. Require the XML report and the `verifyDevice` anti-omission check.
- Do not accept direct JUnit execution or in-process compilation as the final gate when canonical Gradle execution is available outside the sandbox.

## Verify

- [ ] `verifyFast` passes with Gradle-owned JVM test tasks.
- [ ] `verifyDevice` starts the managed device, runs all declared tests and reports no omissions.
- [ ] `verifyVisual` confirms `HomeGoldenTest`.
- [ ] `verifyAll` publishes the cumulative verification index.
- [ ] `context/setup.md`, `context/android-local.md` and `.mex/ROUTER.md` describe the observed boundary accurately.

## Update Scaffold

- [ ] Update `.mex/ROUTER.md` "Current Project State" if a blocked gate becomes green or a new blocker appears.
- [ ] Update `.mex/context/setup.md` and `.mex/context/android-local.md` when the environment boundary changes.
- [ ] Add any repeatable new failure signature or Compose testing gotcha to this pattern.
