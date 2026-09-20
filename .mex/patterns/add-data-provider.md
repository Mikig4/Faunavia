---
name: add-data-provider
description: Add or change a biodiversity provider adapter without leaking provider details into the app.
triggers:
  - "add provider"
  - "GBIF"
  - "iNaturalist"
  - "occurrence API"
edges:
  - target: context/data-and-provenance.md
    condition: always, because source metadata and evidence semantics are part of the adapter contract
  - target: context/route-analysis.md
    condition: when the provider query consumes a corridor or sample geometry
last_updated: 2026-09-20
---

# Add a data provider

## Context

Read `context/data-and-provenance.md` and `context/route-analysis.md`. The adapter must convert provider records to the internal occurrence model and preserve provenance.

## Steps

1. Confirm API, license, attribution, authentication and rate limits from primary documentation.
2. Define the smallest bounded query that answers the corridor request.
3. Add an adapter behind the provider interface and map errors to retry/stale/empty states.
4. Store provider ID, query timestamp, license, coordinate precision and source URL.
5. Add fixtures for success, empty result, malformed response, rate limit and outage.
6. Update source/decision documentation before presenting the integration as complete.

## Gotchas

- Let the adapter choose its spatial shape. F6 passes corridor portions and query chunks, never raw route files; GBIF can use polygons or a deterministic bbox fallback, while NNB WFS uses bounded bboxes.
- De-duplicate only by provider plus provider record ID. Do not collapse records from different providers merely because date, taxon or coordinates happen to match.
- Preserve a provider's generalized coordinate and uncertainty exactly. Missing precision stays missing; no estimated point or radius may be constructed.
- Cache normalized records under the F5 fingerprint and adapter set. A fresh cache avoids a network call, and a failed refresh returns stale records with an explicit failure list.
- Keep online smoke checks outside deterministic gates. `verifyOccurrenceSmoke` reports only status/count and must not persist response payloads.

## Verify

- [ ] Provider calls exist only in the adapter.
- [ ] No API key or secret is committed.
- [ ] Attribution and license are visible in the result.
- [ ] Retry behavior is bounded and failure is tested.
- [ ] Fresh-cache, stale-cache, pagination and cross-portion duplication are tested with fakes/fixtures.
