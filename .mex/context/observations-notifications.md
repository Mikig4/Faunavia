---
name: observations-notifications
description: Manual observation diary, local photos, peculiar-species suggestions, timezone-aware daily summaries, and Android notifications.
triggers:
  - "observation"
  - "avvistamento"
  - "photo"
  - "diary"
  - "notification"
  - "daily summary"
  - "suggestion"
edges:
  - target: context/architecture.md
    condition: when observation or notification behavior changes the application flow
  - target: context/data-and-provenance.md
    condition: when distinguishing manual diary data from provider occurrences
  - target: context/android-local.md
    condition: when implementing Room, Photo Picker, alarms, or notification permission
  - target: context/offline-first.md
    condition: when storing photos and diary data without network access
last_updated: 2026-10-02
---

# Observations and notifications

## Observation contract

The F8B usability refinement searches the general catalogue directly in the diary instead of requiring a previous Catalogo selection. Debounced online search returns local selections on provider failure; errors expose retry. Selection saves the accepted taxon and aliases first, while diary save/edit/delete remain offline. “Aggiungi avvistamento” from a trip prefills optional planning links without requiring a result or coordinates.

An observation must point to a selected taxon from the adopted animal catalogue. The user may search by common/scientific name or synonym, but the saved record uses the accepted taxon ID. It may include local date/time, optional coordinates, count, notes, and one or more local photo references. Manual records remain distinct from imported provider records.

## Suggestion contract

Suggestions are a curated discovery layer, not the set of all possible animals. A `SuggestionProfile` needs area/habitat, distinctiveness, an urban-common exclusion flag, a source or curator note, and an explanation that can be shown to the user. The diary must never reject an animal because it is not suggested.

## Implemented F8B memories

Persistent unidentified drafts now preserve date, optional location, notes, quantity and trip/outing links in a separate table. Conversion requires an accepted Animalia taxon, retains identity/creation time and is atomic; failed or concurrent conversion cannot lose or duplicate the memory. Drafts never enter identified species counts.

Trips/outings group observations and drafts. Deleting planning only unlinks memories, preserving diary/photo metadata. Calendar and lists retain unlocated observations; the map draws only personally entered coordinates. Species/first-observation summaries use the global identified diary and recompute after edit/delete. “L'ho visto” from evidence prefills taxon/context only after verified selection, requires actual date/location confirmation and never copies an external point.

F10 now attaches private photos to both memory types and preserves them during identification. Notifications F11 must exclude drafts, and F12 backup must preserve planning, photos and all relationships, including F9 wishlists. Details and gates live in `09 - Piano di sviluppo dettagliato.md`.

## Implemented F9 suggestions

`PersonalSuggestionEngine` in the pure exploration module selects from existing live evidence or saved trip snapshots with the current analysis key. Curation never creates a presence level or changes an assessment. Four versioned original profiles (kingfisher, black woodpecker, ibex, urban-control blackbird) include habitat, source, date, license/redistribution limits, reason and editorial distinctiveness. Their coverage is an explicit pilot rectangle, not an administrative border or range; habitat compatibility is not inferred.

Typical uses threshold 0.6 and excludes urban-common profiles only in that view. Never-observed uses all identified diary taxa, matching accepted identity or scientific name with authority stripped; unidentified drafts and external occurrences cannot count as personal sightings. Reopening the screen after diary edits/deletes recomputes it. Easier-to-observe explicitly declares missing comparable ease estimates and uses stable alphabetical order; sourced F7 guidance can still be displayed separately.

Room schema 7 persists accepted-Animalia wishlist identities, with idempotent adds retaining their first timestamp. Wishes survive trip/diary deletion and taxon upserts; removal deletes only the wish. Catalogue explicit selection and trip cards expose add/remove; unresolved external identities must first resolve through taxonomy selection. Wishlist relevance is based only on the available current area/period results; missing or outdated evidence is unassessed, never absence. Offline desires and valid saved results work without providers; tile/new-analysis limitations remain F8.

Usage and migration notes: `GUIDA-FASE-9.md`. Automated F9 coverage: `PersonalSuggestionsTest`, `F9PersistenceTest`, `F9UiTest`.

## Planned daily summary

Schedule a local check at the user's chosen local time. The worker reads Room using the device timezone, counts manual observations for the local date, and emits a notification only when the count is greater than zero. It must be idempotent across reboot and timezone changes, and tolerate Android's scheduling flex.

## Photos

F10 uses Android Photo Picker through Activity 1.12.3, with document-picker fallback and no broad storage permission. Save the text memory first, then open its gallery. AndroidX ExifInterface 1.4.2 reads the input orientation; sampled decoding and fresh JPEG encoding apply all eight orientations and omit original EXIF/GPS. Private copies have a maximum 2048-pixel edge and 320-pixel thumbnail. Input is bounded to 32 MiB/100 MP with 48 MiB storage reserve.

Room 8 stores normalized dimensions, orientation 1, hash/byte size/MIME/private paths and draft-photo relationships; original picker URIs are not retained. File pairs precede metadata, failed attachment cleans the pair, and deletion removes metadata before private files. Recovery under a shared mutex removes only unreferenced files older than 24 hours. Conversion captures draft photos before cascade deletion, then reinserts them under the same identified memory ID atomically; rollback preserves the draft and its files.

The saveable gallery ID and ViewModel survive Activity recreation. Completed images reopen after process death without the original. A terminated unfinished import requires re-selection. Missing/corrupt images are visible fallback states; their text and metadata remain until explicit removal. Export/import remains F12, camera capture is deferred. See `GUIDA-FASE-10.md`.
