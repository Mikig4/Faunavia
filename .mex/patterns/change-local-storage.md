---
name: change-local-storage
description: Evolve Room entities, repository behavior and migration tests without losing diary data.
triggers:
  - "Room"
  - "migration"
  - "local repository"
edges:
  - target: context/android-local.md
    condition: when changing database behavior
  - target: context/conventions.md
    condition: when verifying changes
last_updated: 2026-09-16
---

# Change local storage

## Context

`:core:domain` owns models and suspend repository contracts. `:core:local` owns Room entities, DAO, mappers and repository implementations. The application creates one lazy repository container with a real clock; tests inject `FakeClock`.

## Steps

1. Inspect the current schema JSON in `core/local/schemas/it.faunavia.local.FaunaviaDatabase` before editing entities. Keep old exported schemas immutable.
2. Keep domain models free of Android/Room imports. Preserve provenance and instant precision in mappers.
3. Bump the database version for schema changes and register a non-destructive migration in the production builder. The initial F2 v1 fixture predates the settings time zone; v2 adds `zoneId` with deterministic `UTC` default. F1 shipped no database.
4. Use repository transactions for related writes. Use `@Upsert`, not replacement inserts, for taxa so child data survives updates.
5. Extend `LocalPersistenceTest` and `LocalMigrationTest` in the app instrumented suite. Include populated migration, raw SQL constraints and reopening the file-backed database.
6. Run `verifyAll` after sourcing `scripts/android-env.ps1`. In the restricted Windows environment, use the documented short temporary directory when Java loopback fails.

## Gotchas

- Foreign keys alone cannot enforce accepted Animalia status. Triggers protect observation inserts/updates and referenced taxon changes; install them on creation, migration and opening. Room schema JSON does not include custom triggers: test their behavior explicitly.
- Taxon deletion is restricted while the diary references it. Observation deletion cascades photo metadata and returns removed references. F10 owns physical photo-file lifecycle; F2 performs no file deletion or upload.
- Store timestamps as ISO instants to preserve nanoseconds and query local calendar days using indexed epoch seconds and zone-aware day boundaries. DST days are not always 24 hours.
- Missing reads return null/empty, missing observation updates fail, and missing deletes are idempotent. Invalid references and storage errors propagate to the caller; future UI must render them.
- Never enable destructive migration fallback to make a test pass.
- Keep serialization runtime aligned in the app and instrumentation APKs. Room migration testing brings JSON 1.8.1, while AGP can constrain core to the app's older 1.7.3. The app explicitly depends on JSON 1.8.1 to avoid `GeneratedSerializer.typeParametersSerializers` linkage errors. Check `dependencyInsight --single-path` before changing this pin.
- Use `runBlocking<Unit>` for expression-bodied JUnit coroutine tests. A final `assertThrows` otherwise infers an exception return type, and the Android runner can silently omit the class. `verifyDevice` compares declared test names with result XML and fails for missing tests.

## Verify

- CRUD and mapper round-trips, provenance, empty repositories and missing references.
- Null/blank/missing/non-Animalia/unaccepted observation taxa rejected by database constraints.
- Transaction rollback, independent photo metadata deletion, restricted taxon deletion and cascades.
- Populated database reopening, populated/empty migration and both DST transitions.
- Full F0–F2 regression, navigation and visual golden.

## Debug

Read the app managed-device XML/HTML reports under the external build root. For migration errors, compare exported schema JSON and SQL; for invalid relationships, inspect foreign keys and triggers rather than disabling them.

## Update Scaffold

Update the router and Android storage context after verified behavior changes; keep the old schema history and the phase report accurate.
