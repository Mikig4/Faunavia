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
last_updated: 2026-09-14
---

# Observations and notifications

## Observation contract

An observation must point to a selected taxon from the adopted animal catalogue. The user may search by common/scientific name or synonym, but the saved record uses the accepted taxon ID. It may include local date/time, optional coordinates, count, notes, and one or more local photo references. Manual records remain distinct from imported provider records.

## Suggestion contract

Suggestions are a curated discovery layer, not the set of all possible animals. A `SuggestionProfile` needs area/habitat, distinctiveness, an urban-common exclusion flag, a source or curator note, and an explanation that can be shown to the user. The diary must never reject an animal because it is not suggested.

## Daily summary

Schedule a local check at the user's chosen local time. The worker reads Room using the device timezone, counts manual observations for the local date, and emits a notification only when the count is greater than zero. It must be idempotent across reboot and timezone changes, and tolerate Android's scheduling flex.

## Photos

Prefer Android Photo Picker, copy a controlled-size version into app-private storage, keep the URI/hash in Room, and provide export/import. Do not upload photos in the MVP.
