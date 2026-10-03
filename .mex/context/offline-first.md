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
last_updated: 2026-10-03
---

# Offline-first

## F17 prepared outing materials and profile photographs

Five original factual outing summaries at three Lombardia public sites ship in the APK. Saved proposal metrics/access notes/species and separate source/coordinate provenance are durable outing payload v2; previous v1 outings remain readable in Room 10. Complete format-2 local backup includes these fields; offline reopening and links/deletion are tested. Public source pages require network, and the pilot contains no acquired trail geometry or regional map tiles. Existing/imported geometries can be viewed as a disconnected-segment offline schematic. This is the F17 local consultation boundary, not completion of F15 regional maps/gazetteer.

Wikipedia photos load on profile opening with exact identity and Commons license/author validation. First download requires network; 30-day normalized metadata and the existing bounded HTTP media cache may support later consultation but are expellable and excluded from diary backup. Stale metadata is labelled; missing/failed media retains the generic illustration and visible retry. Profiles and diary remain usable independently.

The F8B usability refinement allows online general-catalogue search from the diary and persists explicit taxon/alias selections. Provider failure returns the selected-local set with retry; recording an already selected species remains local. External Maps opening is optional and its failure never removes planning or memories. It does not add an offline Google map or a full taxonomy snapshot.

The MVP must show previously loaded routes, diary entries, source metadata, local photos and local assets without a network connection. It does not promise new biodiversity searches or a full offline map in the first build.

## Local backup

F12 exports the complete durable Room snapshot and private controlled photos through a user-selected SAF document; F14 extends it to format 2/schema 10 with personal GLB bytes/credits and still reads released format-1 schema-9 archives. Validation and restoration need no network once the archive is readable; a cloud document provider can require connectivity to supply/store that document. No Faunavia upload service is introduced. Cache provenance/expiry is preserved, so restoring a cache never makes stale evidence fresh.

The ZIP is unencrypted and contains personal notes/locations/images/model credits. Hashes check integrity, not author identity. Transient map/image/geocoder caches and in-flight analyses are not archived. Restoring replaces current durable data after a validated preview; creating a separate export beforehand is the way to retain both states. Images/models are appended under unique private paths before atomic row replacement, with old orphan cleanup deferred. Scheduling is recreated using restored preferences and current device permissions/timezone. See `GUIDA-FASE-12.md` and `GUIDA-FASE-14.md`.

## F14 offline illustrations

Bundled blackbird and copied personal models/credits work without network; no source document URI is retained as the durable model. Personal import requires self-contained resources and complete user-declared rights metadata, with a 100-model/20-MiB-per-file maximum. The viewer loads only when opened, never during mapping/research. Missing or corrupt bytes expose retry/removal and the 2D profile remains usable. General biology and illustrated animation do not alter direct evidence, plausibility, diary or taxon selection. Human approval and performance on a physical phone remain pending.

## F13 species consultation

Twelve versioned natural-history profiles ship inside the APK and work offline on first opening. Existing selected/local profiles stay in Room; presentation cache holds up to 64 viewed normalized profiles of 128 KiB each. The profile UI requires no new remote provider for curiosities. Source browsers and F9 distribution adapters keep their explicit network limits.

If local storage reading fails, use reviewed included content or a previously viewed copy with a warning/retry. A successful current read, even one without a profile after restoration, must not resurrect the old cached facts. Presentation cache is excluded from F12 ZIP; included profiles can be recreated from the APK, durable Room profile rows remain archived. Original vector and procedural generic 2D representations keep the detail usable without downloads or 3D.

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

F10 stores controlled photo copies/thumbnails in Android no-backup private files and their normalized metadata in Room 8. Save/view/remove and draft identification need no network once the selected image is locally readable. Picker providers may offer remote images; obtaining one depends on that provider, but Faunavia never uploads a copy. Original URIs, EXIF/GPS and full-resolution originals are not kept in the committed photo archive. Missing/corrupt copies expose a fallback without losing text. Completed images reopen after process restart; unfinished imports need re-selection and old unreferenced files are cleaned after 24 hours. F12 includes these controlled copies in its local archive.
