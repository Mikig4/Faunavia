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
last_updated: 2026-09-30
---

# Data and provenance

Provider adapters are the only place that knows remote API parameters. They return internal occurrence records with provider, source ID, timestamp, license, coordinate precision, and raw-query metadata. UI code consumes normalized records and evidence summaries.

## Evidence semantics

- `documented`: records in or near the corridor meet configured quality and recency thresholds.
- `plausible`: documented range and habitat jointly support the species while usable direct records are absent; season is a separate confidence modifier.
- `insufficient`: the app cannot justify showing a stronger claim.

These labels are product semantics, not biological certainty. Every ranking reason must be inspectable.

## Privacy and sensitive data

Prefer bounding boxes or reduced precision when querying external services. Do not expose exact locations for sensitive species merely because a provider returns them. Store external source IDs rather than full responses when possible, and make cache deletion explicit.

## Manual observations

Manual observations are first-party diary records, not provider occurrences. They must reference a selected accepted taxon, and may contain a local photo, note, count, and local timestamp. They must remain distinguishable from imported evidence and must not automatically promote a common urban animal into the curated suggestion list.

## Taxonomy catalogue

The F3 selector uses the GBIF Species API v1 behind `:core:taxonomy`. It normalizes common/scientific/synonym queries, resolves a suggested synonym to its accepted GBIF record, filters selectable Animalia and preserves the stable `gbif:<id>` identifier with source/version metadata. The GBIF documentation says Backbone identifiers remain supported in its API even as its primary taxonomy moves to Catalogue of Life XR; a future provider upgrade can choose a checklist without invalidating saved IDs.

The MVP does not bundle a global taxonomy snapshot. Room caches only explicitly selected taxa and their searchable aliases; remote suggestions are held in memory for five minutes and never replace selected records. Offline mode returns the local selections with a visible state.

F3 does not fetch or copy occurrence media. It shows an explicit unavailable-preview placeholder, so selection does not imply a media license. A later preview provider must retain creator, rights holder, license, source URL and cache expiry before replacing that placeholder.

## F6 occurrence gateway

- `:core:occurrence` contains the provider-neutral `DocumentedOccurrence` contract. It preserves provider/record identity, scientific name, provider-supplied date, location, uncertainty, source URL and complete `Provenance`; it is not a claim of current presence.
- GBIF uses bounded pages (at most 300 records per request) and polygon corridor portions, with deterministic fallback to F5 query-chunk bounding boxes. NNB uses its WFS fallback with bounded boxes and `startIndex` pagination.
- The gateway retries only timeout/network/429/5xx failures, at most three attempts with exponential backoff or a provider `Retry-After`; malformed and other 4xx responses fail immediately. Partial records retain provider failures; all-provider failure returns stale cache explicitly when available.
- The persistent cache lasts six hours and is keyed by F5's configuration-sensitive fingerprint plus the active adapter set. It stores normalized responses only and offers explicit deletion; the UI must render `fresh`, `network` or `stale` state in F8.
- GBIF records retain their individual licence and dataset attribution. NNB WFS does not expose a per-record licence, so its stored licence says so and must not be treated as general commercial/public reuse permission.

## F7 plausibility engine

- `:core:plausibility` produces `documented`, `plausible` or `insufficient` together with stable calculation steps and complete source records. A direct occurrence is usable only if it has a precise ISO date, location and acceptable age/uncertainty; this preserves a historical record without treating it as current presence.
- In the absence of usable direct evidence, an Article 12/17 range must intersect and the land-cover samples must match a preferred or suitable MAES association. Adjacent ranges, occasional associations, missing sources, non-equivalent CLCplus input and uncurated translations do not promote a taxon.
- Institutional monthly windows take precedence. Only when none exists may dated GBIF/NNB occurrences offer a lower-quality empirical monthly signal; absence from those records never reduces confidence because it may be sampling bias.
- Natura 2000 has a positive-context-only model. Documented habitat/period/time-of-day guidance is distinct from evidence; missing guidance remains unavailable rather than an invented encounter probability.

## F0 provider baseline

- GBIF polygon occurrence search, GBIF taxonomy match, EEA Article 12/17 envelope queries, the EEA MAES 2014 archive and CLCplus 2021 point identify returned usable bounded fixtures.
- NNB GeoAPI returned HTTP 503; NNB WFS returned data and is the explicit fallback. The lack of per-record licensing metadata prevents assuming public/commercial reuse.
- The discoverable CLCplus point service used in F0 is 2021. A guessed 2023 ImageServer returned 404; 2021 remains visibly dated and non-equivalent.
- CLCplus classes are retained raw. No direct CLCplus-to-MAES mapping is accepted until scientific curation; without it, habitat evidence is `insufficient`.
- Provider snapshots, source registry, query limits, normalization examples and fallback outcomes are versioned in `f0/fixtures/` and replayed offline with `npm --prefix f0 test`.

## Geographic search contract

- F8 accepts country, region and city names in addition to coordinates and imported route geometry.
- The geocoder adapter returns a canonical display name, place type, country code and point/bounding box/polygon geometry; the user confirms before a naturalistic query starts.
- Online lookup is bounded, attributed, cached locally and never systematic; ambiguous, empty or unavailable responses remain explicit UI states.
- A regional offline gazetteer is deferred to F15 and must be versioned with its source and license.
- F8A implements public Nominatim as an explicit-submit adapter, not client-side autocomplete: maximum five candidates, one request/second per process, identified User-Agent, 30-day local cache and OSM/Nominatim attribution. Empty, ambiguous and unavailable states differ. A country/region result is confirmed as a 20 km maximum sample around its center, not full territorial coverage.
- A single live query for “Milano, Italia” returned an omonymous locality as its first candidate; successful HTTP status does not prove the top hit is the intended city. Keep the complete canonical display name visible and require confirmation instead of automatically accepting the first result.
- F8A result cards retain the source link, attribution, date, license, quality and an Italian plain-language rendering of the F7 calculation trace. The map shows only route/corridor/samples and area-level evidence counts, never exact external occurrence pins. Range/habitat data are not yet fed live, so a taxon lacking a usable direct record remains `insufficient`.

## Optional Firebase boundary

Firestore may later synchronize structured records, regional suggestion profiles, and catalogue versions. Cloud photo storage is not assumed in a zero-cost design; if it is introduced, review the current Firebase billing requirement and security rules first. No provider is allowed to become the only copy of the user's diary.
