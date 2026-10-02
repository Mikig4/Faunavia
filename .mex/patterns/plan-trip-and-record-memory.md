---
name: plan-trip-and-record-memory
description: Preserve F8B planning, chosen evidence, diary links and unidentified drafts without losing or inventing personal memories.
triggers:
  - "trip"
  - "outing"
  - "viaggio"
  - "bozza"
  - "unidentified draft"
edges:
  - target: context/android-local.md
    condition: when changing Room tables, triggers, transactions or state restoration
  - target: context/observations-notifications.md
    condition: when changing diary projections, conversion or future notification counts
  - target: patterns/explore-place-and-map.md
    condition: when changing analysis scope, provider access or the map
  - target: patterns/debug-gradle-android-gates.md
    condition: when verification infrastructure or Compose tests fail
grounds_to: []
last_updated: 2026-10-02
---

# Plan trips and record memories

## Context

F8B separates four concepts: planning, chosen external evidence, identified personal observations and persistent unidentified drafts. Use `trip-models.kt`, Room schema 6 and `TripScreen.kt` as current implementation evidence; do not treat a missing/stale MEX code-graph node as proof that a symbol does not exist. `GUIDA-FASE-8B.md` contains the concise recovery commands.

## Steps

0. Keep the ordinary path confirmed departure/destination + dates → choose the full trace on the internal map → trip → evidence/diary. Name, radius/interests, manual coordinates, outings and imported routes are optional. OSRM calculation is explicit, endpoint-only, rate-limited and authorized. Maps directions receive the endpoints on click and recalculate independently; legacy trips/outings still open a point.
1. Confirm destinations explicitly and validate dates/radius before analysis. Copy normalized route geometry into outings; F17 discovery is not a route-import feature.
2. Save complete original result period/area, saved time, explanations, evidence provenance and partial/stale flags. Include scope changes in the analysis key, not display-only names. Require explicit recalculation after dates/area/radius/interests/outing geometry change.
3. Prefill a personal observation only with a verified accepted taxon and trip/outing links. Require actual date/location confirmation; never copy provider occurrence coordinates. Missing identity is a taxonomy selection, not permission to create a new accepted taxon.
4. Keep drafts in their separate table. Conversion removes the draft and inserts the identified observation in one transaction with the same ID and original creation time. A failed insert must restore the deleted draft.
5. Planning deletion must unlink memories before cascades. Validate outing membership in the repository and SQLite triggers; reject cross-trip links and IDs present in both observation/draft tables.
6. Derive personal species/firsts from identified diary records only. Lists/calendar include unlocated memories, while map features contain only actual personal coordinates.
7. Preserve the populated legacy database with additive migrations. Do not rebuild observations and accidentally cascade-delete photo metadata. Restore existing editors only after their data has loaded, preserving unsaved saveable fields and identity.

## Gotchas

- Trip payload v3 adds ordered dated stages; absent stages remain empty for v1/v2 without changing Room schema 6. Preserve legacy analysis keys for trips without stages. A stage's analysis uses its complete geometry and one day; snapshot IDs include stage identity to avoid overwriting another day's result for the same species. Removed stages leave snapshots readable but outdated.
- Reordering/changing a destination invalidates only selected legs whose origin or destination changed; changing dates keeps the geometry. Require non-decreasing dates within the trip, allow same-day legs and rest days, and save the entire itinerary atomically as one Trip payload. Whole-trip geometry concatenates segments without bridging them. Diary links remain trip/outing links, not synthetic stage foreign keys.
- Changing a confirmed endpoint discards pending choices and selected geometry; ignore late answers for previous endpoints. A failed recalculation preserves the saved choice. Never fabricate a line on failure or promise Google Maps returns its chosen geometry.
- Unsaved route choices stay out of saved-instance bundles to avoid large Binder payloads; after process recreation they need recalculation. Confirmed endpoints, dates and other small fields remain saveable; persisted routes reopen without a routing request.

- The diary searches the general catalogue, not just previously selected taxa. Debounce and cancel obsolete queries, preserve local fallback, and persist the selected taxon/aliases before accepting it in the editor. A selected prefill can be saved without network. Use the latest editor state after asynchronous selection so other edits are not lost.
- Tests must explicitly expand optional trip controls; scroll to the toggle before checking whether its children are composed. Primary launch is Viaggi; the home golden now captures that launch screen.
- Custom bottom navigation must reserve Android navigation-bar insets. Verify button bounds against the system bar; the status-bar area stays green with light icons. Restrict dark navigation icons/pale system bar to API 27+ resources; API 26 uses green with light icons.
- Keep date initialization compatible with minSdk 26: use `instant.atZone(zone).toLocalDate()`, not `LocalDate.ofInstant`, which lint requires API 34 or desugaring for. Do not add a dependency or suppress NewApi for this conversion.
- After an asynchronous catalogue result appears, close the keyboard and scroll explicitly again before clicking: a lazy child can exist in semantics while remaining outside the viewport. Scroll to newly expanded error messages before asserting visibility.
- Result payloads also contain an outing reference; read the authoritative nullable column after an outing delete, not the obsolete JSON reference.
- Expiring provider cache and durable chosen snapshots are distinct. An offline retry must not remove snapshots or present them as newly calculated.
- Animal-interest preferences do not justify guessing class/group from binomial names. Until classification exists, disclose manual choice.
- Do not relax the mandatory selected taxon in `ObservationDraft` to support unidentified memories. Use `UnidentifiedInput` instead.
- Compose helpers returning Unit must be PascalCase for lint. Close the keyboard and scroll the vertical lazy container to a nested row, then scroll that row horizontally to compose off-screen filters. The outer container cannot find an uncomposed child of a horizontal lazy list.

## Verify

- [ ] Free dated stages preserve order/full geometry/provenance, invalidate changed legs, reject out-of-range/backwards dates, survive reopening, and scope Maps/evidence to the selected leg/day. Removing a stage retains diary memories and marks old stage snapshots outdated.
- [ ] Endpoints/date/trace creation, explicit route selection, changed-endpoint invalidation, routing retry, Maps directions/failure and legacy point links work; general catalogue and diary retry work.
- [ ] Reopening preserves trips/outings, copied route geometry, result provenance and drafts.
- [ ] Populated migration retains diary precision, quantities, photos, settings and cache.
- [ ] Raw SQL rejects invalid membership/duplicate identity; planning deletion preserves memories.
- [ ] Failed and concurrent conversion leaves exactly one memory and preserves creation time.
- [ ] Trip/result/card/confirmed sighting flow, offline snapshots, explicit recalculation, filters, calendar/map and global firsts work.
- [ ] Restored trip and outing editors retain unsaved fields without duplicating the stored entity.
- [ ] `verifyAll --no-daemon --stacktrace` passes, including old phases, omission detection and all three golden tests.

## Debug

Read the external Android result XML first and reproduce the failing test. Investigate transaction order and trigger validity before changing constraints. A broken migration or conversion must be fixed without clearing the user's database. For Gradle startup or sandbox/toolchain access failures follow the existing Android gate pattern, not a weakened test command.

## Update Scaffold

- [ ] Update Router project state and relevant architecture/storage/observation/offline context when behavior changes.
- [ ] Keep report and recovery guide aligned with actual verification results.
- [ ] Bump touched scaffold dates and consult `mex logging --json` before optional notes.
