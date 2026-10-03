---
name: android-local
description: Android-native implementation choices for Room, photos, notifications, MapLibre, offline map packages, and APK distribution.
triggers:
  - "Kotlin"
  - "Jetpack Compose"
  - "Room"
  - "MapLibre"
  - "APK"
  - "Android"
  - "offline map"
edges:
  - target: context/stack.md
    condition: when selecting Android libraries or SDK versions
  - target: context/offline-first.md
    condition: when deciding what is cached or packaged on the device
  - target: context/observations-notifications.md
    condition: when implementing diary photos or local reminders
  - target: context/architecture.md
    condition: when Android platform behavior changes a component boundary
last_updated: 2026-10-03
---

# Android local architecture

## Local source of truth

Room/SQLite stores structured data. Photos are files in app-private storage with metadata in Room. The APK can bundle a small curated catalogue and GLB assets; larger regional map packages are separate, versioned imports.

## F2 storage implementation

- Room 2.8.4 with KSP 2.3.6 lives in `:core:local`; schemas v1/v2 are versioned. Migration 1→2 adds the settings time zone with `UTC` as the deterministic legacy default. No destructive migration fallback is configured.
- Catalogue, preview, species/suggestion profiles, source evidence, routes, diary, photo metadata and settings persist locally with explicit domain/Room mapping.
- All repository writes are transactions on an IO dispatcher. Clock injection controls creation/update timestamps; observed instants retain nanoseconds and their recorded zone.
- Local-date queries use start-of-day boundaries in the requested zone, including 23/25-hour DST days.
- Missing records return null or empty lists. Updating a missing observation or saving an invalid taxon reference fails. Missing deletes are idempotent.
- Deleting a taxon used by the diary fails. Deleting an observation cascades photo metadata and returns removed references; independent photo deletion leaves the observation intact. Physical photo management remains F10.
- Managed-device tests in the app cover CRUD, raw SQL integrity, rollback, mapper round-trips, database reopening and populated/empty migration. Schema copying precedes test asset merging.

## F3 taxonomy cache

- Schema v3 adds `taxon_aliases(taxonId, name, normalizedName)`, with a foreign key cascade and an index on the normalized search name. The v2→v3 migration preserves all existing catalogue and diary records.
- The catalogue persists an accepted Animalia taxon and all its aliases atomically only after the person selects it. Local SQL search checks scientific name, common name and aliases, filters accepted Animalia, and orders species/subspecies first.
- The app declares the Internet permission for the GBIF adapter. The catalog UI is still useful without a connection because it falls back to selected local taxa and makes the offline state explicit.
- A debug-only empty activity hosts injected Compose search states in instrumented tests; it is excluded from release builds.

## F4/F5 local flows

- Schema v4 adds `ObservationRow.quantity` with default one; the Diary UI performs offline create/edit/delete with a required selected taxon.
- F5 keeps the Room schema at v4. `RouteRow.points` remains a text column but now contains a versioned JSON object with source, source filename and nested segments; the mapper still reads legacy flat point arrays.
- Android's document picker grants temporary read access. The app reads at most 5 million characters, parses off the main thread, stores only normalized geometry and does not retain the URI or raw file.
- The Percorsi screen exposes configurable radius/sampling, manual WGS84 coordinates, explicit validation states and offline summaries. Its saved routes can now be selected in Risultati.
- F8A adds MapLibre OpenGL 13.6.1 behind `ExplorationMapAdapter`, an OSM raster style asset, HTTP cache/User-Agent, route/corridor/sample overlays and an always-visible attribution under the map. A failed/missing tile does not hide the list or diary. The regional offline map package remains F15.
- F8A geocoding uses `NominatimPlaceSearch` in the pure exploration module on explicit submit only, bounded to five results and one request/second, with a 30-day Android preferences cache. User confirmation is required. Country/region analysis is a bounded 20 km sample. The offline regional gazetteer remains F15.
- An OpenDocument URI is retained in saveable screen state and a persistable read grant is requested for process restoration; providers that do not grant one require re-selection after process death. No raw document is stored as a trip in F8A.
- `RouteTestActivity` hosts deterministic Compose tests and remains debug-only.

## F6 occurrence cache

- Schema v5 adds `occurrence_cache(key, cachedAt, expiresAt, occurrences)`. The payload contains normalized records and their provenance, not raw provider responses or route files.
- `LocalRepositories.occurrenceCache` offers explicit read, save, keyed delete and full clear operations; cache expiry is evaluated in the pure gateway so stale data cannot silently look current.
- The v4→v5 migration is additive. Instrumented tests cover empty migration/cache access and a file-backed mapper round trip; provider fixtures and gateway tests run off-device.

