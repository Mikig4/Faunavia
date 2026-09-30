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
last_updated: 2026-09-30
---

# Import route and analyze

## Steps

1. Accept one input source and report its type and validation errors.
2. Read at most the configured local size limit; never retain or upload the raw file by default.
3. Normalize to WGS84 and preserve source type, source filename and every segment boundary.
4. Remove only consecutive exact duplicates with an explicit warning; reject invalid coordinates before persistence.
5. Validate the EPSG:3035 pilot area, then compute length, bounding box, geodetic samples and configured metric corridor.
6. Emit stable 1 km cells, 5 km chunks, a geometry identity and a configuration-sensitive search fingerprint.
7. In Percorsi, persist only after parsing and analysis succeed; a duplicate geometry reuses the existing route. In F8A Risultati, analyze an ephemeral route without persistence unless the person selects a previously saved route.
8. Query through the biodiversity gateway in F6 and render map geometry separately through the F8A adapter. A matching fingerprint reuses the normalized occurrence cache.

## Verify

- [ ] Empty, malformed and multi-segment files produce clear errors.
- [ ] A route with no stops still returns samples and a corridor.
- [ ] The same fixture produces deterministic geometry.
- [ ] Reimporting the same geometry under another filename or radius does not duplicate the stored route.
- [ ] Changing radius or sampling changes the search fingerprint.
- [ ] Extra-European and antimeridian routes fail explicitly instead of using a hidden projection fallback.
- [ ] Results show source timestamp and evidence level.
