---
name: assets-3d
description: Blender authoring, GLB delivery, asset licensing, metadata, performance, and species-profile fallback behavior.
triggers:
  - "3D"
  - "GLB"
  - "glTF"
  - "Blender"
  - "asset"
  - "model"
edges:
  - target: context/architecture.md
    condition: when loading assets from species profiles or changing lazy loading
  - target: context/stack.md
    condition: when selecting a viewer, optimizer, or build integration
  - target: context/conventions.md
    condition: when naming files or adding asset manifests
  - target: context/data-and-provenance.md
    condition: when a model or image needs license/author/source metadata
last_updated: 2026-09-20
---

# 3D assets

Planned F14 extension approved 2026-09-20: local personal GLB import, taxon association, preview, replacement and removal; supported animation clip selection and play/pause. In-app modeling/animation authoring is excluded. Invalid imports preserve existing assets, and versioned backup must cover personal models and provenance. This is specified, not implemented.

Every species profile has a working non-3D fallback. A GLB is optional enrichment and is loaded lazily. The asset manifest records species ID, asset version, file hash, author, source URL, license, modifications, and attribution text.

Prefer a small coherent low-poly set over unverified downloads. Test model scale, orientation, pivot, materials, file size, touch controls, and memory on a real phone. Keep Blender source files separate from delivery files and never lose the license record during export.
