---
name: architecture
description: How the major pieces of this project connect and flow.
triggers:
  - "architecture"
  - "system design"
  - "integration"
  - "flow"
edges:
  - target: context/stack.md
    condition: when specific technology details are needed
  - target: context/decisions.md
    condition: when understanding why the architecture is structured this way
  - target: context/route-analysis.md
    condition: when implementing route import, sampling, or corridor geometry
  - target: context/data-and-provenance.md
    condition: when implementing provider adapters or evidence ranking
  - target: context/assets-3d.md
    condition: when implementing the species catalogue or 3D asset loading
  - target: context/observations-notifications.md
    condition: when implementing manual observations, photos, daily summaries, or notifications
  - target: context/android-local.md
    condition: when implementing Android storage, Room, MapLibre, or offline packages
grounds_to: []
last_updated: 2026-10-02
---

# Architecture

## System Overview

- User starts with confirmed departure/destination, dates and a chosen trace. Optional ordered stages have destinations, days and chosen leg geometry; exploration without a trip, imported tracks and manual coordinates remain optional tools.
- The route engine validates coordinates, samples the geometry, and builds a configurable corridor.
- Provider adapters query bounded areas; the local store caches raw metadata and normalized records.
- The evidence engine deduplicates species, scores recency/distance/quality, and preserves provenance.
- The UI renders map, corridor, curated peculiar-species suggestions, evidence labels, sources, diary entries, and species details.
- A lazy-loaded GLB model enriches a species profile; every profile also has a 2D fallback.
- Room/SQLite stores the diary, local photos, cache, catalogue and notification settings.
- A local scheduler creates a daily summary notification only when the user entered observations that day.

## Key Components

- **Android UI** — user flow, map, filters, species cards, detail views and diary; depends on route/evidence/observation interfaces.
- **Route engine** — GPX/GeoJSON/geolocation normalization, sampling and corridor geometry; independent of UI.
- **Geographic search adapter** — resolves country/region/city names to canonical places and point/bbox/polygon geometry; provider-specific geocoding stays outside UI and domain.
- **Biodiversity gateway** — provider adapters with bounded queries, caching and provenance; first candidate is GBIF.
- **Evidence engine** — taxonomy normalization, deduplication and explainable evidence levels.
- **Local store and asset registry** — Room/SQLite records, local photo references, and versioned GLB/2D assets.
- **Taxonomy catalogue** — common/scientific-name autocomplete, synonym resolution and accepted Animalia taxa.
- **Suggestion engine** — curated regional profiles with distinctiveness and urban-common exclusion rules.
- **Notification scheduler** — timezone-aware local daily summary; no server or FCM dependency.

## Implemented F1 module boundary

- `:app` owns Android, Compose navigation and platform tests.
- `:core:domain` is pure Kotlin and exposes the clock, current repository/model contracts and provenance-aware evidence types. Unused F1 location/species ports and summary were removed after F10.
- `:core:testing` provides `FakeClock`; obsolete standalone location/provider fakes were removed.
- `:core:network` owns blocking JSON GET; typed provider clients retain timeout, size, status/error and User-Agent policies.
- A static boundary gate rejects Android imports in the domain and provider URLs in UI source.

## F2 local persistence boundary

- `:core:domain` defines nine local models, complete provenance and suspend contracts for diary, catalogue, routes and settings.
- `:core:local` owns Room entities, DAO, explicit mappers and IO-dispatched transactional repositories. It depends on the domain; the domain never imports Room or Android.
- `FaunaviaApplication` lazily creates the local repository container with the system clock. F2 adds no diary UI; F3/F4 will consume the contracts.
- SQLite foreign keys and triggers enforce accepted Animalia references even for direct SQL writes. Deleting a referenced taxon is rejected; deleting an observation cascades photo metadata.
- Database schema v2 and a non-destructive v1-to-v2 migration are exported under `core/local/schemas`; v1 is an initial F2 fixture, not a database shipped by F1.

## F3 taxonomy boundary

