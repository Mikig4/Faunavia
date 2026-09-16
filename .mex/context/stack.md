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
last_updated: 2026-09-16
---

# Stack

## Core Technologies

- **Kotlin 2.3.21** — Compose compiler plugin and pure-JVM domain/testing modules; the Android module uses AGP 9.4 built-in Kotlin.
- **Jetpack Compose BOM 2026.08.00** — Android UI toolkit used by the F1 scaffold.
- **Room 2.8.4 / SQLite, KSP 2.3.6** — implemented local source of truth in `:core:local`; explicit mappers, schema history and migration tests.
- **Kotlin coroutines 1.10.2** — IO-dispatched suspend repository implementations; domain interfaces remain framework-independent.
- **Kotlin serialization 1.8.1** — aligned app/test runtime for Navigation and Room migration testing; AGP's consistent resolution otherwise combines core 1.7.3 with test JSON 1.8.1.
- **MapLibre Native Android** — proposed map renderer with a path to regional offline packages.
- **WorkManager + local notifications** — proposed background scheduling; use a precise alarm only if the UX requires it.

## Key Libraries

- **Provider/route payload parsing** — adapter-specific codecs remain future work; the F2 storage mapper uses Android JSON only inside `:core:local`.
- **GBIF Species API** — candidate taxonomy autocomplete and accepted taxon identifiers; filter to Animalia.
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
