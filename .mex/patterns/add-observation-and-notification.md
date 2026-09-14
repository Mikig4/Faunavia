---
name: add-observation-and-notification
description: Add or change manual diary entries, photos, peculiar suggestions, or the daily local summary notification.
triggers:
  - "add observation"
  - "avvistamento"
  - "photo observation"
  - "daily notification"
  - "suggestion rule"
edges:
  - target: context/observations-notifications.md
    condition: always, because diary and notification semantics are the feature contract
  - target: context/android-local.md
    condition: when implementing Room, Photo Picker, WorkManager, alarms, or Android permissions
  - target: context/data-and-provenance.md
    condition: when distinguishing manual records from provider evidence
last_updated: 2026-09-14
---

# Add an observation or daily notification

## Steps

1. Keep the manual observation path independent from the suggestion list.
2. Resolve the animal through the catalogue by common/scientific name or synonym; save an accepted taxon ID.
3. Save the structured record first; attach a local photo reference separately.
4. Resolve the local date using the device timezone and test midnight/DST boundaries.
5. Schedule or update one idempotent daily local check; do not add FCM or a server for local data.
6. Show the summary only when at least one manual observation exists.

## Verify

- [ ] An animal absent from suggestions can still be saved.
- [ ] Photo access is optional and the record works without a photo.
- [ ] No network is required to save, view, or summarize an observation.
- [ ] The notification is not emitted for an empty day.
- [ ] Android 13+ permission denial disables the reminder without disabling the diary.
