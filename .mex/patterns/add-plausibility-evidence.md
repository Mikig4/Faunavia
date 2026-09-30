---
name: add-plausibility-evidence
description: Change F7 range, habitat, season, context or observability evidence without overstating a species' presence.
triggers:
  - "plausibility"
  - "range evidence"
  - "habitat evidence"
  - "seasonality"
edges:
  - target: context/data-and-provenance.md
    condition: always, because evidence semantics and source provenance are part of the output contract
  - target: patterns/add-data-provider.md
    condition: when a new remote adapter or provider response is involved
grounds_to: []
last_updated: 2026-09-20
---

# Add plausibility evidence

## Context

Load `context/data-and-provenance.md`, `context/architecture.md` and `context/conventions.md`. The pure `:core:plausibility` module receives normalized F6 occurrences and source-specific range/habitat data; Compose must not implement the classification itself.

## Steps

1. Preserve a source's record ID, query, retrieval time, licence, attribution, quality and version in `Provenance`.
2. Add provider endpoints only in `institutional-adapters.kt`; make malformed, rate-limited, unavailable and network outcomes explicit.
3. Retain raw CLCplus classes. Add a MAES mapping only with a cited, scientifically curated crosswalk; otherwise return unavailable habitat evidence.
4. Keep `PlausibilityEngine`'s rule strict: usable direct occurrence is `documented`; otherwise both an intersecting range and compatible preferred/suitable habitat are needed for `plausible`.
5. Add an institutional monthly window when documented. Use dated GBIF/NNB months only if that window is absent, mark it lower quality and never infer a negative season from sampling absence.
6. Add Natura 2000 only as positive context and keep habitat/period/time-of-day guidance separate from the level.

## Gotchas

- An adjacent reporting range is not an intersection.
- A reporting range is not a direct observation.
- An old, imprecisely dated or unlocated occurrence remains historical source data, not automatically `documented`.
- A source missing from the request must be visible in the explanation and must not be treated as `false`.
- The CLCplus 2021 fixture proves parsing only; it is intentionally non-equivalent to 2023.

## Verify

- [ ] Test range yes/no × habitat yes/no × season yes/no and assert that a lone required signal is never `plausible`.
- [ ] Test missing range/habitat, adjacent range, mixed land cover, historical data, missing Natura 2000 and unavailable observability.
- [ ] Replay Article 12/17, MAES and CLCplus fixtures with a fake HTTP client; test malformed and rate-limited responses.
- [ ] Assert same input has the same level and explanation, including provenance.
- [ ] Run `:core:plausibility:test`, then `verifyFast` and `verifyAll` from an unrestricted local PowerShell session.

## Update Scaffold

- [ ] Update `.mex/ROUTER.md` if the evidence boundary or its unresolved sources changed.
- [ ] Update `context/data-and-provenance.md` when a source, crosswalk or evidence rule changes.
- [ ] Update `patterns/add-data-provider.md` when the new provider adds reusable adapter guidance.