- `:core:taxonomy` owns the taxonomy-provider port, GBIF Species v1 adapter, query normalisation, accepted-Animalia filter, rank ordering, duplicate removal and controlled in-memory suggestion expiry.
- The adapter maps common/scientific names and a suggested synonym to its accepted GBIF record. It preserves source, query, timestamp, license, attribution, quality and API version on the resulting `Taxon`.
- `:core:local` schema v3 adds an indexed `taxon_aliases` table. A selected accepted taxon and all searchable names are written transactionally; only selected taxa are read while offline.
- `FaunaviaApplication` composes the Room-backed selection store, GBIF adapter and search service. Compose calls the service on IO after a 350 ms debounce; no provider URL appears in UI source.
- F3 deliberately does not copy provider media. Each result presents an explicit unavailable-preview placeholder, so missing or unlicensed media never blocks selection.

## F4 diary boundary

- The Compose Diary reads and writes only through `DiaryRepository`; incomplete taxon searches remain transient UI state.
- Schema v4 adds observation quantity with a non-destructive v3→v4 migration and keeps manual records separate from external evidence.
- Post-F10, `parseDiaryInput` shares observation/draft validation; `rememberTaxonomyLookup` shares catalogue/diary search lifecycles. Provenance JSON conversion is shared in `:core:local`, preserving Room 8 and stored payloads.

## F5 route boundary

- `:core:route` is a pure Kotlin module depending inward on `:core:domain`; Android document access remains in `:app`, and Room remains behind `RouteRepository`.
- GPX and GeoJSON parsing completes before persistence. Invalid input cannot write a route; raw files are neither stored nor uploaded.
- The engine uses ellipsoidal EPSG:3035 for the European pilot, geodetic distance sampling, metric corridor portions, 1 km cells and 5 km chunks.
- Geometry identity deduplicates stored routes independently of analysis configuration. Search fingerprints include geometry, radius, interval, precision and grid contract for future cache keys.
- Route persistence reuses the existing text column with a versioned segment payload and reads legacy flat arrays, so F5 does not require a Room schema migration.

## F6 occurrence boundary

- `:core:occurrence` is pure Kotlin and depends on `:core:domain`, `:core:route` and `:core:network`; it owns provider request shapes, bounded pagination, normalized records, retries and de-duplication.
- The gateway receives F5 corridor portions/chunks, not a raw GPX/GeoJSON document. GBIF selects polygons unless its configured vertex bound requires chunk bounding boxes; NNB WFS uses bounded query-chunk boxes.
- A record remains identified by `(provider, providerRecordId)`, preventing overlapping spatial requests from duplicating it without merging distinct providers' provenance.
- `OccurrenceGateway` returns a fresh cache, network records, or a clearly marked stale cache with provider failures. It never infers coordinates or precision absent from the provider response.
- `:core:local` schema v5 stores only normalized occurrence-cache records plus cached/expiry instants in one transactional entry. `FaunaviaApplication` composes GBIF and NNB adapters; result rendering remains F8.

## F8A exploration boundary

- `:core:exploration` is pure Kotlin and connects F5 route analysis, F6 gateway and F7 assessments without requiring a stored trip. It also owns the Nominatim place-search port/adapter; Android owns only the bounded preferences cache and explicit-confirmation UI.
- `FaunaviaApplication` composes the explorer and geocoder. Risultati can use a confirmed place, coordinate, imported file or saved local route, plus a selected period and radius. F5 fingerprints reuse F6 cache across equivalent geometries.
- `ExplorationMapAdapter` isolates MapLibre from data logic. The map renders route, corridor and samples but not exact occurrence points. The list carries evidence levels, calculation steps and provenance; absent institutional range/habitat cannot become `plausible`.

## F8B trip and memory boundary

