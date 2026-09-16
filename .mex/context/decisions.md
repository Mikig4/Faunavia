---
name: decisions
description: Key architectural and technical decisions with reasoning.
triggers:
  - "why do we"
  - "decision"
  - "alternative"
  - "we chose"
edges:
  - target: context/architecture.md
    condition: when a decision relates to system structure
  - target: context/stack.md
    condition: when a decision relates to technology choice
  - target: context/data-and-provenance.md
    condition: when changing source data, licensing, or evidence semantics
  - target: context/offline-first.md
    condition: when changing local storage or network behavior
grounds_to: []
last_updated: 2026-09-14
---

# Decisions

## Decision Log

### Local-first PWA before a hosted backend
**Date:** 2026-09-14
**Status:** Superseded by "Android-native local-first app"
**Decision:** Start as an installable client-side PWA with local persistence and no mandatory backend.
**Reasoning:** It fits a personal use case and the no-cost constraint while keeping location data local.
**Alternatives considered:** Hosted API/database (deferred because it adds cost, deployment and privacy complexity); native mobile app (deferred until device/offline needs justify it).
**Consequences:** Provider adapters must tolerate browser limits and the app needs clear cache/error states.

### Android-native local-first app
**Date:** 2026-09-14
**Status:** Active, pending implementation spike
**Decision:** Build the first distributable product as a native Android app with Kotlin, Jetpack Compose, Room/SQLite and local notifications.
**Reasoning:** Android download, background notification behavior, local photos and offline storage are first-class requirements; a native app reduces platform workarounds.
**Alternatives considered:** PWA/Capacitor (kept as a future option, but background scheduling and offline map behavior need more native integration); cloud-first app (rejected for cost/privacy).
**Consequences:** The first codebase is Android-specific and an APK can be sideloaded without Play Store costs.

### Manual diary is independent from suggestions
**Date:** 2026-09-14
**Status:** Active
**Decision:** Any animal can be recorded manually after selecting an accepted Animalia taxon, whether suggested or found through the general catalogue; suggestions are a separate curated discovery layer.
**Reasoning:** The user's observation must never be constrained by the ranking policy that hides common urban animals, while a stable accepted taxon keeps the diary searchable and unambiguous.
**Alternatives considered:** Allow only suggested species (rejected because it would lose real observations and personal notes).
**Consequences:** `Observation.speciesId` and taxonomic source/version are mandatory; an unresolved name can be searched or previewed but is not persisted as an observation.

### Local notifications before push messaging
**Date:** 2026-09-14
**Status:** Active
**Decision:** The end-of-day reminder is computed and delivered on-device; FCM is not required.
**Reasoning:** The condition depends only on local observations and must work offline. FCM would add a server-side sender without improving this use case.
**Alternatives considered:** FCM/Cloud Functions (deferred for future sync or remote messages); no notification (rejected because the daily prompt is part of the core diary workflow).
**Consequences:** The app must request Android notification permission and tolerate system scheduling delays.

### Firebase is optional structured sync
**Date:** 2026-09-14
**Status:** Active, pending a real backup need
**Decision:** Do not introduce Firebase in the MVP; consider Firestore later for structured sync, while photos remain local until costs and backup requirements are explicit.
**Reasoning:** The personal diary can work entirely offline; current Firebase Storage setup requires a billing plan for Android use.
**Alternatives considered:** Cloud-first Firestore + Storage (deferred because it violates the strict zero-cost assumption for photo storage); manual export (chosen for MVP).
**Consequences:** Import/export format and migration must be designed even before cloud sync exists.

### Complete selectable animal catalogue via taxonomic backbone
**Date:** 2026-09-14
**Status:** Active, pending taxonomy spike
**Decision:** Let the user search and select any accepted Animalia species/subspecies from a taxonomic backbone; keep the peculiar-species suggestions as a separate curated subset.
**Reasoning:** The diary must not be limited to animals suggested for a place, while manually maintaining a global list would become stale and incomplete.
**Alternatives considered:** Bundle a hand-written global list (rejected as incomplete); bundle an entire taxonomy snapshot in the first APK (deferred because it increases size and update work).
**Consequences:** Online autocomplete plus local caching is the MVP path; every saved observation stores a stable taxon ID and source/version metadata.

### GBIF as the first biodiversity provider
**Date:** 2026-09-14
**Status:** Active, API shape validated in F0
**Decision:** Use GBIF as the first occurrence-search adapter and keep iNaturalist optional.
**Reasoning:** GBIF exposes a documented occurrence API and is suitable for repeatable, source-backed queries.
**Alternatives considered:** iNaturalist first (deferred until licensing and rate limits are confirmed for cached use); custom dataset (too much initial curation work).
**Consequences:** Results are evidence-based and potentially sparse; provider metadata and licenses are first-class fields.

### Lombardia pilot and provisional geographic partition
**Date:** 2026-09-14
**Status:** Active for F0; grid decision must be revisited in F5
**Decision:** Use Lombardia with a synthetic Parco Nord Milano pilot, a configurable 1 km corridor sampled every 500 m, stable WGS84 0.01° cells and 0.05° query chunks.
**Reasoning:** Five contrasting short routes produced bounded, deterministic queries and made the data-source spike reproducible without treating synthetic paths as navigation data.
**Alternatives considered:** Italy-wide first spike (too broad for source diagnostics); provider-specific unversioned bounding boxes (rejected because cache and replay identity would drift); immediate EPSG:3035 implementation (deferred to the production route-engine phase).
**Consequences:** The F0 keys and fingerprints are test contracts only. F5 must measure distortion and edge cases before adopting or replacing the grid.

### Evidence instead of presence prediction
**Date:** 2026-09-14
**Status:** Active
**Decision:** The product language reports documented or plausible presence, never a guaranteed current encounter.
**Reasoning:** Occurrence data has temporal, spatial and sampling bias; honest uncertainty is essential for a nature app.
**Alternatives considered:** A single “animals here now” list (rejected because it would overstate the data).
**Consequences:** Ranking, UI labels and tests must preserve the distinction between evidence levels.