## F8B planning and memory storage

- The usability refinement changes no Room schema. Viaggi is the app start with four primary destinations; exploration and route import are optional tools. Destination/date-only creation uses a generated name and the existing radius default, while custom name/radius/interests are optional.
- The custom bottom navigation must apply `navigationBarsPadding`; unlike Material NavigationBar, its LazyRow does not consume those insets automatically. Scaffold paints the reserved status area green and content stays light. API 27+ overrides the base theme with a pale system navigation bar and dark icons; API 26 uses green with light icons. Manual API 36 review caught the prior overlap.
- Date conversion in the trip form and exploration uses `instant.atZone(zone).toLocalDate()` for minSdk 26; `LocalDate.ofInstant` requires newer Android APIs without desugaring.
- Use `URLEncoder.encode(value, "UTF-8")` (or the encoding name) in pure JVM dependency modules too: the Charset overload requires API 33 and app lint did not detect the dependency call. The static boundary gate now rejects it.
- Trip JSON payload v3 retains Room schema 6 and reads v1/v2 with missing stage/departure/route fields. Ordered stages and each complete trace/provenance survive reopening; legacy snapshot keys are preserved. `TripStagesForm` saves small draft fields only in the instance bundle, retaining existing routes only when both endpoints still match. Changed origins/destinations invalidate affected selected legs; stale in-flight responses cannot restore them. MapLibre fits full or selected-stage bounds; explicit per-leg calculations reuse the OSRM port and its process rate limit/cache.
- Diary reuses the taxonomy port online and falls back to selected local taxa; selecting a new entry persists aliases/provenance before local diary writes. Maps URLs are built outside UI, and Android launches them only on click with a visible failure state.
- Schema v6 adds `trips`, `outings`, `saved_trip_places`, `saved_trip_results` and `unidentified_drafts`. Migration 5→6 adds nullable indexed diary links and preserves legacy diary/photo/settings/cache data, including timestamp precision.
- Planning rows use explicit JSON mappers with full place provenance, copied route segments and chosen-result original dates/area/evidence. Saved-result `outingId` is read from the row so SQLite `SET NULL` is honored after an outing is deleted.
- Trip deletion unlinks both memory links before cascading planning data; outing deletion retains the trip link. Raw SQL membership/identity triggers protect both observations and drafts; conversion deletes the draft before insertion within a single rollback-safe transaction.
- Viaggi and Diary use injected repository ports. Save failures retain editor fields; saveable editor identity/confirmed place waits for existing Room data after restoration, preventing an edit from becoming a new record.
- `PersonalMapAdapter` shares MapLibre lifecycle handling with exploration but draws only confirmed personal points. No external occurrence coordinate is used as the personal location; no line connecting observations is invented.
- The original F8B adds 5 domain JVM, 10 persistence, 11 Compose UI and 1 migration tests, with cumulative Android count 54. Its first usability refinement passed 73 JVM and 60 Android tests, plus 13 F0 (historical report `21 - Rifinitura Viaggi e Diario.md`). The clarified departure/full-route/catalogue extension passed the complete gate with 82 JVM, 66 Android and 13 F0, no test failures or skipped tests, lint, boundaries and omission detection. Three visual goldens cover home, exploration and trips/draft; signatures are not full pixel comparisons. Live API 36 checks confirmed “merlo” and a saved Milano–Como route; no physical-phone test was performed. Final APK `artifacts/Faunavia-f8b-viaggi-debug.apk` is 0.8.2-f8b (11); see `22 - Tracciato viaggio e Catalogo.md`.

The 2026-10-02 staged-itinerary extension passed the cumulative gate: 13 F0, 84 JVM and 70 Android tests, no failures/skips, lint, boundaries, omission detection and three visual signatures. New coverage includes v2 compatibility, stage persistence/deletion without diary loss, ordering/date constraints, retry, stage Maps endpoints and stage-day analysis. APK `artifacts/Faunavia-f8b-tappe-debug.apk` is 0.8.3-f8b (12). These new UI tests use fakes on API 36; no physical-device claim. See `23 - Tappe e giorni del viaggio.md`.

## F9 wishlist and profile versioning

Room schema 7 adds `wishlist(taxonId, addedAt)` with a restricted taxon foreign key and accepted-Animalia integrity triggers. Repeated adds preserve the first timestamp; removing a wish leaves taxon, diary and planning intact. Migration 6→7 adds the profile schema version and marks old profiles as read-only v0, preserving even empty habitats. New v1 profiles require a nonempty habitat and reason; curation is bundled in the pure exploration module.