- Viaggi is the launch destination; primary navigation contains Viaggi, Diario, Catalogo and Impostazioni. New trips confirm departure/destination and dates, calculate driving choices, preview/select the full route on MapLibre, and persist it. Optional exploration/import stay reachable; name defaults to the destination and radius/interests/manual coordinates are disclosed on demand.
- `TripRouting` and the OSRM adapter live in pure `:core:exploration`. The user authorized sending the two endpoints to the public demo on explicit calculation, limited to one request/second with transient cache; chosen route geometry/provenance is durable. Changed endpoints invalidate unsaved choices and obsolete responses are ignored. No straight-line fallback is invented.
- `mapsDirectionsUrl` and `mapsSearchUrl` live in pure `:core:exploration`; Android opens directions for two endpoints or a legacy/outing point after a click, catching missing activity/security failures. No API key, date, complete route or diary content is included. Google Maps recalculates directions; it cannot return the selected trace through the link.
- Diary catalogue lookup reuses F3 with a 350 ms debounce, error/retry and selected-local fallback. Explicit selection persists the accepted taxon and aliases before the editor accepts it; observation writes remain local. Failed selection retains fields and never creates an observation. Selected prefills require no lookup to save.
- Pure domain models and repository ports separate saved planning, evidence snapshots, identified observations and unidentified drafts. `PersonalDiaryViews` derives filters/calendar/located map points and global personal firsts from identified memories only.
- `:core:local` schema v6 adds planning/draft tables and nullable diary links without rebuilding observations. SQLite triggers validate trip/outing membership, unlink memories on planning deletion and prohibit one identity in both memory tables.
- Trip payload v3 adds ordered `TripStage` entries with date, destination and copied `TripRoute` to the v2 departure/trace fields; v1/v2 remain readable and Room stays at schema 6. Combined geometry preserves every stage segment. `stageScope` uses only the selected leg and its day; whole-trip analysis uses the full corridor/date interval. Legacy analysis keys remain unchanged. Saved results retain nullable stage IDs; removed/changed stages mark snapshots outdated without deleting them or diary memories. Saved traces reopen without OSRM; large unsaved traces do not enter instance-state bundles.
- `UnidentifiedRepository.convert` deletes the draft and creates the accepted-Animalia observation in one transaction, preserving ID and original creation time; failure rolls back both operations.
- Android Viaggi composes existing F5–F8A services, confirms destinations and explicitly recalculates after scope changes. Saved results retain original period/area, explanation and provenance independently of provider cache expiry. Outings copy imported geometry rather than depend on the original route row.
- The shared essential species card may prefill an accepted taxon and planning links, but never external occurrence coordinates. The user confirms actual observation date/location. `PersonalMapAdapter` draws only located personal memories; unlocated entries stay in lists and calendar.

## F7 plausibility boundary

- `:core:plausibility` depends on `:core:domain` and the normalized F6 occurrence contract; it contains no Android or Compose code. F8 will compose it after an explicit area/period selection.
- `PlausibilityEngine` calls a record `documented` only when it is recent, dated, located and within the configured uncertainty threshold. Without usable direct evidence, both an intersecting reporting range and compatible habitat are required for `plausible`.
- Article 12/17 reporting ranges, MAES associations and raw CLCplus samples remain adapters. CLCplus is translated only by a separately sourced, scientifically curated crosswalk; 2021/non-equivalent samples and uncurated mappings yield `insufficient`.
- Institutional monthly season windows override a bounded lower-quality monthly signal derived from dated GBIF/NNB records. Natura 2000 contributes positive context only; observability guidance is separate from evidence level.

## F9 personal discovery boundary

- Pure `:core:exploration` owns the versioned pilot curation and deterministic `PersonalSuggestionEngine`. Inputs are existing evidence candidates, analysis points, accepted personal diary taxa and wishlist taxa. Suggestions cannot reclassify presence or derive ease from record counts.
- `:core:domain` adds `WishlistEntry`/`WishlistRepository` and an explicit suggestion-profile schema version; `:core:local` schema 7 adds wishlist storage with migration 6→7. Existing profiles remain readable as legacy version 0, including missing habitats; new curated profiles require version 1 with nonempty habitat. Legacy profiles cannot enter the typical view or be rewritten without curation. Foreign-key restrictions and accepted-Animalia triggers preserve selected identities.
- Catalogue handles explicit taxonomy selection before wishlist writes. Trips exposes four personal views for whole-trip/stage/outing context and reuses original live evidence or only still-valid saved snapshots; stale/partial states remain visible. Diary writes and personal sighting confirmation remain independent of curation.

