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
last_updated: 2026-10-03
---

# Data and provenance

## F17 outing facts and Wikipedia media

The bundled Lombardia pilot contains original factual summaries of official visitor routes/sites, with separate itinerary/access/fauna sources consulted 2026-10-03. Source article prose, photos, PDFs and maps are not copied. Public Brabbia/Vanzago reference points retain OSM identifiers/ODbL and explicitly unspecified metric precision; the Sebino Wikidata P625 center retains CC0 and stated angular precision. Representative centers are not entrances or sensitive animal sites. Unavailable geometry is never fabricated; published partial closures and guided-access requirements remain visible. Site fauna documentation and recommended months do not establish current presence, probability or F7 level.

Wikipedia PageImages supplies the main filename only after unique exact P225 identity and Wikipedia pageprops item verification. The Italian article precedes English. Commons imageinfo must supply reusable license, author and official HTTPS media/description URLs; unsafe/ambiguous/unknown-rights content has a fallback. Original source date, author, license, query/version and article URL remain in normalized metadata. Live smoke verifies `Turdus merula` item Q25234, `Turdus_merula_Nesting.jpg`, CC BY-SA 3.0 and thumbnail HTTP 200; test fixtures are separate synthetic images. See `scripts/verify-species-photo-smoke.ps1` and `artifacts/f17-wikipedia-smoke.json`.

## Common-name catalogue and planned routes

- GBIF `/species/suggest` is scientific autocomplete, not reliable Italian common-name search. The common-name path is `/species/search?qField=VERNACULAR`, restricted to the GBIF Backbone and Animalia; then scientific autocomplete if no selectable common candidates exist. Search records use `taxonomicStatus` and `vernacularNames[]`; suggest uses `status`. Normalize both and resolve accepted synonym keys.
- Prefer an Italian common name and exact displayed-name matches before foreign aliases; the live query `merlo` also matches the Spanish fish name, so alphabetical sorting alone is misleading. Keep scientific name/ID/provenance visible. GBIF live smoke validates the shape; fixture-only success cannot establish live lookup correctness.
- OSRM receives only the confirmed departure/destination coordinates after explicit calculation. The user authorized this external destination. Store the complete selected geometry with source, retrieval date, ODbL attribution and the provided map version (or explicitly missing version); do not invent traffic accuracy or replace errors with straight lines. Google Maps directions receive only endpoints on click and may differ from the saved trace.

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

## F9 compact species presentation

- The approved main research view selects typical taxa from existing evidence via the versioned twelve-profile pilot. Urban-common/unreviewed taxa are absent only from this selection; the complete evidence view and diary stay available. Scientific identity and original evidence objects are retained; counts never determine typicality.
- Shared research/trip/suggestion cards show names, general habitat and season only; evidence explanations, attribution and licences are disclosed on demand. Italian curated names are presentation labels, never an accepted-taxonomy shortcut.
- Habitat opens an internal distribution dialog for every taxon. Reviewed pilot Commons files remain preferred; non-pilot images require exactly one Wikidata item with matching P225 scientific identity, then its P181 map claim. Commons imageinfo supplies permitted media URL, author, reusable licence and map date, with the original legend retained. Images load only on click, with bounded 32 MiB HTTP cache, 4 MiB response and sampled bitmap limits; metadata cache contains normalized records only. No range geometry is imported into F7. GBIF Maps v2 provides an explicitly separate aggregate historical-observation layer on OSM, never range boundaries or encounter probability. No IUCN feed is integrated. Online smoke summaries are `artifacts/f9-fixes-live.json` and `artifacts/f9-fixes-tile.json`.
- Common Italian names outside the pilot use exact accepted Animalia species/subspecies identity in GBIF. Foreign names and fuzzy matches cannot fill the Italian label. Missing names retain an immediate visible scientific title. This separate presentation cache never persists an implicitly selected taxon in Room, and retains full source/date/licence/quality/version on stale fallback.
- The F9 refinement deferred curiosities to F13 as a clickable section with individual sources and missing-data fallback; F13 is now implemented below. Primary natural-history sheets (parks, Lipu) are prioritized; Wikipedia/Wikidata remain complementary candidates. There is no dedicated automatic curiosity provider; verify text/image reuse separately before implementing one.

## F13 natural-history facts

`PilotNaturalHistory` version `natural-history-2026-10-03-v1` contains twelve original short factual profiles sourced primarily from Lipu (nine birds) and Parco Nazionale Gran Paradiso (three mammals), consulted 2026-10-03. Each field and curiosity retains all eight Provenance fields and its specific source URL; consultation time is not an event/publication date. Scientific identity matching keeps subspecies distinct. No unsupported curiosity is generated and no source paragraph, photograph or page structure is imported.

Lipu's source license is CC BY-NC-ND 4.0 with photographs excluded; the Parco pages do not indicate a verified text/image reuse license. These constraints stay explicit in provenance. Source inconsistencies are omitted, not silently repaired (e.g. garzetta wingspan header and picchio-nero length variants). Conservation notes describe pressures/tutela, not automatic current IUCN labels. The generic original 2D symbol is labelled non-identifying and licensed separately in the asset manifest.

Facts, migration descriptions and maps do not become evidence of local presence. The compact card's F7 historical-season signal remains distinct from the detail's general season/migration field. Existing result `Evidenze e fonti` still owns the travel-specific explanation; viewing cannot change its classification or create a personal sighting.

## Optional Firebase boundary

Firestore may later synchronize structured records, regional suggestion profiles, and catalogue versions. Cloud photo storage is not assumed in a zero-cost design; if it is introduced, review the current Firebase billing requirement and security rules first. No provider is allowed to become the only copy of the user's diary.
