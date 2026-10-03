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
last_updated: 2026-10-03
---

# 3D assets

F14 implements local personal GLB import, exact displayed-taxon association, preview, replacement/removal and clip selection/play/pause. Original procedural male blackbird is included for nominal `Turdus merula`; UI/manifest explicitly mark anatomy, visual and distribution-terms review as pending. Formal F14 completion additionally needs a physical-device performance check. In-app authoring remains excluded.

Every species profile has a working non-3D fallback. A GLB is optional enrichment and is loaded lazily. The asset manifest records species ID, asset version, file hash, author, source URL, license, modifications, and attribution text.

F13 implements a universally available generic fauna symbol, explicitly non-identifying, in `app/src/main/res/drawable/species_fallback.xml`. Original vector metadata/hash/version/attribution are recorded in `app/src/main/assets/asset-manifest.json`. Missing/unreadable resource falls back to original procedural Canvas geometry with the same accessible description and a visible warning. F14 preserves this path and adds a fifth visual signature for actual Filament GLB pixels. No third-party media is imported; physical-phone and real TalkBack checks remain unperformed.

`assets-3d/blackbird/create.py` uses official Blender/bpy 4.5.3 headless; `.blend`, preview and textual reference are retained. `export_glb.py` is a deterministic exporter specific to this rigid-node untextured study, with explicit matching illustrative clips; repeated GLB SHA-256 is identical. Khronos glTF Validator 2.0.0-dev.3.10 requires zero errors/warnings. `scripts/build-3d-assets.ps1` regenerates/validates and updates the common manifest; `verifyFast` checks the existing manifest without rewriting it.

`GlbInspection` accepts a bounded self-contained glTF-2 GLB subset: <=20 MiB, <=60k mesh/120k instantiated triangles, <=128 nodes/32 materials, <=16 PNG/JPEG images <=2048 per side/64 MiB decoded, <=32 clips. External references, required extensions and sparse accessors are rejected; buffers/indices/transforms/graph/images/animation count/time are checked before native resource parsing. Personal rights are user-declared, visibly distinguished from independent license approval. Invalid input never replaces a prior complete model.

`PersonalModelStore` keeps complete UUID-private copies, credits/date/hash in Room 10 and a 100-model maximum. It shares the photo mutex/restore generation and 24-hour orphan grace. Format-2 backup includes personal bytes/metadata and reads released format-1 schema-9 archives. A missing/corrupt personal file offers retry/reimport/removal and the existing 2D return path. `SpeciesModelDialog` loads only on request, uses Filament 1.77.1, stops frames on pause and releases model/engine on detach. Vulkan is selected where Android advertises hardware support, otherwise OpenGL; Windows SwiftShader OpenGL surface rendering crashed qemu whereas Vulkan passed the native test.

User limits/pipeline/manual gate: `GUIDA-FASE-14.md`; implementation evidence: `32 - Rapporto Fase 14.md`.

Prefer a small coherent low-poly set over unverified downloads. Test model scale, orientation, pivot, materials, file size, touch controls, and memory on a real phone. Keep Blender source files separate from delivery files and never lose the license record during export.
