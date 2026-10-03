---
name: router
description: Session bootstrap and navigation hub. Read at the start of every session before any task. Contains project state, routing table, and behavioural contract.
edges:
  - target: context/architecture.md
    condition: when working on system design, integrations, or understanding how components connect
  - target: context/stack.md
    condition: when working with specific technologies, libraries, or making tech decisions
  - target: context/conventions.md
    condition: when writing new code, reviewing code, or unsure about project patterns
  - target: context/decisions.md
    condition: when making architectural choices or understanding why something is built a certain way
  - target: context/setup.md
    condition: when setting up the dev environment or running the project for the first time
  - target: patterns/INDEX.md
    condition: when starting a task — check the pattern index for a matching pattern file
  - target: patterns/add-data-provider.md
    condition: when adding a provider adapter
  - target: patterns/import-route-and-analyze.md
    condition: when importing a route and running analysis
  - target: patterns/search-taxon-with-preview.md
    condition: when searching and selecting a taxon
  - target: patterns/add-observation-and-notification.md
    condition: when adding an observation or notification
  - target: patterns/add-species-and-3d-asset.md
    condition: when adding a species profile or 3D asset
  - target: patterns/change-local-storage.md
    condition: when changing Room schemas, repositories, migrations or diary integrity
  - target: patterns/backup-and-restore.md
    condition: when evolving SAF export/import, ZIP archives or staged database/photo restoration
  - target: patterns/debug-gradle-android-gates.md
    condition: when Gradle, JVM workers, managed devices, UTP or Compose UI gates fail
  - target: patterns/explore-place-and-map.md
    condition: when changing the F8A geographic search, exploration screen or MapLibre adapter
  - target: patterns/plan-trip-and-record-memory.md
    condition: when changing saved trips, outings, result snapshots, diary links or unidentified drafts
last_updated: 2026-10-03
---

# Session Bootstrap

If you haven't already read `AGENTS.md`, read it now — it contains the project identity, non-negotiables, and commands.

Then read this file fully before doing anything else in this session.

## Current Project State

**Working:**
- Obsidian project folder and Git repository created.
- MEX 0.8.2 scaffold initialized with Codex skills and a fresh code graph.
- Product brief, architecture proposal, data plan, asset plan and roadmap are documented in the vault.
- F0 is complete: Lombardia pilot, five synthetic routes, versioned provider fixtures, normalized data contracts, source ADR and a green offline test gate under `f0/`.
- F1 is complete: installable Compose APK, six placeholder destinations, pure Kotlin domain boundary, shared deterministic fakes and four automated Gradle verification gates.
- F2 is complete: nine domain models, Room schema v2 with v1 migration, local repositories, accepted-Animalia diary constraints, transactional writes and file-backed reopening tests.
- F3 is complete: the GBIF Species adapter resolves synonyms to accepted Animalia taxa; the Catalogo UI debounces queries, shows loading/empty/error states and preserves selections offline without requiring a photo preview.
- F4 is complete: the offline Diary creates, edits and deletes manual observations with a mandatory selected taxon, local date/time, quantity, notes and optional coordinates; Room schema v4 preserves legacy rows with quantity one.
- F5 is complete: `:core:route` parses GPX/GeoJSON or a point, preserves segments, samples by distance and emits EPSG:3035 corridor portions, 1 km cells, 5 km chunks and deterministic fingerprints. The Percorsi UI imports locally and stores only normalized geometry.
- F6 is complete: `:core:occurrence` queries GBIF polygons or fallback bounding boxes and NNB WFS bounding boxes behind one gateway, deduplicates provider records and returns bounded retry/failure or explicit stale-cache states. Room schema v5 persists normalized records and complete provenance, never raw payloads.
- F7 is complete in `:core:plausibility`: Article 12/17, MAES and bounded CLCplus adapters normalize provenance; direct evidence, range, habitat, season and positive Natura 2000 context produce a deterministic trace. Range and habitat remain jointly mandatory for `plausible`.
- F8A is implemented: the Risultati screen accepts confirmed names, coordinates, GPX/GeoJSON and saved local routes, then period/radius → F5/F6/F7 analysis → explained cards, filters and MapLibre map with list fallback. Country/region queries explicitly cover a bounded sample, not the entire administrative area.
- The F8A cumulative gate is green: 13 F0 tests, 66 Gradle JVM tests, 32 managed-device tests, formatting, boundaries, lint, omission detection and home/exploration visual goldens. The live GBIF and NNB WFS smoke checks returned HTTP 200 during F6 verification.
- F8B is complete: saved trips/outings, confirmed destinations, dates/radius/interests, chosen place/result snapshots, shared essential cards, confirmed personal-sighting prefills, linked diary filters/calendar/map/global personal firsts and persistent unidentified drafts. Room v6 migrates additively; conversion is atomic and planning deletion only unlinks memories.
- The cumulative F8B gate is green: 13 F0, 71 JVM and 54 managed-device tests, build, lint, formatting, boundaries, omission detection and three visual golden tests. Recovery and limits are in `GUIDA-FASE-8B.md` and `20 - Rapporto Fase 8B.md`.
- The first F8B usability refinement was verified: trips-first navigation, destination/date-only creation, external point Maps links and diary catalogue search with local fallback. Its gate passed 13 F0, 73 JVM and 60 Android tests, lint, boundaries, omission detection and three goldens. This is the historical 0.8.1 baseline in `21 - Rifinitura Viaggi e Diario.md`, superseded functionally by the clarified route requirement below; F9/F13 scope is unchanged.
- The clarified F8B extension is verified: departure/destination/dates, explicit full-route selection on the internal map via authorized endpoint-only OSRM calculation, persisted trace, Maps directions and corrected real GBIF common-name lookup. Its full gate passed 13 F0, 82 JVM and 66 Android tests, lint, boundaries, omission detection and three visual goldens. Live API 36 checks confirmed Italian “merlo” search and Milano–Como route persistence. APK `artifacts/Faunavia-f8b-viaggi-debug.apk` is 0.8.2-f8b (11); see `22 - Tracciato viaggio e Catalogo.md`. The physical phone remains untested; F9/F13 scope is unchanged.