Catalogue explicit selections and trip suggestions expose wishlist actions. Never-observed reads global identified memories; errors preserve local records and expose retry, restoration preserves the selected view, and saved trip candidates require the current analysis key. Final F9 verification passed 13 F0, 93 JVM and 82 Android tests, lint, formatting, boundaries, omission detection and three existing visual signatures. APK `artifacts/Faunavia-f9-debug.apk` is 0.9.0-f9 (13); signature matches the F8B staged-itinerary build. See `24 - Rapporto Fase 9.md` for limits and wish/suggestion-specific future extensions.

## F10 image storage

Room schema 8 migrates 7→8 additively, preserving diary/wishlist/draft data and legacy photo identities with unknown dimension defaults. `draft_photos` has a cascading foreign key to its draft; conversion transfers all photo metadata in the same transaction and retains file paths. Photo Picker uses Activity 1.12.3; EXIF parsing uses AndroidX ExifInterface 1.4.2, avoiding the older platform parser flagged by lint.

`noBackupFilesDir/photos` contains only private normalized JPEG pairs and temporary input during import. Bounds: 32 MiB/100 MP source, sampled 2048-pixel output, 320-pixel thumbnail, 48 MiB reserve. Re-encoding removes GPS/date/camera metadata; all eight orientations are applied to pixels and persisted as normal orientation. SHA-256/length validate the main copy before display. Original picker URIs need no permanent grant once the copy is committed.

Metadata is written after complete files; failed Room writes remove new files. Metadata deletion precedes file deletion, exposing incomplete cleanup. A shared mutex and 24-hour grace protect referenced/recent files during orphan recovery. Gallery state/jobs survive Activity recreation; committed copies reopen from Room after process restart, while interrupted imports require explicit re-selection. Tests include real UI Automator picker selection with synthetic MediaStore images, actual rotated-image pixels, Activity recreation, migration, reopening and failure/rollback cases. See `GUIDA-FASE-10.md`; F12 now exports and restores these controlled copies.

## F11 notification scheduling

F11 introduced schema 9: migration 8→9 adds a daily delivery ledger without changing any existing row. WorkManager 2.12.0 uses unique 15-minute periodic work with initial delay, KEEP for reopening and CANCEL_AND_REENQUEUE for time/zone changes. Its boot restoration is supplied by the library; the app receiver handles TIME_SET/TIMEZONE_CHANGED and onCreate/onResume reconcile persisted settings. Runtime permission and notification channel blocks prevent posting; disabling leaves the diary intact. Notification intents are immutable and retain date/zone. No network constraint or exact alarm is used. See `GUIDA-FASE-11.md`.

## F12 backup storage

SAF `CreateDocument`/`OpenDocument` transfers a format-1 ZIP without broad storage permission. `manifest.json` inventories `database.json` and every referenced controlled image/thumbnail with size and SHA-256. The data snapshot covers all 19 schema-9 tables, preserving provenance, stages, links, wishes, settings and daily deliveries. Schema 8 input is validated with its absent ledger empty; no prior shipped archive exists.

Import stages a bounded complete ZIP through `ZipFile`, checks manifest/versions/paths/files/hashes/counts, validates a separate Room database and image dimensions, then shows a replacement preview. Photos are copied and synced into a fresh private directory before one live Room transaction rewrites only their paths and replaces rows. Existing files are never overwritten; failures before commit leave their references untouched. Repository/photo generations reject queued stale mutations, and reminder settings serialize with restore. A fresh Activity task clears unsaved UI state; WorkManager is reconciled after commit, respecting the receiving device's permissions/timezone.

Limits: 2 GiB ZIP, 10,000 image files including thumbnails, 64 MiB database payload, 100,000 rows and 32 MiB per image. Import requires the staged ZIP plus three times declared uncompressed data and 64 MiB reserve. Archives are not encrypted; raw picker URIs, platform permissions, tile caches, online metadata caches and unfinished analyses are excluded. Interrupted staging and old nested orphan files have a 24-hour cleanup grace. See `GUIDA-FASE-12.md` and `patterns/backup-and-restore.md`.

## F13 viewed profiles

The F13 baseline retains schema 9 and format-1 backup with the same 19-table inventory; F14 evolution is documented below. Existing flat `SpeciesProfile` rows are projected into sourced presentation fields without changing their payload. Opening a profile never saves a taxon or personal evidence. A storage sentinel compares durable snapshots before/after viewing, then round-trips an existing local profile and diary through the local archive.

`species-profiles-v1` SharedPreferences contains at most 64 normalized viewed profiles, each <=128 KiB (<=8 MiB worst-case payload). Commit failure surfaces as a retry warning; decoded identity/schema/provenance are checked. Included/local profiles are available offline. A successful local read always beats old cache content, including after restore; cache-only fallback is visibly unverified when Room reading fails. Presentation preferences are excluded from backup, while durable profile rows remain included.

