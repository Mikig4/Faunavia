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
last_updated: 2026-09-14
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
- **Biodiversity gateway** — provider adapters with bounded queries, caching and provenance; first candidate is GBIF.
- **Evidence engine** — taxonomy normalization, deduplication and explainable evidence levels.
- **Local store and asset registry** — Room/SQLite records, local photo references, and versioned GLB/2D assets.
- **Taxonomy catalogue** — common/scientific-name autocomplete, synonym resolution and accepted Animalia taxa.
- **Suggestion engine** — curated regional profiles with distinctiveness and urban-common exclusion rules.
- **Notification scheduler** — timezone-aware local daily summary; no server or FCM dependency.

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
