---
name: import-route-and-analyze
description: Implement or debug the flow from location/GPX/GeoJSON to a biodiversity result corridor.
triggers:
  - "import route"
  - "GPX"
  - "GeoJSON"
  - "corridor analysis"
  - "route sampling"
edges:
  - target: context/route-analysis.md
    condition: always, because geometry validation and sampling define the query area
  - target: context/data-and-provenance.md
    condition: when the corridor is sent to an occurrence provider
  - target: context/offline-first.md
    condition: when route files or results are persisted locally
last_updated: 2026-09-14
---

# Import route and analyze

## Steps

1. Accept one input source and report its type and validation errors.
2. Normalize to WGS84 and preserve original metadata.
3. Remove only exact duplicates or invalid points with an explicit warning.
4. Compute length, bounding box, samples and configured corridor.
5. Query through the biodiversity gateway and persist a cacheable result.
6. Render map geometry and evidence summaries separately from raw records.

## Verify

- [ ] Empty, malformed and multi-segment files produce clear errors.
- [ ] A route with no stops still returns samples and a corridor.
- [ ] The same fixture produces deterministic geometry.
- [ ] Results show source timestamp and evidence level.
