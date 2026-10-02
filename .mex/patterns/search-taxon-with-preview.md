---
name: search-taxon-with-preview
description: Implement taxonomic autocomplete with a licensed photo preview before an animal is selected.
triggers:
  - "taxon search"
  - "animal autocomplete"
  - "photo preview"
  - "species search"
  - "synonym"
edges:
  - target: context/data-and-provenance.md
    condition: always, because taxon identity, source version, image license, and attribution must be preserved
  - target: context/observations-notifications.md
    condition: when the selected taxon becomes a manual observation
  - target: context/android-local.md
    condition: when caching thumbnails or implementing Android image loading
last_updated: 2026-10-01
---

# Search a taxon with preview

## Steps

1. Debounce the user's query and search common/scientific names and synonyms.
2. Restrict the result set to accepted selectable Animalia taxa.
3. Show scientific name, common name when available, rank, and source identifier.
4. Fetch thumbnails only for visible results, with a strict small limit.
5. Preserve image creator, rights holder, license, source URL, and cache expiry.
6. Display a placeholder when no image or acceptable reuse rights are available.
7. Save the accepted taxon ID only after explicit selection.

## Gotchas

- Use the GBIF vernacular search response for common-name fixtures, including `taxonomicStatus` and multilingual `vernacularNames`. `/suggest?q=merlo` does not demonstrate Italian Merlo lookup. Test the actual adapter through the UI and perform a bounded live APK smoke separately.
- Prefer the displayed Italian exact common name before exact foreign aliases. `Merlo` in Spanish may identify `Labrus merula`; the expected Italian bird is `gbif:2490719` / `Turdus merula`.
- The `URLEncoder.encode(String, Charset)` overload requires Android API 33 even in pure JVM dependency modules. Use the encoding-name overload for minSdk 26; the boundary guard prevents recurrence. Search UI always clears loading in `finally`, propagates cancellation and offers retry/fallback after typed or unexpected failures.

- Taxonomic search results and occurrence images are separate API operations.
- A missing image must never make a valid animal unselectable.
- Do not bundle or permanently copy provider images without checking the image license.
- Common names may be missing, duplicated, or language-specific; show the scientific name.
- F3 uses a 350 ms Compose debounce and a five-minute in-memory remote-result TTL. Persist only an explicit accepted Animalia selection, its stable ID and its aliases; offline search must read that local set instead of an expired remote response.
- The GBIF adapter owns request URLs and maps malformed payloads, timeout and network errors to recoverable states. Resolve a returned synonym through its accepted key before it reaches the selectable list.
- Compose tests that inject a search fake need an empty activity in the debug app target. Do not call the test rule's `setContent` after an activity that already installs the production composition.

## Verify

- [ ] Search works with common name, scientific name, and synonym.
- [ ] Only Animalia selectable taxa are shown.
- [ ] Preview attribution is visible or the UI clearly indicates unavailable media.
- [ ] Results remain usable when image requests fail or are rate-limited.
