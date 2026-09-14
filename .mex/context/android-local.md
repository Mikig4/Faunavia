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
last_updated: 2026-09-14
---

# Android local architecture

## Local source of truth

Room/SQLite stores structured data. Photos are files in app-private storage with metadata in Room. The APK can bundle a small curated catalogue and GLB assets; larger regional map packages are separate, versioned imports.

## Notifications

Use WorkManager or a one-shot local scheduling strategy for the daily summary. The job must not require network. Android 13+ requires runtime notification permission; denial should disable only the reminder, not the diary.

## Maps

MapLibre Native Android is the proposed renderer. Online sources must be used according to their terms. Offline regions must come from a source/process that permits offline packaging; standard OSM tile servers are not a source for bulk offline downloads.

## Distribution

Generate and sideload a debug/release APK locally for the zero-cost path. Play Store publication is a separate decision and may introduce account or publishing costs.
