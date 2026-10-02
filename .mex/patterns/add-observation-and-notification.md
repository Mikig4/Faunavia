---
name: add-observation-and-notification
description: Add or change manual diary entries, photos, peculiar suggestions, or the daily local summary notification.
triggers:
  - "add observation"
  - "avvistamento"
  - "photo observation"
  - "daily notification"
  - "suggestion rule"
edges:
  - target: context/observations-notifications.md
    condition: always, because diary and notification semantics are the feature contract
  - target: context/android-local.md
    condition: when implementing Room, Photo Picker, WorkManager, alarms, or Android permissions
  - target: context/data-and-provenance.md
    condition: when distinguishing manual records from provider evidence
last_updated: 2026-10-02
---

# Add an observation or daily notification

## Steps

1. Keep the manual observation path independent from the suggestion list.
2. Resolve the animal through the catalogue by common/scientific name or synonym; save an accepted taxon ID.
3. Save the structured record first; attach a local photo reference separately.
4. Resolve the local date using the device timezone and test midnight/DST boundaries.
5. Schedule or update one idempotent daily local check; do not add FCM or a server for local data.
6. Show the summary only when at least one manual observation exists.

## Verify

- [ ] An animal absent from suggestions can still be saved.
- [ ] Photo access is optional and the record works without a photo.
- [ ] No network is required to save, view, or summarize an observation.
- [ ] The notification is not emitted for an empty day.
- [ ] Android 13+ permission denial disables the reminder without disabling the diary.

## F9 curation and wishlist changes

1. Keep profile curation in `PilotSuggestionProfiles`, source every factual habitat/reason and version the set. The curator envelope is not an administrative boundary or species range; never infer a local habitat match from the generic profile.
2. Feed `PersonalSuggestionEngine` only original evidence candidates. Keep evidence objects/levels unchanged. Typical uses the editorial threshold and urban exclusion; other views must retain common species.
3. Never use occurrence count, distinctive appearance or habitat advice as a comparable ease score. Show unavailable estimates explicitly. Sorting needs scientific-name and ID tie breakers.
4. For never-observed, load the global identified diary and accepted catalogue taxa. Match identity across provider IDs via canonical scientific names where possible, without claiming synonym resolution. Recompute on re-entry after diary edits/deletes; drafts never count.
5. Wishlist writes require a selected accepted identity. Use repository transactions, protect wish identities at SQL level, preserve the initial add timestamp and ensure wish deletion cannot delete the diary or taxon.
6. When reusing saved trip evidence, filter by the exact current `tripAnalysisKey`, including stage/outing. Missing/obsolete evidence means relevance unassessed, never absence. Keep partial/stale labels.

F9 verification: pure profile/threshold/sort/identity/absence tests; Room populated migration and offline reopening; UI add/remove, restoration, errors, diary recomputation, preserved evidence and valid/outdated snapshot relevance. Use `verifyAll` for the cumulative final gate.

## F10 private photos

1. Save the text memory first. Open its gallery from the identified or draft row; attach copies separately through `MemoryPhotos`. Picker access is image-only, temporary and optional; no broad storage/camera permission and no upload.
2. `PrivatePhotoStore` owns bounded IO in Android: 32 MiB input, 100 MP header, sampled decoding, all eight EXIF orientations, fresh JPEG pixels up to 2048 and a 320 thumbnail. Keep originals unchanged and strip their metadata by re-encoding. Store copies in `noBackupFilesDir`, persist only private relative paths/hash/size/dimensions/normal orientation, and reject escaping paths.
3. Commit complete file pairs before metadata. On a metadata failure remove the new files. On deletion remove metadata first, return cleanup status visibly, and recover only unreferenced files older than 24 hours under the shared store mutex. Never delete referenced or recent files as crash recovery.
4. Room 8 adds photo dimensions/thumbnail defaults without changing legacy photo identities and adds draft photo foreign keys. Conversion captures draft metadata before cascade deletion and inserts observation/photos in the same transaction; any failure rolls back all owners. Planning deletion retains copies.
5. Keep `PhotoGalleryModel` jobs across Activity recreation and the open memory ID in saveable state. After process death reload committed Room metadata/private copies; unfinished import requires re-selection. Missing/corrupt copies show a fallback, never remove text or silently erase metadata. Backup work remains F12.

Verify with `F10PhotoTest`, `F10PhotoUiTest`, domain metadata constraints and the full `verifyAll` gate. Seed only synthetic images into MediaStore for the real UI Automator picker; delete those test media afterward. Capture the gallery and check actual rotated image colors, not only semantics. See `GUIDA-FASE-10.md`.

## F11 reminders

1. Use current-device local day boundaries, not the zone stored on each observation. Count identified records, not quantity, and exclude drafts/provider tables.
2. Keep one unique WorkManager periodic request with no network constraint. Delay the first check to the preferred time; use KEEP on ordinary reopening and realign on preference/timezone/system-clock changes. Complete preference persistence and scheduling together despite cancellation of the UI scope; test cancellation after persistence but before enqueue. Eligibility ends at midnight; never send a previous-day catch-up.
3. Request POST_NOTIFICATIONS only after explicit activation. Check runtime/global/channel permission at delivery too. Revocation must not affect diary writes; settings reconciliation cancels/disables the reminder. Time parsing must reject 24:00 and seconds, and disabling must remain available with invalid edited time.
4. Serialize snapshot/post/delivery ledger under a short Room transaction. Mark only successful sends. Key the ledger by date even across zone changes. Stable per-date notification tags and onlyAlertOnce reduce the hard-process-death gap; never claim a cross-system atomic transaction.
5. Preserve notification date/zone in the summary deep link and saveable navigation. Load only local catalogue/diary/photos; gallery recovery must include unidentified-photo references too.
6. Test fake clock, zero/N, quantities/drafts, midnight/DST/zone, concurrent repeats, reopen/migration, post/storage retry, unique scheduling, runtime grant/denial, actual notification tap and Activity recreation. Simulated reopen is not physical reboot/Doze verification.

Test gotcha: onNewIntent changes MainActivity's Intent, so ActivityScenario ignores lifecycle events whose signature no longer matches its launcher. In the notification test, restore only the original test launch signature after the link is consumed and invoke the actual Activity's recreate, then wait for a new instance. Do not remove recreation coverage or change production navigation to satisfy the harness. On Windows use a single class instrumentation filter; comma-separated Gradle arguments may silently select only the first class. The final verifyAll omission check remains mandatory.
