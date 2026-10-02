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
last_updated: 2026-10-02
---

# Stack

## Core Technologies

- **Kotlin 2.3.21** — Compose compiler plugin and pure-JVM domain/testing modules; the Android module uses AGP 9.4 built-in Kotlin.
- **Jetpack Compose BOM 2026.08.00** — Android UI toolkit used by the F1 scaffold.
- **AndroidX Lifecycle ViewModel Compose 2.10.0** — F9 keeps analysis jobs/results across configuration changes; directly declared at the existing lifecycle version.
- **Room 2.8.4 / SQLite, KSP 2.3.6** — implemented local source of truth in `:core:local`; explicit mappers, schema history and migration tests.
- **Kotlin coroutines 1.10.2** — IO-dispatched suspend repository implementations; domain interfaces remain framework-independent.
- **Kotlin serialization 1.8.1** — aligned app/test runtime for Navigation and Room migration testing; AGP's consistent resolution otherwise combines core 1.7.3 with test JSON 1.8.1.
- **MapLibre Native Android 13.6.1 OpenGL** — F8A map renderer behind an adapter; OkHttp 4.12.0 supplies an identified tile User-Agent and HTTP cache. Regional offline packages remain future work.
- **WorkManager + local notifications** — proposed background scheduling; use a precise alarm only if the UX requires it.

## Key Libraries

- **Provider/route payload parsing** — F5 GPX/GeoJSON, F6 occurrence and F8A Nominatim adapters normalize externally supplied data outside the UI; Room mapping remains in `:core:local`.
- **GBIF Species API** — candidate taxonomy autocomplete and accepted taxon identifiers; filter to Animalia.
- **GBIF Maps v2 + Wikimedia Commons/MediaWiki imageinfo + Wikidata Action API** — F9 internal distribution presentation: historical observation density, attributed reusable range illustrations and exact P225/P181 association. Separate from F7 range evidence and accepted taxonomy selection; no new backend or account.
- **Nominatim** — F8A explicit-submit place search for country/region/city with bounded results, rate limit and local cache; no client-side network autocomplete.
- **OSRM Route API v1** — F8B user-triggered driving routes from two confirmed endpoints, explicitly authorized for the personal prototype. Public demo: one request/second maximum, no uptime/traffic guarantee. Full chosen geometry/provenance is copied into the trip; no new package or paid API is needed.
- **Android Photo Picker** — select photos with the least invasive storage permission flow.
- **Filament/SceneView or equivalent** (candidate) — render GLB with an accessible 2D fallback.
- **JUnit 4.13.2 + AndroidX Test** — JVM, Compose, UI Automator and managed-device verification.
- **Firebase Firestore** (optional) — structured sync only after the local-first MVP proves the need.

## What We Deliberately Do NOT Use

- No paid maps, hosted database, serverless subscription, cloud photo bucket, or paid AI asset generator as a prerequisite.
- No direct provider calls scattered through UI components; all external data goes through adapters.
- No automatic bulk download of OSM tiles or systematic geocoding queries.
- No framework-specific 3D authoring dependency: Blender files remain source assets, GLB is the delivery artifact.
- No Firebase dependency for local notifications; the daily summary is computed from Room on-device.

## Version Constraints

Node.js 22.5+ is required by MEX 0.8.2; the current environment uses Node 24. F1 pins Temurin JDK 17.0.20.1+1, Gradle 9.6.0, AGP 9.4.0, Android build tools 36.0.0, compile SDK 37.2, target SDK 37, min SDK 26 and a managed Pixel 2 on API 36 x86_64.
