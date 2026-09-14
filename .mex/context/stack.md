---
name: stack
description: Proposed technology stack, library choices, and constraints.
triggers:
  - "library"
  - "package"
  - "dependency"
  - "technology"
edges:
  - target: context/decisions.md
    condition: when the reasoning behind a tech choice is needed
  - target: context/conventions.md
    condition: when understanding how to use a technology in this codebase
  - target: context/architecture.md
    condition: when a technology choice changes the system flow
  - target: context/offline-first.md
    condition: when choosing storage, caching, or installable-app behavior
grounds_to: []
last_updated: 2026-09-14
---

# Stack

## Core Technologies

- **Kotlin** — proposed primary language for Android and shared domain logic.
- **Jetpack Compose** — proposed Android UI toolkit.
- **Room/SQLite** — proposed local source of truth for diary, cache, catalogue and settings.
- **MapLibre Native Android** — proposed map renderer with a path to regional offline packages.
- **WorkManager + local notifications** — proposed background scheduling; use a precise alarm only if the UX requires it.

## Key Libraries

- **Kotlin serialization** (candidate) — parse GPX/GeoJSON/provider payloads at the boundary.
- **GBIF Species API** — candidate taxonomy autocomplete and accepted taxon identifiers; filter to Animalia.
- **Android Photo Picker** — select photos with the least invasive storage permission flow.
- **Filament/SceneView or equivalent** (candidate) — render GLB with an accessible 2D fallback.
- **JUnit + Kotlin test** (candidate) — unit tests for geometry, ranking, timezone and normalization.
- **Firebase Firestore** (optional) — structured sync only after the local-first MVP proves the need.

## What We Deliberately Do NOT Use

- No paid maps, hosted database, serverless subscription, cloud photo bucket, or paid AI asset generator as a prerequisite.
- No direct provider calls scattered through UI components; all external data goes through adapters.
- No automatic bulk download of OSM tiles or systematic geocoding queries.
- No framework-specific 3D authoring dependency: Blender files remain source assets, GLB is the delivery artifact.
- No Firebase dependency for local notifications; the daily summary is computed from Room on-device.

## Version Constraints

Node.js 22.5+ is required by MEX 0.8.1; the current environment uses Node 24. Android Studio/JDK and exact Android API targets are still to be locked after the first native spike.
