---
name: offline-first
description: Local persistence, cache behavior, Android storage, privacy, map packages, and the boundary between cached data and true offline analysis.
triggers:
  - "offline"
  - "cache"
  - "Android"
  - "Room"
  - "SQLite"
  - "local storage"
  - "geolocation"
edges:
  - target: context/architecture.md
    condition: when network availability changes the system flow
  - target: context/setup.md
    condition: when testing the APK or local storage on a device
  - target: context/android-local.md
    condition: when choosing Room, photo storage, MapLibre offline regions, or APK packaging
  - target: context/decisions.md
    condition: when choosing between cached results, downloadable datasets, and a backend
last_updated: 2026-09-14
---

# Offline-first

The MVP must show previously loaded routes, diary entries, source metadata, local photos and local assets without a network connection. It does not promise new biodiversity searches or a full offline map in the first build.

## Cache rules

- Cache normalized data with provider timestamp and TTL.
- Mark stale results visibly; never silently present them as current.
- Keep raw route input local by default.
- Provide clear storage reset controls.
- Treat geolocation as permissioned, foreground-only input until tracking is explicitly requested.
- Keep photos in app-private storage and support manual export/import before considering cloud backup.
- Deliver the daily summary from local data; network is not a prerequisite for notification.

True offline analysis requires a deliberately packaged dataset and an offline-capable map source. A regional map package may be added later; standard OSM tile servers must not be bulk-downloaded for offline use. Treat this as a product decision, not as a side effect of adding a cache.
