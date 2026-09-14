---
name: setup
description: Development environment setup and commands.
triggers:
  - "setup"
  - "install"
  - "environment"
  - "getting started"
  - "local development"
edges:
  - target: context/stack.md
    condition: when specific technology versions or libraries are needed
  - target: context/architecture.md
    condition: when understanding components during setup
  - target: context/offline-first.md
    condition: when testing local storage, APK install, or device behavior
  - target: context/conventions.md
    condition: when setup introduces project-wide tooling or scripts
grounds_to: []
last_updated: 2026-09-14
---

# Setup

## Prerequisites

- Node.js 22.5+ (required by MEX; current environment: Node 24).
- Git.
- Android Studio with a compatible JDK and Android SDK.
- An Android device or emulator with geolocation, notifications and WebGL/GLB support.
- Blender only when creating or editing 3D assets.

## First-time Setup

1. Open the Android project in Android Studio after the native scaffold is created.
2. Build and install a debug APK locally: `[TO BE DETERMINED]`.
3. Run unit tests for route geometry, ranking, local-date boundaries and notification eligibility: `[TO BE DETERMINED]`.
4. Test a sample GPX/GeoJSON fixture and a manual photo observation on a real device.
5. For MEX maintenance, use `npx mex-agent@0.8.1 graph status` and `npx mex-agent@0.8.1 hub`.

## Environment Variables

- None required for the planned MVP.
- Provider keys are not assumed; if a provider later requires one, document it as optional and keep a no-key fallback.

## Common Commands

- `[TO BE DETERMINED]` — build/install debug APK.
- `[TO BE DETERMINED]` — unit tests.
- `[TO BE DETERMINED]` — production build.
- `npx mex-agent@0.8.1 graph status` — inspect MEX graph.
- `npx mex-agent@0.8.1 wiki rebuild-index` — rebuild MEX wiki index after context changes.

## Common Issues

- **Provider limits or downtime:** show cached results and the provider error; do not retry in a tight loop.
- **Geolocation denied:** allow GPX/GeoJSON import or manual place search; never block at the permission dialog.
- **MEX indexes stale:** run explicit graph/wiki maintenance after reviewing working-tree changes.
