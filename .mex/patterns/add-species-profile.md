---
name: add-species-profile
description: Add sourced species facts, photographs and accessible offline 2D fallbacks.
triggers:
  - "add species"
  - "species profile"
  - "species photo"
edges:
  - target: context/data-and-provenance.md
    condition: when adding taxonomy, natural-history facts or external media
  - target: context/architecture.md
    condition: when changing profile presentation or caching
last_updated: 2026-10-26
---

# Add a species profile

The 3D feature was removed on 2026-10-26. Do not add a model importer, renderer, assets or their tests as part of ordinary species work. See the historical F14 report only for older builds.

1. Choose a stable scientific identity and preserve taxonomic/name provenance. Add original factual notes to `PilotNaturalHistory`, with per-field sources, consultation dates, license constraints, quality and content version. Omit conflicting or unavailable facts; general biology does not classify local evidence or select/save a taxon.
2. Keep compact results readable and place facts in `SpeciesProfileDialog`. Curiosities and provenance open on request. Retain the accessible generic 2D vector/procedural fallback offline. Record author/source/license/modifications/hash for included assets.
3. For automatic photos, `RemoteSpeciesMetadata.photo` verifies unique P225 identity, Wikipedia pageprops identity and Commons reuse rights/author/official URLs. `WikipediaSpeciesPhoto` shows credit/source/article/stale/error/retry through the bounded loader. Do not assume an identifier from memory. Use synthetic images in `WikipediaPhotoUiTest`; online smoke checks metadata/HEAD without retaining image bytes.
4. Keep presentation caches separate from Room/backup. A successful local read after restoration must not resurrect facts from an old cache. Bound and normalize new formats; expose corrupt/missing provenance.
5. Exercise `SpeciesProfileTest`, `F13ProfileStorageTest`, `F13ProfileUiTest` and relevant photo tests, then unfiltered `verifyAll`. Preserve the four remaining visual signatures. Check large text, contrast, source errors, recreation, distribution/return navigation and unchanged diary/backup data.

Android Dialog installs its own `LocalUriHandler` and `LocalDensity`. Inject `LocalProfileSourceOpener` for deterministic source tests and provide the captured parent density inside the dialog. The large-text check measures rendered font scale 1.8; real TalkBack and nonlinear OS scaling still need device review.
