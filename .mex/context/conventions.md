---
name: conventions
description: Naming, structure, patterns, and verification rules for the application.
triggers:
  - "convention"
  - "pattern"
  - "naming"
  - "style"
edges:
  - target: context/architecture.md
    condition: when a convention depends on system structure
  - target: context/stack.md
    condition: when a convention depends on a selected library or framework
  - target: context/data-and-provenance.md
    condition: when writing provider, occurrence, or attribution code
grounds_to: []
last_updated: 2026-09-14
---

# Conventions

## Naming

- Domain types use singular PascalCase (`Route`, `Species`, `Occurrence`).
- Provider adapters use a provider name plus capability (`GbifOccurrenceProvider`).
- Files use kebab-case; test files sit beside the module they exercise.
- External IDs keep their provider prefix or are stored with an explicit `source` field.

## Structure

- UI components do not call GBIF, iNaturalist, Nominatim or tile endpoints directly.
- Geometry and evidence scoring live in framework-independent domain modules.
- Provider-specific response shapes are converted at the boundary into internal types.
- Asset metadata and licenses live next to the asset manifest, not only in UI code.

## Patterns

- Provider failures are explicit and recoverable: show cached data with a stale indicator or a clear retry state.
- Evidence explanations are data, not hard-coded prose: the UI renders score reasons and source links.
- Large 3D assets are lazy-loaded and always have a fallback representation.

## Verify Checklist

- [ ] No UI module calls a provider directly.
- [ ] Source, timestamp, license and quality are preserved for imported data.
- [ ] A provider/network failure has a visible fallback or retry path.
- [ ] “Observed” and “plausible” results remain distinct.
- [ ] New asset files include manifest metadata and a verified license.
- [ ] Geometry/ranking logic has deterministic tests for edge cases.
