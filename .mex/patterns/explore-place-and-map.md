---
name: explore-place-and-map
description: Change or debug F8A place search, period exploration, MapLibre and list fallback without weakening provenance or offline behavior.
triggers:
  - "F8A"
  - "Nominatim"
  - "MapLibre"
  - "place confirmation"
edges:
  - target: context/data-and-provenance.md
    condition: when changing geocoding, occurrence sources or evidence labels
  - target: context/android-local.md
    condition: when changing map, cache, lifecycle or Compose restoration
  - target: patterns/import-route-and-analyze.md
    condition: when changing coordinate, GPX or GeoJSON analysis
grounds_to: []
last_updated: 2026-09-30
---

# Explore a place and render a map

## Context

F8A is an unsaved exploration path through F5/F6/F7. A chosen administrative place yields a user-confirmed point sample with at most 20 km radius; it does not query the entire country or region. The public Nominatim service prohibits client-side autocomplete, so only an explicit submit triggers a network request.

## Steps

1. Keep external endpoint/query parsing and the one-request-per-second limit in `:core:exploration`'s geocoder adapter. Return canonical name/type/country/center/bounds; keep ambiguity, empty result and outage separate.
2. Ask for explicit user confirmation before creating an ephemeral point route. The selected period and radius must validate before gateway access. GPX/GeoJSON may be opened again after process restoration using its retained URI/read grant; a provider may require re-selection.
3. Pass F5 `RouteAnalysis` to F6 `OccurrenceGateway`, then each grouped taxon to F7. Never label a species plausible without both range and habitat evidence, and never imply a historical occurrence is current presence.
4. Keep provider URLs outside app Kotlin UI source; `scripts/quality/check-boundaries.ps1` enforces this. Put map tile URLs in the style asset and geocoder URLs in the pure adapter.
5. MapLibre reads ordinary online tiles with identifiable User-Agent, cache and visible OSM attribution. Do not prefetch for offline regions. Draw route/corridor/samples and aggregate evidence, not precise occurrence coordinates. Keep cards and diary available if map/tiles/network fail.

## Gotchas

- Equivalent GPX, GeoJSON and coordinate geometry shares one F5 fingerprint and F6 cache; a fake provider can legitimately be called once for three analyses.
- The UI's lazy list needs `performScrollToNode(hasTestTag(...))` and a closed keyboard for off-screen Compose controls.
- A provider-call counter does not prove saved-state restoration: it may still reflect the pre-restore request. After restoration, wait for the result title and assert the restored map and filtered card again.
- `verifyAll` treats lint's KTX suggestions as errors. Use `String.toUri()` and `SharedPreferences.edit { ... }` when appropriate.
- In a restricted shell, the wrapper may attempt a blocked Gradle download or Java may report `AccessDeniedException` for an installed toolchain JAR. Use the pinned local Gradle binary and request out-of-sandbox execution rather than weakening gates.

## Verify

- [ ] Pure tests cover unique/ambiguous/empty/unavailable geocoder results, encoding, rate limit and cache.
- [ ] E2E fixture covers GPX, GeoJSON and coordinates through fingerprint, fake provider and F7 level/season.
- [ ] Compose tests cover place confirmation, filters, saved routes, state restoration, fallback and attribution.
- [ ] Visual test checks versioned map/list/error signatures; real MapLibre smoke does not require tile availability.
- [ ] `verifyAll --no-daemon` passes and the Android result XML lists every declared test.

## Update Scaffold

- [ ] Update `.mex/ROUTER.md` Current Project State and relevant `context/` files when behavior changes.
- [ ] Record provider policy changes from primary sources before changing request behavior.
