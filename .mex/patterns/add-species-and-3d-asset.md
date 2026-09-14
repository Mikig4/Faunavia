---
name: add-species-and-3d-asset
description: Add a species profile and optional 3D/2D assets with verified provenance and a mobile-friendly fallback.
triggers:
  - "add species"
  - "species profile"
  - "3D asset"
  - "GLB"
  - "Blender"
edges:
  - target: context/assets-3d.md
    condition: always, because the manifest and GLB fallback are required
  - target: context/data-and-provenance.md
    condition: when adding taxonomy, distribution, observations, or external media
last_updated: 2026-09-14
---

# Add a species and 3D asset

## Steps

1. Choose a stable internal species ID and record names and taxonomy source.
2. Write the profile from cited sources; keep evidence separate from general natural-history facts.
3. Create or import a model only with a verified license and complete the asset manifest.
4. Export GLB, check scale/orientation/size, and generate a preview.
5. Add a 2D fallback and test the profile with the model unavailable.
6. Lazy-load the model and record the asset version in the fixture.

## Verify

- [ ] Scientific names and facts have sources.
- [ ] Asset author, source, license and modifications are recorded.
- [ ] GLB is optional and the 2D fallback renders.
- [ ] Mobile load size and interaction have been checked.
