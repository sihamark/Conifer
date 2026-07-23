# Changelog

## Version 1.2.0 (XX.XX.2026)

- groundwork for Nextcloud sync (spec §10 point ①): bits now carry sync bookkeeping (modification
  time and device, tombstone flag, dirty marker, remote ETag, fixed month bucket) — database
  schema v4 with a migration that keeps existing data and marks it for a first push
- new local sync state tables (bucket ETags, readable-day hashes and pending queue) with a
  dedicated sync DAO; deleting an already-synced bit now leaves a hidden tombstone instead of
  removing the row
- last-write-wins merge policy (per-bit, deterministic device-id tiebreaker) as pure, tested logic
- sync preferences (root ETag, last sync time, device id, server URL, app root) in a
  DataStore-backed store, wired per platform via the initializer pattern; on web they persist
  to localStorage (DataStore 1.3 WebLocalStorage)

## Version 1.1.2 (17.07.2026)

- changed exported display of exported bits (show complete date dd.MM.yyyy)
- when editing a bits, the cursor in the text field is now always at the end of the text
- the keyboard now auto-capitalizes the start of sentences in the bit text field
- a selected date/time is kept after adding a bit, so several bits can be entered for the same
  date/time in a row
- the top bar is hidden while the keyboard is open, leaving more room for reading existing bits
- the day selection chips are more compact, showing weekday and date on a single line
- the day chip indicator now shows up to three dots: one for any bit, two when both morning and
  afternoon have one, three when the day additionally holds more than three bits
- the notification permission prompt is now a compact banner matching the mockup: bell icon,
  highlighted lead sentence, dashed border and an "Allow" button
- the date/time of a bit is now stored as a zone-less local date-time (database schema v3), so
  bits keep the day and time they were entered with even when the device's time zone changes
- after adding or editing a bit, the list now scrolls to that bit (unless it is already visible),
  so a bit saved with a custom date/time doesn't disappear off-screen

## Version 1.1.1 (07.07.2026)

- reduced size of time slider thumb
- time of a bit is now in a column in front of the actual text
- adjusted padding of a card

## Version 1.1.0 (03.07.2026)

- major styling improvement
- editing and deleting of bits is now possible
- upgraded to Room 3 (androidx.room3), which unblocks the web target
- re-added a wasmJs web target via a new :webApp module; the web build uses Room's
  WebWorkerSQLiteDriver with a vendored sqlite-web worker (OPFS persistence)

## Version 1.0.0 (XX.XX.XXXX)

- refactored default kmp project layout added initializer functions and renamed default functions and classes
- added room database
- remove web target which are incompatible with room
- remove most expect actual declarations in favor for initializers