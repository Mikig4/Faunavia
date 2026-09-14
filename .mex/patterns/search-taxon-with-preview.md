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
last_updated: 2026-09-14
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

- Taxonomic search results and occurrence images are separate API operations.
- A missing image must never make a valid animal unselectable.
- Do not bundle or permanently copy provider images without checking the image license.
- Common names may be missing, duplicated, or language-specific; show the scientific name.

## Verify

- [ ] Search works with common name, scientific name, and synonym.
- [ ] Only Animalia selectable taxa are shown.
- [ ] Preview attribution is visible or the UI clearly indicates unavailable media.
- [ ] Results remain usable when image requests fail or are rate-limited.
