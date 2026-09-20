---
name: route-analysis
description: Route import, geometry normalization, sampling, and corridor construction for animal discovery.
triggers:
  - "GPX"
  - "GeoJSON"
  - "route"
  - "track"
  - "corridor"
  - "sampling"
edges:
  - target: context/architecture.md
    condition: when route behavior affects the end-to-end flow
  - target: context/data-and-provenance.md
    condition: when corridor geometry becomes a provider query
  - target: context/offline-first.md
    condition: when route files or analysis results are cached locally
last_updated: 2026-09-20
---

# Route analysis

The route engine accepts geolocation, GPX, and GeoJSON inputs and returns validated WGS84 geometry, length, bounding box, samples, and a corridor. It must not know about React, map rendering, or provider-specific response shapes.

## Rules

- Validate coordinate ranges, empty tracks, invalid segments, and relevant projection edge cases.
- Normalize multi-segment tracks without silently dropping geometry.
- Sample by distance, not by number of input points; deduplicate close samples.
- Keep the corridor radius explicit and configurable.
- Retain original input metadata and do not upload the raw file without a deliberate feature decision.

## Tests first

Cover a point location, one-segment track, multi-segment GPX, duplicate points, malformed file, and a route crossing a boundary relevant to the first geography.

## F0 spike baseline

- Pilot area: Lombardia; primary synthetic route: Parco Nord Milano.
- Comparison set: five synthetic routes covering urban park, canal, river/agriculture, pre-Alpine relief and dense urban context.
- Default corridor radius: 1 km, configurable; sampling interval: 500 m.
- Reproducibility grid: WGS84 0.01° cells grouped into 0.05° query chunks, with fixed origin and deterministic fingerprint.
- The degree-based grid deliberately over-covers the corridor and is not a production decision. F5 must compare it with EPSG:3035 and test boundary, distortion and antimeridian cases.
- Canonical fixture and expected output live under `f0/fixtures/routes/` and are verified by `npm --prefix f0 test`.

## F5 implementation

- `:core:route` accepts GPX tracks/routes, GeoJSON Point/LineString/MultiLineString/Feature/FeatureCollection, and a direct WGS84 point.
- Only consecutive exact duplicates are removed, with a structured warning. Segment boundaries are preserved in analysis and persistence and are never bridged.
- Sampling densifies each geodesic edge so adjacent samples are at most the configured interval; the default remains 500 m.
- The production pilot grid is EPSG:3035, with 1 km cells and 5×5-cell chunks. The default corridor radius remains 1 km and is configurable.
- A geometry hash identifies the stored route. A separate search fingerprint covers normalized geometry plus radius, sampling, precision and grid configuration.
- EPSG:3035 is intentionally bounded to Europe. Antimeridian and extra-European inputs return explicit errors; they never fall back silently to the provisional degree grid.
- Android reads a selected document locally and discards the raw content after parsing. Room stores normalized segments and source metadata in a versioned payload compatible with legacy flat route arrays.

## Verification status

- Thirteen deterministic F5 JVM tests cover valid and invalid formats, points, multiple segments, projection accuracy, density, corridor coverage, cells/chunks, fingerprints, deduplication and write atomicity.
- Android UI and persistence tests compile. On this host the UTP worker fails its loopback connection before instrumentation begins, so device/visual gates remain pending.
