---
name: discover-naturalist-outings
description: Extend the bounded F17 public outing catalogue, ranking and durable planning snapshots.
triggers:
  - "F17"
  - "outing discovery"
  - "sentieri"
edges:
  - target: context/data-and-provenance.md
    condition: always, before curating sources or coordinates
  - target: patterns/plan-trip-and-record-memory.md
    condition: when changing outing storage or diary links
  - target: patterns/backup-and-restore.md
    condition: when changing durable outing payload fields
last_updated: 2026-10-03
---

# Discover naturalist outings

## Context

`OutingDiscovery` lives in pure exploration; `OutingGuide`/`OutingSpecies` are domain snapshots. `PilotOutingCatalogue` includes five original factual summaries at three public sites. Room 10 stores outing payload v2 and still reads v1; backup format 2 includes it without new tables. Regional map/gazetteer F15 and sync decision F16 remain separate.

## Steps

1. Verify primary public-access, itinerary and fauna sources. Preserve separate itinerary/access/species/coordinate provenance. Summarize facts in original text; do not copy prose, photographs, PDFs or cartography without reusable rights.
2. Record only documented metrics, season recommendations and access restrictions. Missing months mean unspecified, not all-year presence. Missing difficulty/accessibility remain unknown. Distance to the reference point is geodesic, never transfer time.
3. Use verified public visitor/reference coordinates, documenting their precision and whether they are an actual entrance or representative center. Exclude sensitive locations. Do not generate a walking trace from biodiversity occurrences.
4. Filter by destination radius/date/interests and exact accepted wishlist identities. Stable tie-breaking uses proposal ID. Curation cannot change F7 evidence or create a diary record.
5. Save an immutable copy with stable trip/proposal/day identity. A repeat click opens the existing snapshot; source changes/outages must not replace it. Different days can have different outing IDs. Keep original route segments; never bridge them.
6. Include new fields in the bounded domain mapper, keeping v1 readable and rejecting unsupported payload versions. Changing an outing’s place/route removes the now-unrelated guide; name/date edits preserve it. Existing backup staging must validate the new fields.

## Gotchas

- A route description is not an authorized route geometry. The pilot deliberately has no trace; show the official start label and explicit absent geometry.
- Torbiere central routes have a published partial safety closure; WWF Vanzago uses guided visits. Preserve these facts and source dates instead of assuming future access.
- Modal source buttons use `ProfileSource` and `LocalProfileSourceOpener`. Lazy content needs `performScrollToNode` and a closed keyboard in Compose tests.
- Public geographic point sources and species sources are different; preserve both, including OSM ODbL or Wikidata CC0.

## Verify

- Deterministic `OutingDiscoveryTest`: coverage, dates/interests/wishes, unknown metrics, stable sorting, sensitive exclusions, repeat identity, original segments.
- `F17DiscoveryUiTest`: save/reopen/restoration, linked draft, missing duration/invalid date, outage/retry and preserved old snapshot.
- `F17PersistenceTest`: file-backed reopen, no implicit taxon, links/deletion, v1 and invalid v2.
- `F12BackupTest` complete ZIP round-trip must retain the guide and all its source fields after reopening.
- Finish with unfiltered `verifyAll`, omission detection and existing visual signatures.

## Update Scaffold

Update Router, offline/data/architecture context, report and guide; retain F14 manual-review and F15/F16 boundaries.
