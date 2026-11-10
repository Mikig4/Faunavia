---
name: discover-naturalist-outings
description: Maintain explicit route-scoped online outing search and durable source snapshots.
triggers:
  - "F17"
  - "outing discovery"
  - "sentieri"
edges:
  - target: context/data-and-provenance.md
    condition: always, before changing sources or coordinates
  - target: context/route-analysis.md
    condition: when modifying route scope or query boxes
  - target: patterns/plan-trip-and-record-memory.md
    condition: when changing outing storage or diary links
  - target: patterns/backup-and-restore.md
    condition: when changing durable outing fields
last_updated: 2026-10-03
---

# Discover naturalist outings

## Context

The explicit help button searches the selected Trip.stageScope or whole trip through OutingSearch/OverpassOutingSearch. F5 aggregate 20 km boxes are capped at 12 with an honest partial-coverage warning. The former included catalogue is only a JVM/Android fixture; production ships no predefined outing catalogue. OutingDiscovery ranks the returned proposals locally. OutingGuide still uses Room-10 payload v2 (reads v1), included in backup format 2.

## Steps

1. Verify source license, API limits and public-instance usage. Keep the replaceable endpoint suitable for personal tests; distributed production needs dedicated hosting/provider. Never upload full tracks, diary or wishlist when aggregate boxes suffice.
2. Start only from explicit help/retry buttons. Retain in-flight/completed state in OutingSearchViewModel across Activity recreation; selection, restoration and local filters cannot trigger a request. Ignore superseded/cancelled completions.
3. Bound POST time/body/elements and query area count. Preserve OSM identity, retrieval/database/object version, license, attribution and query. Handle incomplete records, server remarks, coverage limits, 429 cooldown and outage explicitly, with no automatic retries.
4. Filter nearest distance to each original route segment. Never connect separated segments. Missing fauna allows geographic proposals; explicit wish/season/metric filters exclude unknown facts. No encounter probabilities, access guarantees or synthetic walking geometry.
5. Label website tags as links indicated in OSM. Exclude private/no access or foot records. Centers are representative, not verified entrances. Only documented duration/distance/sac_scale/wheelchair values become facts.
6. Keep stable trip/proposal/day saves and immutable sources. Repeated saves open the existing snapshot; name/date edits preserve the guide, place/route changes discard it. Diary/backup behavior stays independent of live services.

## Gotchas

- No default request in LaunchedEffect or on route selection. After process death, restored open UI requires another explicit search click.
- Google is a browser search using public place labels, not an integrated billed Google Search/Places API and never full route geometry.
- Network calls live in the exploration adapter; UI source cannot include provider endpoint URLs.
- Lazy Compose assertions require performScrollToNode after awaited results; close the keyboard. Real Activity recreation tests need AnalysisTestActivity.testContent rather than a bare test Activity.

## Verify

- OverpassOutingSearchTest: bounded privacy/partial coverage, route midpoint/gaps, attribution, unknown fauna/metrics, restrictive access, malformed data, 429/empty/failure and safe links.
- JsonHttpTransportTest: POST form, response bounds and resource cleanup.
- F17DiscoveryUiTest and OutingSearchLifecycleTest: no selection/filter/restoration calls, selected stage/full scope, real recreation, save/reopen, outage/retry and preserved old sources.
- Existing F17PersistenceTest and populated F12BackupTest keep snapshots/legacy readers/links intact.
- Full unfiltered verifyAll, anti-omission and existing visual signatures. Live smoke is separate and saves only a status/count/source summary.

## Update Scaffold

Update Router, architecture/offline/data/decision/stack, report and guide. Keep F14 human/physical review, F15 regional packages and F16 sync decision open.