- The 2026-10-02 F8B extension for freely ordered dated stages is verified: per-leg route selection, full-trip/stage views, stage-day evidence and Maps directions, with backward-compatible Trip payload v3 and Room schema 6. The cumulative gate passed 13 F0, 84 JVM and 70 Android tests, lint, boundaries, omission detection and three visual goldens. APK `artifacts/Faunavia-f8b-tappe-debug.apk` is 0.8.3-f8b (12); see `23 - Tappe e giorni del viaggio.md`. The new Android tests use deterministic providers/maps; no new live or physical-device test was performed.
- F9 is complete: four versioned pilot profiles, typical/easier/never-personally-observed/wishlist views, explicit unavailable comparable ease estimates, global identified-diary filtering, current-scope evidence relevance and offline accepted-Animalia wishes. Room schema 7 migrates existing profiles as readable legacy v0 without inventing habitats. The final cumulative gate passed 13 F0, 93 JVM and 82 Android tests with zero failures/skips, lint, formatting, boundaries, omission detection and three visual signatures. APK `artifacts/Faunavia-f9-debug.apk` is 0.9.0-f9 (13), signed with the same identity as F8B. See `24 - Rapporto Fase 9.md` and `GUIDA-FASE-9.md`; no new live-provider or physical-phone check.

- The F9 refinement approved on 2026-10-02 is verified: research defaults to typical animals in both exploration and trip contexts, twelve curated pilot profiles, compact names/habitat/season cards and reviewed Commons distribution links opened externally on click. Full evidence and personal saved snapshots remain accessible. Gate: 13 F0, 96 JVM, 87 Android, zero failures/skips/omissions and three visual signatures. APK `artifacts/Faunavia-f9-tipici-debug.apk`, 0.9.1-f9 (14), same signing identity. See `25 - Animali tipici e schede compatte.md`; clickable curiosities are explicitly planned in F13, with primary natural-history sources and complementary Wikimedia references, not implemented yet.

