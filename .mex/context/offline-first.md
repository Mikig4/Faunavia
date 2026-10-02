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
last_updated: 2026-10-02
---

# Offline-first

The F8B usability refinement allows online general-catalogue search from the diary and persists explicit taxon/alias selections. Provider failure returns the selected-local set with retry; recording an already selected species remains local. External Maps opening is optional and its failure never removes planning or memories. It does not add an offline Google map or a full taxonomy snapshot.

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

F8A caches confirmed place candidates in Android preferences for 30 days, normalized occurrence results in the F6 Room cache for six hours, and ordinary viewed OSM tiles in an HTTP cache. These caches do not promise full offline geocoding or a new biodiversity search. An offline map failure leaves the textual result and local diary available; stale occurrence results are visibly marked.

F8B persists trips, chosen full route geometry/provenance, outings, chosen places/results and unidentified drafts in Room independently of those caches. OSRM calculations require network; its ten-minute cache is distinct from the durable confirmed trace. Saved traces reopen without a routing request; base tiles remain ordinary HTTP cache, not a regional offline package. A chosen result keeps original period/area, saved date, evidence sources and partial/stale flags; changing scope requires explicit recalculation. Offline provider or tile failure leaves planning, saved explanations and textual memories available. Deleting planning only unlinks memories; failed draft conversion rolls back atomically. Regional packages and backups remain later phases.

F10 stores controlled photo copies/thumbnails in Android no-backup private files and their normalized metadata in Room 8. Save/view/remove and draft identification need no network once the selected image is locally readable. Picker providers may offer remote images; obtaining one depends on that provider, but Faunavia never uploads a copy. Original URIs, EXIF/GPS and full-resolution originals are not kept in the committed photo archive. Missing/corrupt copies expose a fallback without losing text. Completed images reopen after process restart; unfinished imports need re-selection and old unreferenced files are cleaned after 24 hours. Backup/export remains F12.
