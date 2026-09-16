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
last_updated: 2026-09-16
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

## Notifications

Use WorkManager or a one-shot local scheduling strategy for the daily summary. The job must not require network. Android 13+ requires runtime notification permission; denial should disable only the reminder, not the diary.

## Maps

MapLibre Native Android is the proposed renderer. Online sources must be used according to their terms. Offline regions must come from a source/process that permits offline packaging; standard OSM tile servers are not a source for bulk offline downloads.

## Distribution

Generate and sideload a debug/release APK locally for the zero-cost path. Play Store publication is a separate decision and may introduce account or publishing costs.

## F1 build and verification baseline

- The portable bootstrap pins JDK 17.0.20.1+1, Gradle 9.6.0, AGP 9.4.0, build tools 36.0.0 and compile SDK 37.2.
- The app targets API 37, supports API 26+, and runs automated managed-device tests on an API 36 Pixel 2 x86_64 image.
- `verifyFast`, `verifyDevice`, `verifyVisual` and `verifyAll` are the canonical Gradle gates.
- Compose UI tests use the v2 test rule; UI Automator proves launcher install/start; the visual gate compares a versioned home-screen color signature and captures the actual bitmap during the test.
- Generated output lives under the portable toolchain build root to avoid OneDrive locking; source and baselines remain in Git.