The 2026-10-02 F9 correction is verified: readable species titles (green Scaffold inherited white text), exact Italian-name metadata without implicit Room selection, and exploration/trip analysis jobs/results retained through Activity recreation. Distribution opens inside the app for every taxon, with attributed reusable Commons illustrations via exact Wikidata P225/P181 association and a separately labelled native GBIF historical-observation layer. No IUCN feed or F7 range promotion is introduced. Final gate: 13 F0, 102 JVM, 94 Android, zero failures/skips/omissions, lint, boundaries, formatting and three visual signatures. APK `artifacts/Faunavia-f9-mappe-debug.apk`, 0.9.2-f9 (15), same signing identity, Room 7. See `26 - Nomi, rotazione e mappe interne.md`; physical-phone testing remains unperformed, curiosities remain clickable F13.

- F10 is complete: private Photo Picker galleries for identified observations and unidentified drafts, controlled JPEG/thumbnail copies with all EXIF orientations applied and sensitive metadata omitted, recorded hash/size/dimensions, offline reopening, independent photo deletion, transactional conversion preserving photos and visible recovery/cleanup errors. Room 8 migrates 7→8 additively. Final `verifyAll` passed 13 F0, 103 JVM and 109 Android tests with zero failures/skips/omissions, lint, boundaries, formatting and three existing visual signatures; a real API 36 picker and Activity recreation are tested with synthetic photos. APK `artifacts/Faunavia-f10-debug.apk` is 0.10.0-f10 (16), same signing identity as F9. See `27 - Rapporto Fase 10.md` and `GUIDA-FASE-10.md`; no physical-phone check, export/import remains F12.

- Post-F10 refactoring is verified: unused F1 ports/fakes removed; shared diary parsing, catalogue/diary lookup lifecycle, headers/colors, JSON provenance and six-client HTTP transport in `:core:network`. Room stays 8, existing payloads and provider policies are preserved; drafts now trim quantity whitespace consistently. `verifyAll`: 13 F0, 111 JVM, 110 Android, zero failures/skips/omissions, lint/boundaries/format and three visual signatures. APK `artifacts/Faunavia-f10-refactor-debug.apk`, `0.10.1-f10` (17), same signing identity; 67 fewer production Kotlin lines. See `28 - Semplificazione del codice.md` and `patterns/refactor-shared-code.md`. No new live-provider or physical-phone test.

- F11 is complete: local flexible reminders, permission/settings, current-day identified record counts, persistent daily delivery markers and summary deep links with private photos. Room 9 and WorkManager 2.12.0; final `verifyAll` passed 13 F0, 115 JVM, 123 Android with zero failures/skips/omissions, lint/boundaries/format and three existing visual signatures. Includes real permission denial, notification tap, Activity recreation, unique work, populated migration and cancellation between preferences/save scheduling. APK `artifacts/Faunavia-f11-debug.apk`, 0.11.0-f11 (18), same signing identity. Reboot is simulated through persistence reopening, no physical-phone or prolonged Doze check. See `29 - Rapporto Fase 11.md` and `GUIDA-FASE-11.md`; backup remains F12.

- F12 is complete: SAF ZIP export/import with versioned manifest, SHA-256 inventory and all 19 durable tables/private images; staged Room/domain/image validation, preview/cancel, transactional replacement and rollback. Unique photo publication and repository/photo generations protect prior references and queued edits; reminders are rescheduled against receiving-device permissions/timezone. Post-commit cleanup warnings survive the fresh Activity task. Final `verifyAll`: 13 F0, 115 JVM, 136 Android, zero failures/skips/omissions, lint/boundaries/format and three existing visual signatures. Real SAF, rotation and cleanup permission denial are covered. APK `artifacts/Faunavia-f12-debug.apk`, 0.12.0-f12 (19), Room remains 9, same signing identity. Schema 8 compatibility uses a synthetic fixture; no earlier released backup, physical-phone transfer or maximum-volume test. See `30 - Rapporto Fase 12.md`, `GUIDA-FASE-12.md` and `patterns/backup-and-restore.md`. F13 is next; F14–F17 numbering is unchanged.

