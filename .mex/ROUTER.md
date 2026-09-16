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
last_updated: 2026-09-16
---

# Session Bootstrap

If you haven't already read `AGENTS.md`, read it now — it contains the project identity, non-negotiables, and commands.

Then read this file fully before doing anything else in this session.

## Current Project State
<!-- What is working. What is not yet built. Known issues.
     Update this section whenever significant work is completed.
     This is the primary drift prevention mechanism — it re-grounds the agent every session.
     Length: 3 sections (Working / Not Built / Known Issues), 3-7 items each.
     Example:
     **Working:**
     - User authentication and session management
     - Core CRUD operations for all main entities

     **Not yet built:**
     - Email notification system
     - Admin dashboard

     **Known issues:**
     - Pagination breaks on filtered queries with more than 1000 results -->

## Current Project State

**Working:**
- Obsidian project folder and Git repository created.
- MEX 0.8.2 scaffold initialized with Codex skills and a fresh code graph.
- Product brief, architecture proposal, data plan, asset plan and roadmap are documented in the vault.
- F0 is complete: Lombardia pilot, five synthetic routes, versioned provider fixtures, normalized data contracts, source ADR and a green offline test gate under `f0/`.
- F1 is complete: installable Compose APK, six placeholder destinations, pure Kotlin domain boundary, shared deterministic fakes and four automated Gradle verification gates.
- F2 is complete: nine domain models, Room schema v2 with v1 migration, local repositories, accepted-Animalia diary constraints, transactional writes and file-backed reopening tests.
- `verifyAll` runs F0–F2 regression: 13 F0 tests, 12 JVM tests and 13 API 36 device tests (Room, migrations, launch, navigation and golden). `verifyDevice` fails if any declared instrumented test is absent from the report.

**Not yet built:**
- Diary UI, taxonomy search, route engine and map UI beyond the F1 placeholders.
- Provider adapters, peculiar-species suggestion rules and evidence ranking.
- Manual observation diary, local photos, daily notification and species catalogue.
- Blender/GLB asset library, regional map package and physical-device tests.

**Known issues:**
- Offline map source and any Firebase sync boundary are still open decisions; Android build versions are pinned by F1.
- NNB GeoAPI returned HTTP 503 during F0; WFS works as a fallback, but dataset-specific reuse permission remains unresolved.
- CLCplus 2023 point access and the scientific CLCplus-to-MAES crosswalk must be resolved before F7; the F0 technical fixture uses 2021 and is marked non-equivalent.
- The F0 WGS84 0.01° grid is provisional; F5 must compare it with a metric EPSG:3035 implementation.
- MEX population was completed manually because the interactive Codex TUI was unavailable in this terminal.
- AGP 9.4 still prints an advisory about the managed-device ABI even though `testedAbi = "x86_64"` is explicit; re-check on the next AGP upgrade.
- Gradle outputs are redirected to the portable toolchain directory because OneDrive locks incremental build files in the checkout.

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
