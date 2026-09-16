---
name: patterns-index
description: Task-specific implementation and verification runbooks.
last_updated: 2026-09-16
---

# Pattern Index

Lookup table for all pattern files in this directory. Check here before starting any task — if a pattern exists, follow it.

<!-- This file is populated during setup (Pass 2) and updated whenever patterns are added.
     Each row maps a pattern file (or section) to its trigger — when should the agent load it?

     Format — simple (one task per file):
     | `filename.md` | One-line description of when to use this pattern |

     Format — anchored (multi-section file, one row per task):
     | `filename.md#task-first-task` | When doing the first task |
     | `filename.md#task-second-task` | When doing the second task |

     Example (from a Flask API project):
     | `add-api-client.md` | Adding a new external service integration |
     | `debug-pipeline.md` | Diagnosing failures in the request pipeline |
     | `crud-operations.md#task-add-endpoint` | Adding a new API route with validation |
     | `crud-operations.md#task-add-model` | Adding a new database model |

     Keep this table sorted alphabetically. One row per task (not per file).
     If you create a new pattern, add it here. If you delete one, remove it. -->

| Pattern | Use when |
|---------|----------|
| [add-data-provider.md](add-data-provider.md) | Adding or changing GBIF/iNaturalist or another biodiversity provider adapter |
| [add-observation-and-notification.md](add-observation-and-notification.md) | Adding manual observations, photos, peculiar-species rules, or the daily summary notification |
| [add-species-and-3d-asset.md](add-species-and-3d-asset.md) | Adding a species profile, Blender source, GLB model, or 2D fallback |
| [change-local-storage.md](change-local-storage.md) | Changing Room schemas, local repositories, migrations or diary integrity constraints |
| [import-route-and-analyze.md](import-route-and-analyze.md) | Importing a route, sampling it, building a corridor, and querying evidence |
| [search-taxon-with-preview.md](search-taxon-with-preview.md) | Searching the complete animal catalogue and showing a licensed photo preview before selection |