- F13 is complete: readable shared species details, twelve versioned natural-history profiles, per-fact/name provenance, clickable sourced curiosities and explicit unavailable fields. Original generic 2D vector/procedural recovery is accessible and offline; a bounded 64-profile presentation cache stays separate from Room/backup. General biology does not classify local evidence or select taxa. Final `verifyAll`: 13 F0, 123 JVM, 144 Android, zero failures/skips/omissions, lint/boundaries/format and four visual signatures. Includes measured rendered font scale 1.8, source-open failures, recreation, result→profile→distribution→result/map and unchanged diary/backup snapshots. APK `artifacts/Faunavia-f13-debug.apk`, 0.13.0-f13 (20), 56,935,930 bytes, Room 9/backup format 1, same signing identity. A pre-existing F11 Back/IME timing dependency was corrected without disabling its real permission test. No physical-phone, nonlinear OS font-scaler or actual TalkBack session. See `31 - Rapporto Fase 13.md`, `GUIDA-FASE-13.md` and `patterns/add-species-and-3d-asset.md`. F14 is next; F15–F17 unchanged.

- F14 implementation is automated-verified: original procedural blackbird with retained Blender/bpy source/preview/reference, deterministic 172,764-byte GLB/two clips, Khronos zero errors/warnings and shared manifest. Filament 1.77.1 lazy viewer adds orbit/pinch/reset, clip play/pause and private attributed GLB import/replacement/removal without implicit taxon selection. Room 10 adds personal models; format-2 backup includes complete model bytes/credits and reads released format-1/schema-9 archives. Final unfiltered `verifyAll` passed in 10m31s: 13 F0, 123 JVM, 155 Android, zero failures/errors/skips/omissions, lint/boundaries/format and five visual signatures. APK `artifacts/Faunavia-f14-debug.apk`, 0.14.0-f14 (21), 86,635,610 bytes, same signing identity as F13. Native focused capture: first frame 1,421ms/total process native heap 90,815,920 bytes on software emulator, not a phone budget. Formal F14 completion is pending human anatomy/visual/distribution-terms approval and at least one physical-device performance check. See `32 - Rapporto Fase 14.md`, `GUIDA-FASE-14.md` and the model/backup runbooks; do not mark the formal gate complete from emulator evidence.

**Not yet built:**
- F15–F17 expansion includes trip offline preparation, optional synchronization assessment and F17 discovery of observation places/trails. These later features are not implemented; subsequent numbering including F17 is unchanged.
- Device current-location adapter and true regional offline maps; manual coordinates are already supported in F8A.
- Broader curated-species coverage and sourced comparable ease estimates; the F9 pilot does not invent them.
- Reviewed natural-history profiles beyond the twelve F13 pilot entries; other selectable taxa retain local data or explicit fallback.
- Additional reviewed Blender/GLB species assets, regional map package and physical-device tests. The first F14 original asset is explicitly awaiting human review.

**Known issues:**
- F14 human asset review and physical-device performance remain open. Windows SwiftShader OpenGL surface rendering crashed qemu; the viewer selects Vulkan when advertised, otherwise OpenGL. A real native pixel test is required alongside resource preflight. Model dialog visibility must be hoisted to the profile owner to survive saved-state restoration.
- Offline map source and any Firebase sync boundary are still open decisions; Android build versions are pinned by F1.
- NNB GeoAPI returned HTTP 503 during F0; WFS is live as the F6 fallback, but its dataset-specific reuse permission remains unresolved and is retained in every record's license field.
- CLCplus Backbone 2023 is publicly released, but a bounded sampling endpoint and a scientifically curated CLCplus-to-MAES crosswalk are still required before live land-cover data can support `plausible`; F7 therefore keeps non-equivalent 2021 input and uncurated mappings `insufficient`.
- MEX population was completed manually because the interactive Codex TUI was unavailable in this terminal.
- AGP 9.4 still prints an advisory about the managed-device ABI even though `testedAbi = "x86_64"` is explicit; re-check on the next AGP upgrade.
- Gradle outputs are redirected to the portable toolchain directory because OneDrive locks incremental build files in the checkout.
- The Windows Gradle launcher and project JVM arguments are aligned so `--no-daemon` can run in-process. Test workers inherit a JDK option that activates the built-in TCP fallback instead of the restricted Unix-domain socket; recovery is recorded in `GUIDA-GRADLE-LOOPBACK.md`.
- F5 deliberately supports the European EPSG:3035 area. Extra-European and antimeridian routes fail explicitly until a separate global projection strategy is designed.
- F8A uses public Nominatim only on explicit submit (not autocomplete), with one-request-per-second process limit and 30-day place cache; the public service is not a production-scale/offline geocoder. MapLibre uses online OSM raster tiles with attribution and HTTP cache, not an offline map package.
- F8A live search has no institutional range/habitat feed yet; without direct usable occurrences the F7 level remains `insufficient`, never invented `plausible`.
- F8B animal interests are persisted preferences; occurrence records lack complete group classification, so result choice is explicitly manual rather than an invented taxonomic filter. Saved evidence snapshots remain distinct from the expiring provider cache and personal observations.