The UI exposes headings, explicit unavailable states, labelled generic 2D fallback, saveable curiosity/source disclosures and a fixed close action. The native Dialog content retains parent density; the large-text test measures rendered TextLayoutResult font scale 1.8. Automated tests cover long accented text, semantics, source-open failure and four versioned visual signatures. Final `verifyAll`: 13 F0, 123 JVM, 144 Android, no failures/errors/skips/omissions. No new library, physical-device or actual TalkBack session was used.

## F14 personal GLB storage and rendering

Current schema is 10: additive migration 9→10 adds `personal_models` with domain-checked credits/import date/hash/path/size. Association is to the displayed taxon identity, separate from selected catalogue rows; importing an illustration cannot create a diary taxon. Private no-backup UUID model directories are published complete before metadata and recovered after the photo-style 24-hour grace. File import, replacement/removal and restoration share the photo mutex/generation.

Format-2 ZIP adds exact referenced model bytes/provenance to all 20 durable tables, still accepting released format-1/schema-9 input and synthetic schema-8 compatibility. Limits remain 2 GiB archive/10,000 resource files/64 MiB JSON/100,000 rows; models additionally have 20 MiB/file and 100 associations maximum. Staging checks structure and Filament resources; fresh paths are committed with rows, failed restore removes only newly published copies. A legacy backup has an empty model library and replaces the current one after preview/confirmation.

Filament 1.77.1 renders only when the shared profile's model dialog is opened. Vulkan is preferred on advertised hardware support, otherwise OpenGL; lifecycle pause stops callbacks, detach releases native ownership exactly once. Model dialog visibility/controls belong to the profile's composition so saveable state survives restoration, including a selected paused/playing clip. Actual native pixels/touch/disposal, invalid files, migration, SAF and backup are covered by F14 tests. Physical-device performance and human asset approval remain pending; see `GUIDA-FASE-14.md`.

## Maps (existing F9 boundary)

F9 correction 0.9.2-f9 uses lifecycle ViewModel Compose 2.10.0 (the lifecycle version already pinned/transitively present). `AnalysisViewModel` retains exploration/trip live results and IO work across Activity recreation, avoiding restart or a large saved-state bundle. Tests recreate real Activities with completed and blocked-in-flight providers and assert a single call; trip tests wait for Room loading before inspecting restored rows. Explicit scope changes clear/cancel results. Process death still relies on saved inputs, F6 cache and explicit Room snapshots, not retained live results.

The green Scaffold now sets `contentColor = onBackground`, and essential title/scientific/season text uses `onSurface` on light cards. Semantics alone missed the invisible white-on-light title; a bitmap ink test covers the regression. `SpeciesDistributionDialog` uses native image zoom for attributed Commons illustrations and the shared MapLibre lifecycle adapter for global aggregate GBIF density. All taxa have the action; missing maps and network/image errors remain recoverable. SharedPreferences stores at most 256 normalized metadata entries (30-day TTL); Commons has a bounded HTTP image cache. Room remains schema 7; automatic metadata lookup does not select taxonomy records.

MapLibre Native Android is the F8A renderer, isolated from F5/F6/F7 behind an Android adapter. Online sources must be used according to their terms. Offline regions must come from a source/process that permits offline packaging; standard OSM tile servers are not a source for bulk offline downloads.

## Distribution

Generate and sideload a debug/release APK locally for the zero-cost path. Play Store publication is a separate decision and may introduce account or publishing costs.

## F1 build and verification baseline

- The portable bootstrap pins JDK 17.0.20.1+1, Gradle 9.6.0, AGP 9.4.0, build tools 36.0.0 and compile SDK 37.2.
- The app targets API 37, supports API 26+, and runs automated managed-device tests on an API 36 Pixel 2 x86_64 image.
- `verifyFast`, `verifyDevice`, `verifyVisual` and `verifyAll` are the canonical Gradle gates. F8A adds `:core:exploration:test` and a second versioned visual golden for map/list/error.
- Compose UI tests use the v2 test rule; UI Automator proves launcher install/start; the visual gate compares a versioned home-screen color signature and captures the actual bitmap during the test.
- Generated output lives under the portable toolchain build root to avoid OneDrive locking; source and baselines remain in Git.
- The Windows launcher/build JVM settings are synchronized for in-process `--no-daemon` execution. Test workers inherit `jdk.net.unixdomain.tmpdir=NUL`, which triggers the JDK TCP fallback when Unix-domain sockets are restricted; `GUIDA-GRADLE-LOOPBACK.md` records the recovery command.
