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
last_updated: 2026-10-03
---

# Add a species and 3D asset

F17 adds automatic Wikipedia photos to the same profile without adding an asset to the APK. `RemoteSpeciesMetadata.photo` verifies unique P225 identity, Wikipedia pageprops item and Commons reusable rights/author/official URLs. `WikipediaSpeciesPhoto` reuses the bounded image loader and shows credit/source/article/stale/error/retry. Keep the generic fallback, no implicit taxon selection, and synthetic `WikipediaPhotoUiTest` images. Never use a remembered Wikidata ID without P225 verification: Q25334 is the robin, whereas the live blackbird smoke resolves Q25234. The smoke checks metadata/HEAD without retaining image bytes.

## Steps

1. Choose a stable internal species ID and record names and taxonomy source.
2. Write the profile from cited sources; keep evidence separate from general natural-history facts.
3. Create or import a model only with a verified license and complete the asset manifest.
4. Export GLB, check scale/orientation/size, and generate a preview.
5. Add a 2D fallback and test the profile with the model unavailable.
6. Lazy-load the model and record the asset version in the fixture.

## Verify

### F13 profiles without GLB

F13 implements steps 1/2/5; F14 adds the original blackbird pipeline and personal GLB viewer below. For a non-3D profile:

1. Add original factual notes to `PilotNaturalHistory`, using exact scientific identity and per-field/curiosity source, consultation date, license constraints, quality and content version. Do not import source prose/photos under an assumed license; omit conflicting/unavailable facts.
2. Keep the compact result readable and place facts in `SpeciesProfileDialog`; curiosities and provenance open on request. General biology must not enter evidence scoring or implicitly select/save a taxon. Subspecies need their own reviewed profile.
3. Reuse the generic labelled 2D vector/procedural fallback or add a separately reviewed asset with full manifest metadata/hash. It must work offline and never impersonate an identification photograph.
4. Preserve Room/backup independence. Current local reads precede old cache copies; deleted/restored fields must not resurrect from cache. Normalize/bound any new cached format and fail visibly on corrupt/missing provenance.
5. Exercise `SpeciesProfileTest`, `F13ProfileStorageTest`, `F13ProfileUiTest`, then unfiltered `verifyAll`. Keep link schemes/credentials checked; test title contrast, large text, source errors, no invented curiosity, recreation and result→profile→distribution→result/map.

Gotcha: Android Dialog supplies its own `LocalUriHandler`. Inject `LocalProfileSourceOpener` for deterministic browser tests; an outer framework UriHandler override can accidentally launch a real browser and remove the Compose hierarchy. Catalogue's default IME action is not a submit action; close the keyboard directly in tests.

Dialog also installs its own `LocalDensity`. Capture the parent density before entering the modal and provide it inside when the app's text scale must be retained. The large-font test checks the rendered `TextLayoutResult.layoutInput.density.fontScale == 1.8`, not just an outer configuration. Keep the fixed close action and scrollable facts accessible at that measured scale. Native OS nonlinear scaling and a real TalkBack session still require physical-device review.

- [ ] Scientific names and facts have sources.
- [ ] Asset author, source, license and modifications are recorded.
- [ ] GLB is optional and the 2D fallback renders.
- [ ] Mobile load size and interaction have been checked.

### F14 GLB pipeline and personal library

1. Retain original `.blend`, headless script, reference/terms and preview under `assets-3d/<species>/`. Existing blackbird uses official bpy 4.5.3. Its deterministic exporter is intentionally specific to rigid-node untextured geometry; generic imported models use their own authoring exporter.
2. Run `scripts/build-3d-assets.ps1` and repeat `-SkipRender`; compare delivery SHA-256. Standard Blender addon export produced different vertex/index ordering here, so the study exporter canonicalizes triangles while preserving winding and rounds encoded values consistently. Khronos 2.0.0-dev.3.10 must have zero errors/warnings; publish manifest hash/credits/units/Y-up/ground pivot/review status. Do not silently rewrite the manifest during `verifyFast --check`.
3. Keep personal imports self-contained and bounded with `GlbInspection`, then native resource validation before any metadata publication. Save complete synced files to unique UUID paths. Record taxon/name/author/source/rights/modifications/import date/hash; do not implicitly select catalogue taxa. Personal rights remain declared by the user, not independent approval.
4. Load only from the explicit profile action, preserve 2D, show static state or supported clip selection/play/pause, rotate/pinch/reset. Keep model dialog visibility and controls in the profile's owning composition, outside its native Dialog subtree; otherwise StateRestorationTester can lose nested dialog visibility.
5. Use lifecycle-driven frame callbacks and one native-owner disposal. Stop when paused/detached and let ModelViewer's detach hook own engine destruction; explicit duplicate engine destruction is unsafe. On Windows SwiftShader, actual OpenGL surface rendering crashed qemu; Vulkan on advertised support passed. Parser-only success is not a render test.
6. Include personal bytes and metadata in format-2 backup, preserve format-1/schema-9 readers and the photo/model mutex/restore generation. Removal hides the association immediately; orphan copies have a grace period rather than immediate filesystem deletion.
7. Exercise `F14ModelStoreTest`/`F14ModelUiTest`, all earlier sentinel/goldens and the unfiltered `verifyAll`. Write synthetic captures to instrumentation `additionalTestOutputDir`; shell copying an app-private file can silently fail. A real SAF test must re-fetch DocumentsUI objects after waiting for transitions rather than click a stale returned object.
8. Complete human anatomy/visual/distribution-terms review and test at least one real phone before marking F14 complete or scaling to more species. Record measured device/rendering budget and explicit approval; current asset review remains pending.

SAF fixture gotcha: UiAutomation shell commands do not interpret `>`/`>>` as shell redirection. A base64-print command can return text without creating a file. Publish a complete owned GLB using MediaStore Downloads with `IS_PENDING`, write its bytes, then clear pending; the actual document picker must select that document and private import must survive independently. Delete only the owned synthetic fixture afterward.