## Routing Table

Load the relevant file based on the current task. Always load `context/architecture.md` first if not already in context this session.

| Task type | Load |
|-----------|------|
| Understanding how the system works | `context/architecture.md` |
| Working with a specific technology | `context/stack.md` |
| Writing or reviewing code | `context/conventions.md` |
| Making a design decision | `context/decisions.md` |
| Setting up or running the project | `context/setup.md` |
| Any specific task | Check `patterns/INDEX.md` for a matching pattern |
| Adding a provider adapter | `patterns/add-data-provider.md` |
| Importing a route and running analysis | `patterns/import-route-and-analyze.md` |
| Searching and selecting a taxon | `patterns/search-taxon-with-preview.md` |
| Adding an observation or notification | `patterns/add-observation-and-notification.md` |
| Adding a species profile or 3D asset | `patterns/add-species-and-3d-asset.md` |
| Backup ZIP, SAF export/import, staging or restoration | `patterns/backup-and-restore.md` |
| Debugging Gradle, JVM tests, managed devices or Compose UI gates | `patterns/debug-gradle-android-gates.md` |
| Changing place search, F8A exploration or MapLibre | `patterns/explore-place-and-map.md` |
| Saved trips/outings, evidence snapshots, linked diary or unidentified drafts | `patterns/plan-trip-and-record-memory.md` |
| Route import, sampling, or corridor analysis | `context/route-analysis.md` |
| Provider, occurrence, licensing, or privacy work | `context/data-and-provenance.md` |
| 3D model, GLB, Blender, or species asset work | `context/assets-3d.md` |
| Cache, offline, Android, or device constraints | `context/offline-first.md` |
| Manual observations, photos, suggestions, or notifications | `context/observations-notifications.md` |
| Android, Room, MapLibre, APK, or offline map packages | `context/android-local.md` |

## Behavioural Contract

For every task, follow this loop:

1. **CONTEXT** — Load the relevant context file(s) from the routing table above. Check `patterns/INDEX.md` for a matching pattern. If one exists, follow it.
2. **BUILD** — Do the work. If a pattern exists, follow its Steps. If you are about to deviate from an established pattern, say so before writing any code — state the deviation and why.
3. **VERIFY** — Load `context/conventions.md` and run the Verify Checklist item by item. State each item and whether the output passes. Do not summarise — enumerate explicitly.
4. **DEBUG** — If verification fails or something breaks, check `patterns/INDEX.md` for a debug pattern. Follow it. Fix the issue and re-run VERIFY.
5. **GROW** — After meaningful work, run this binary checklist:
   - **Ground:** What changed in reality? Name the changed behavior, system, command, dependency, or workflow.
   - **Record:** If project state changed, update the "Current Project State" section above. If documented facts changed, update the relevant `context/` file surgically.
   - **Orient:** If this task can recur and no pattern exists, create one in `patterns/` using `patterns/README.md`, then add it to `patterns/INDEX.md`. If a pattern exists but you learned a gotcha, update it.
   - **Write:** Bump `last_updated` in every scaffold file you changed. Read `mex logging --json` before optional `mex log` notes: `significant` records material rationale, `checkpoints` batches useful notes at task/session boundaries, and `manual` avoids unsolicited notes. Honor explicit user log requests in every mode; mandatory workflow Activity and recovery audits remain required.
