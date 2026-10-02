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
last_updated: 2026-10-02
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

## Notifications

Use WorkManager or a one-shot local scheduling strategy for the daily summary. The job must not require network. Android 13+ requires runtime notification permission; denial should disable only the reminder, not the diary.

## Maps

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
