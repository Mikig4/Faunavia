---
name: assets-3d
description: Historical 3D feature retirement and backup compatibility.
triggers:
  - "retired 3D"
edges:
  - target: context/architecture.md
    condition: when checking the remaining archive compatibility
last_updated: 2026-10-27
---

# Retired 3D feature

Removed on user request, 2026-10-26: viewer, importer, GLB parser, Filament, bundled model, Blender/Khronos pipeline and dedicated tests/golden.

The historical personal_models Room table and private bytes remain solely for old-backup compatibility. Archives validate bounded paths, sizes, inventory and SHA-256 and preserve intact files as opaque attachments. Missing or damaged files are omitted from exports with a visible warning, without changing local rows or files. They are never parsed or displayed. Room 11 and backup format 3 remain unchanged.

Use patterns/add-species-profile.md for current species work. The F14 report records the old implementation; 36 - Rimozione modelli 3D.md records its removal.