F9 refinement 2026-10-02: `typicalTaxa` reuses the pure suggestion engine on existing complete evidence and analysis samples; both research surfaces default to this selection without deleting evidence or snapshots. Twelve profiles and `PilotSpeciesPresentation` provide reviewed general habitat/common-name/distribution presentation. Android `SpeciesDetails` shares compact cards and saveable evidence disclosure, with visible scientific fallback. `RemoteSpeciesMetadata` in pure exploration converts GBIF exact-name metadata and Wikidata/Commons range illustrations into normalized presentation types; Android owns bounded cache/image decoding and the internal map dialog. All species can use it; native GBIF observation density is distinct from illustrated ranges and neither feeds F7. `AnalysisViewModel` retains analysis jobs/results across configuration changes; it does not persist live results after process death. Room remains schema 7. F13 explicitly requires a clickable curiosity section, with individual sources and reuse review before content import.

## F10 private photo boundary

- Pure domain photo metadata adds dimensions, normalized orientation and optional thumbnail path while retaining legacy defaults. Unidentified repository photo ports share the stable memory ID without relaxing accepted-Animalia observation constraints.
- Room 8 adds draft-photo foreign keys and additive observation-photo columns. Draft conversion copies metadata into observation photos within its existing transaction before a successful commit; failed conversion rolls back cascades. Planning deletion only unlinks memories.
- Android owns `PrivatePhotoStore`, `MemoryPhotos` and `PhotoGalleryModel`: bounded input/bitmap IO, EXIF orientation, metadata-free encoding, private no-backup files, checked hashes, explicit errors, temporary cleanup and grace-period orphan recovery. No provider/upload dependency or photo-gallery permission is introduced. Text is saved before attachment; file/DB ordering protects existing memories. Committed photos survive process recreation; ongoing jobs survive configuration changes. Backup remains F12.

## F11 local reminder boundary

- Pure domain `DailyReminderPolicy` computes eligibility and initial delay using an explicit Instant/ZoneId. `DailyReminderRepository` exposes one serialized local delivery operation.
- Room 9 adds only `daily_summary_deliveries(date, zoneId, notifiedAt)`. Existing diary/photo/planning/settings tables and all earlier migrations remain unchanged. The worker queries identified records for the current local day; drafts and provider evidence are excluded.
- Android `ReminderScheduler`, `ReminderPreferences`, `ReminderRunner`, `DailyReminderWorker` and `DailySummaryNotifications` own persistent periodic scheduling, runtime/channel permission, time-change reconciliation and system posting. MainActivity consumes valid summary extras both on cold start and onNewIntent; saveable navigation retains the requested date/zone. Settings and summary use only local repositories.
- Durable date ledger and stable per-date notification tags reduce duplicate effects; the cross-system crash gap is explicit. Scheduling is flexible, without exact alarms, server or network dependency. Backup remains F12.

## External Dependencies

AndroidX ExifInterface 1.4.2 supplies maintained local EXIF parsing. Android BitmapFactory decodes supported still-image pixels, and AndroidX Activity selects only explicitly chosen images, falling back to the system document picker where necessary.

- **GBIF API** — primary occurrence search candidate; bounded requests, caching and source metadata required.
- **OpenStreetMap tiles/geocoding** — map context only; use attribution, rate limits and no bulk/offline tile prefetch.
- **iNaturalist API** — optional secondary observation source after licensing and rate-limit review.
- **Blender/glTF/GLB** — free asset authoring and local delivery format; no hosted asset service is required.
- **Firebase/Firestore** — optional future sync for structured data only; not required by the MVP.

## What Does NOT Exist Here

- No user accounts, social features, or multi-device synchronization in the MVP.
- No turn-by-turn navigation or guaranteed real-time animal presence.
- No mandatory cloud backend, paid API, or paid asset-generation service.
- No automated photo/audio species identification in the first release.
- No cloud photo upload in the MVP.
