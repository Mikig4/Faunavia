---
name: backup-and-restore
description: Evolve the local ZIP backup, staging validation and atomic publication of memories and photos.
triggers:
  - "backup"
  - "export"
  - "restore"
edges:
  - target: context/android-local.md
    condition: when changing Room, SAF or private-photo behavior
  - target: context/offline-first.md
    condition: when deciding which durable data belongs in an archive
  - target: patterns/change-local-storage.md
    condition: when the database schema changes
grounds_to: []
last_updated: 2026-10-03
---

# Backup and restore

## Context

`LocalBackupStore` owns the schema-aware table snapshot and Room transaction. `LocalBackupArchive` owns ZIP, inventory, hashes, bounded staging and complete private image/model copies. F14 writes format 2/schema 10 (20 tables), reads released format 1/schema 9 and synthetic schema 8. `BackupViewModel` survives Activity recreation; SAF and confirmation stay in `BackupSection`. See `GUIDA-FASE-12.md` and `GUIDA-FASE-14.md` for contracts and limits.

## Steps

1. Inspect the exported Room schema and `LocalBackupStore.TABLES`. Keep every durable table, its provenance and nanosecond timestamp strings. Include photo metadata, planning links, wishlist, settings and notification ledger. Do not copy a live SQLite file or WAL.
2. Snapshot in a Room transaction while holding the application's photo mutex. Verify all referenced files; missing/corrupt images must fail export rather than silently disappear. Write manifest first, then data and images, with sizes and SHA-256.
3. Bound the raw import, manifest, inventory, paths, individual bytes, total bytes and rows. Require a complete ZIP central directory through `ZipFile`. Reject duplicates, unknown/missing files, traversal, incompatible versions and mismatched hashes/counts.
4. Validate in a separate staging Room database before current data changes. Keep schema, triggers, foreign keys, domain mappers, dates, links, bitmap bounds and metadata checks enabled. Schema 8 compatibility leaves the missing delivery ledger empty, without invented history.
5. Present date/counts and an explicit replacement confirmation. Copy photos into a fresh unique private subdirectory and sync complete files before committing. Rewrite only their paths. Replace all tables in one transaction with parents inserted before children and reverse deletion order.
6. Invalidate writes queued before restoration through the repository generation; photo operations have a generation checked inside the shared mutex. Serialize reminder preference mutations with restoration. After commit, recreate scheduling from restored preferences and clear old editor/analysis state. Scheduling failure is a successful restore with a visible retry message.
7. Keep copied files referenced by a successful commit. Remove unpublished copies after failure; orphan recovery includes nested photo directories after its 24-hour grace. Temporary ZIP/staging databases from a terminated process can be removed after 24 hours on the next backup operation. Expose incomplete cleanup instead of logging private content.

## Gotchas

- ZIP streaming alone can accept a missing central-directory tail. Test truncation after complete local entries too.
- DB rows and private files do not share one filesystem transaction. Appending unique complete files before Room publication leaves either old references or new complete references after an ordinary process interruption; it must never overwrite existing referenced files.
- The photo grace uses each file's last-modified time, not a persisted orphan-since timestamp. Previously old photos can be removed on the first recovery after replacement; do not promise 24 additional hours after restore.
- Restored Room services stay open. A new Activity task clears retained analyses and unsaved editors; it does not kill the application process.
- A queued pre-restore write must fail rather than reinsert old data. Notification permission and device timezone are platform state: reconciliation may disable a restored reminder when Android permission is absent.
- Preserve post-commit cleanup warnings in `LocalBackupArchive.lastMessage` before starting the fresh Activity task. Appending only to the old ViewModel's message loses them during task replacement. Exercise real directory permission denial and still assert committed data plus a visible recovery notice.
- Hashes verify content integrity, not the identity of an archive author. The local archive is not encrypted.
- Archive format 1 permits only database JSON and photo paths. Format 2 adds strict `models/<UUID>/model.glb` paths and checked matching metadata/bytes; map assets still need a future explicit format extension. Legacy restoration supplies an empty personal-model table rather than preserving unrelated current associations.
- `tablesFor(schema)` is the version-specific inventory: schema 8 lacks delivery/model tables, schema 9 lacks models, schema 10 includes all 20. Keep the F12 fixture asserting every original table is populated while explicitly expecting zero models; its dedicated F14 fixture covers model round-trip. Do not add unchecked fake metadata merely to make all table counts positive.
- Models share the photo mutex and restore generation. Validate structural/native resources in staging; copy complete bytes into fresh unique model directories, sync/hash-check, rewrite relative paths and commit rows atomically. Failed commit removes only newly created directories; never overwrite referenced existing assets. Preserve declared credits/import dates and check the exact inventory without extra/missing files.
- On this Windows toolchain, the comma-separated instrumentation class filter may execute only its first class. Run focused UI separately and finish with the unfiltered canonical gate.
- Managed-device orchestration can remove app-owned screenshot files between tests. Archive the synthetic SAF preview while it exists rather than assuming it remains in additional output after the full suite.
- Compose v2 controls the main test dispatcher. After returning from SAF, a settings refresh can hold the preference mutex while waiting to resume on that dispatcher. Run concurrent fixture mutations on IO while `compose.waitUntil` advances UI work; a blocking `runBlocking` on the test thread can deadlock the test itself.

## Verify

- Empty and populated full round-trip, fresh receiver and file-backed reopening.
- Every table, provenance, nanosecond dates, ordered stages, planning links and both kinds of photo metadata preserved.
- Failed staging, hash/size errors, missing/duplicate/unexpected files, future version, incompatible schema and malformed payload leave current data intact.
- Previous schema fixture, cancellation, low space, missing/corrupt local photo and failure immediately before commit.
- Queued writes rejected after restore; diary notification ledger, draft conversion and planning unlinking retain their earlier rules.
- Real SAF create/open, preview/cancel, Activity recreation, complete restore, readable report and reminder rescheduling without broad storage permission.
- `verifyAll --no-daemon`, anti-omission check and all existing visual signatures.

## Debug

Use `F12BackupTest` for archive/storage errors and `F12BackupUiTest` for actual SAF behavior. Wait for DocumentsUI transitions before re-fetching UI objects: a cached drawer item can become stale. Do not derive the app's returned document URI from a path; providers differ. Inspect synthetic test screenshots and the managed-device XML without exposing real diary content in logs.

## Update Scaffold

- Update `.mex/ROUTER.md`, storage/offline/architecture context and phase guide/report after verified behavior changes.
- Update the archive compatibility table whenever schemas, paths or payloads evolve.
- Preserve schemas and released archive contracts; never invent a prior shipped backup version from a synthetic compatibility fixture.
