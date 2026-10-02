---
name: refactor-shared-code
description: Share Android/Kotlin logic while preserving persistence and provider behavior.
triggers:
  - "dead code"
  - "duplication"
  - "refactor"
edges:
  - target: context/conventions.md
    condition: before accepting verification
  - target: patterns/change-local-storage.md
    condition: when extracting stored-data serializers
last_updated: 2026-10-02
---

# Refactor shared code

## Context

Shared code: `:core:network` (`JsonHttpTransport`), `:core:local` (`provenance-json.kt`), `:app` (`FaunaviaHeader`, `FaunaviaColors`, `parseDiaryInput`, `rememberTaxonomyLookup`). Provider policy and UI messages remain with consumers.

## Steps

1. Inspect callers, tests, manifest and Gradle entry points before calling code dead. Missing Kotlin graph nodes cannot establish non-use; check exact source references.
2. Preserve typed ports and keep transport infrastructure outside the domain.
3. Preserve stored JSON keys, nullable values and instant precision. Change Room only for an actual schema change.
4. Guard lookup results, errors and loading completion with request generations. Propagate cancellation and clear results when selected or too short.
5. Run `verifyFast`, then `verifyAll` with persistence, picker and visual tests. Include new JVM modules in gates/reports.

## Gotchas

- HTTP adapters have distinct status/error behavior. OSRM limits characters; metadata limits UTF-8 bytes. Preserve timeouts, User-Agent and `Retry-After`.
- Photo tables have separate ownership. Similar fields alone do not justify merging them.
- `removed.map { store.delete(it) }.all { it }` intentionally attempts every cleanup; `all { store.delete(it) }` stops after the first failure.
- Read/write Kotlin with explicit UTF-8 on Windows. Text-only import pruning misses delegated `getValue`/`setValue` used by Compose `by` declarations.

## Verify

- Validation, stale-query isolation and HTTP resource/error/size regressions pass.
- File-backed persistence, migrations, photo conversion and visual signatures pass.
- UI provider boundary, provenance and evidence levels are preserved.

## Update Scaffold

Record verified version/APK/gates in the router and module ownership in architecture. Keep previous phase reports as history.
