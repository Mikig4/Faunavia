---
name: data-and-provenance
description: Biodiversity providers, occurrence normalization, evidence semantics, licensing, privacy, and source attribution.
triggers:
  - "GBIF"
  - "iNaturalist"
  - "occurrence"
  - "license"
  - "provenance"
  - "evidence"
edges:
  - target: context/architecture.md
    condition: when adding or changing the biodiversity gateway or evidence engine
  - target: context/route-analysis.md
    condition: when a provider query depends on corridor geometry
  - target: context/decisions.md
    condition: when changing what the app claims about presence
  - target: context/conventions.md
    condition: when implementing adapters, normalization, or source rendering
last_updated: 2026-09-14
---

# Data and provenance

Provider adapters are the only place that knows remote API parameters. They return internal occurrence records with provider, source ID, timestamp, license, coordinate precision, and raw-query metadata. UI code consumes normalized records and evidence summaries.

## Evidence semantics

- `documented`: records in or near the corridor meet configured quality and recency thresholds.
- `plausible`: habitat/season/taxonomy support the species, but direct records are weak or absent.
- `insufficient`: the app cannot justify showing a stronger claim.

These labels are product semantics, not biological certainty. Every ranking reason must be inspectable.

## Privacy and sensitive data

Prefer bounding boxes or reduced precision when querying external services. Do not expose exact locations for sensitive species merely because a provider returns them. Store external source IDs rather than full responses when possible, and make cache deletion explicit.

## Manual observations

Manual observations are first-party diary records, not provider occurrences. They must reference a selected accepted taxon, and may contain a local photo, note, count, and local timestamp. They must remain distinguishable from imported evidence and must not automatically promote a common urban animal into the curated suggestion list.

## Taxonomy catalogue

The general selector searches the adopted taxonomy by common name, scientific name, and synonyms, then stores the accepted taxon ID and source/version metadata. The general catalogue is broader than the curated regional suggestion set. A taxonomy snapshot may be cached locally later, but the MVP may use online autocomplete plus caching of selected taxa.

Taxon search may show a lazy photo preview from an occurrence-media source. Preview metadata must include creator, rights holder, license, source URL, and cache expiry. Missing or non-reusable media must fall back to a placeholder without blocking taxon selection.

## Optional Firebase boundary

Firestore may later synchronize structured records, regional suggestion profiles, and catalogue versions. Cloud photo storage is not assumed in a zero-cost design; if it is introduced, review the current Firebase billing requirement and security rules first. No provider is allowed to become the only copy of the user's diary.
