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
last_updated: 2026-09-20
---

# Architecture

## System Overview

- User selects current location, a GPX/GeoJSON track, a searched place, or creates a manual observation.
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
- `:core:domain` is pure Kotlin and exposes clock, location and species-provider ports plus provenance-aware evidence types.
- `:core:testing` provides deterministic fake clock, location and provider implementations.
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

## F5 route boundary

- `:core:route` is a pure Kotlin module depending inward on `:core:domain`; Android document access remains in `:app`, and Room remains behind `RouteRepository`.
- GPX and GeoJSON parsing completes before persistence. Invalid input cannot write a route; raw files are neither stored nor uploaded.
- The engine uses ellipsoidal EPSG:3035 for the European pilot, geodetic distance sampling, metric corridor portions, 1 km cells and 5 km chunks.
- Geometry identity deduplicates stored routes independently of analysis configuration. Search fingerprints include geometry, radius, interval, precision and grid contract for future cache keys.
- Route persistence reuses the existing text column with a versioned segment payload and reads legacy flat arrays, so F5 does not require a Room schema migration.

## F6 occurrence boundary

- `:core:occurrence` is pure Kotlin and depends only on `:core:domain` and `:core:route`; it owns provider request shapes, bounded pagination, normalized records, retries and de-duplication.
- The gateway receives F5 corridor portions/chunks, not a raw GPX/GeoJSON document. GBIF selects polygons unless its configured vertex bound requires chunk bounding boxes; NNB WFS uses bounded query-chunk boxes.
- A record remains identified by `(provider, providerRecordId)`, preventing overlapping spatial requests from duplicating it without merging distinct providers' provenance.
- `OccurrenceGateway` returns a fresh cache, network records, or a clearly marked stale cache with provider failures. It never infers coordinates or precision absent from the provider response.
- `:core:local` schema v5 stores only normalized occurrence-cache records plus cached/expiry instants in one transactional entry. `FaunaviaApplication` composes GBIF and NNB adapters; result rendering remains F8.

## External Dependencies

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
