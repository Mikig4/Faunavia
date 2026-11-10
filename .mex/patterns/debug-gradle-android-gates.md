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
last_updated: 2026-11-10
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

- A managed-device boot snapshot can restore a Launcher3 ANR dialog and steal Activity focus before the first UI tests. On 2026-11-10, two `AnalysisLifecycleTest` cases repeatedly failed in `closeSoftKeyboard`; `adb shell dumpsys window` identified `Application Not Responding: com.android.launcher3`, and `dumpsys activity processes` confirmed `mNotResponding=true`. After the run finished and `adb devices` showed no emulator, only the exact AVD's `snapshots/default_boot` was moved to a recoverable sibling backup. `:app:pixel2Api36Setup --rerun-tasks` regenerated it, and all three focused tests passed unchanged. Preserve a recovery audit, verify paths remain within the intended disposable AVD, and finish with unfiltered `verifyAll`. Do not dismiss or suppress application failures as a workaround. See `artifacts/pending-emulator-recovery.json`.
- If Gradle cannot delete an old generated output, use a fresh `FAUNAVIA_BUILD_ROOT` outside OneDrive and rerun the canonical gate; preserve the prior output for diagnosis. The 2026-11-10 run used `builds/Faunavia-pending-20261110` after deletion of a generated manifest report failed in the previous build root.

- A cancelled managed-device run can leave `~/.android/avd/gradle-managed/active_gradle_devices` with a stale `MDLockCount`. On 2026-10-26 it was 4 with no emulator or previous build remaining; the next owned Gradle thread waited in `ManagedVirtualDeviceLockManager` before launching any emulator. Stop the waiting build, verify no active emulator/other device build, back up the counter and reset only that verified stale counter to zero. Keep a recovery audit record. Do not delete the AVD/snapshots or reset a live reservation. See `GUIDA-GRADLE-LOOPBACK.md`.
- Video preview semantics can be ready before the recreated native surface draws. Require actual selected-color pixels in the device capture, explicitly close the playback dialog and wait for Activity focus before ending the UI test. The first video gate exposed a black premature capture and later Espresso root-focus errors; the strengthened pixel/focus checks exercise the complete return path rather than masking failures with sleeps.

- Saved state inside nested native Compose Dialog content can be outside the test restoration registry. Hoist model visibility to the species-profile owner, with the model dialog as its sibling; clip/play controls then restore consistently. SAF `Until.findObject` can return an object made stale by animation: wait for presence/idle, then fetch a fresh node before clicking.

- `--no-daemon` still uses a single-use daemon if wrapper/build JVM arguments or instrumentation-agent status differ. Keep `JAVA_OPTS`, `JAVA_TOOL_OPTIONS` and the related `gradle.properties` settings synchronized.
- PowerShell arguments beginning with `-P` should be quoted when passed to `gradlew.bat`.
- A generated APK or test APK does not prove instrumentation ran. Require the XML report and the `verifyDevice` anti-omission check.
- Use Espresso `closeSoftKeyboard()` to dismiss the IME in UI tests. After a Save button already hides it, `UiDevice.pressBack()` can navigate out of Settings; F13 exposed this timing dependency in the existing F11 permission test. Retain the actual permission denial assertion rather than skipping the scenario.
- Android PermissionController can expose its denial button before the input window accepts touches: UiAutomator can find the node yet InputDispatcher drops the click. Re-query the button with a bounded retry and wait for the dialog to disappear, then assert the real permission, switch, database and diary states. Do not treat finding the node or clicking it as proof the system received the denial.
- The formatting gate scans `.xml` files under `artifacts` too. A failed Android test report may contain tab-indented stack traces; preserve that raw diagnostic as a `.log` file so it does not cause a separate formatting failure in the next run.
- Do not accept direct JUnit execution or in-process compilation as the final gate when canonical Gradle execution is available outside the sandbox.
- On this OneDrive checkout, `mex wiki validate` can temporarily report `Could not safely read` for placeholder Markdown files. Materialize them by reading their content with native file tools, then retry validation. Do not delete prose or change metadata to suppress a filesystem read failure. The 2026-10-02 retry validated all 27 canonical files.

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
