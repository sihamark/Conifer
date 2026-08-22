# Changelog

## Version 1.X.X (XX.08.2026)

- upcoming changes

## Version 1.2.1 (01.08.2026)

- the packaged desktop release shipped with **no database at all**, so adding a bit did nothing and
  syncing had nothing to sync. Room 3 renamed its package to `androidx.room3` while the rule that
  keeps Room's generated code still said `androidx.room`, so it matched nothing and the shrinker
  deleted the database implementation, both DAOs and every migration. Room looks that generated
  code up by name at runtime, so nothing references it statically to save it from being deleted.
  Only the shrunk release build was ever affected - running from Gradle was always fine. Android
  release builds were never affected either, because Room ships this rule inside its Android
  artifact and a plain jar can't do that; the app's own copy of the rule carried the same outdated
  package name and has been corrected too
- dependency updates: Room 3.0.1, KSafe 3.0.0, Ktor 3.5.2, DataStore 1.3.0-alpha10 and Compose Hot
  Reload 1.2.0

## Version 1.2.0 (01.08.2026)

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
- Nextcloud sync groundwork (spec §10 point ②): a Ktor-based WebDAV transport (`KtorWebDavStore`)
  implementing PROPFIND/GET/PUT/MKCOL and a bulk-upload fast path with automatic fallback, with a
  hand-rolled multistatus XML parser; verified against a real, containerized Nextcloud via
  Testcontainers
- Nextcloud sync engine (spec §10 point ③): `SyncEngine` runs the fast-path check, pull, push, and
  finalize steps against any `RemoteStore`, resolving conflicts through the last-write-wins merge
  policy and recovering from a stale ETag on push by refetching, merging, and retrying; bits'
  JSON wire format (`BitJson`, kotlinx.serialization) keeps the app's zoneless local date-time
  instead of the generic spec's zoned timestamps, so synced bits still can't drift across time
  zones; verified against an in-memory fake WebDAV server, including a genuine cross-device race
  and a sync aborted partway through
- readable module (spec §10 point ④): `ReadableRenderer`/`ReadableModule` render and upload one
  deterministic, human-readable Markdown file per day alongside the machine JSON (same data on
  any device always renders to identical bytes), skipping the upload whenever the rendered
  content hasn't actually changed since the last one (a pure-Kotlin SHA-256, no platform digest
  API needed)
- tombstone garbage collection (spec §10 point ⑤): a deleted bit's file is physically removed from
  the server (and its local row dropped) 90 days after the deletion itself was last pushed clean,
  run at most once a week as part of a normal sync
- Nextcloud Login Flow v2 and encrypted credential storage (spec §10 point ⑥): `LoginFlowV2` polls
  the stock Nextcloud login-flow endpoints for an app password without the app ever seeing the
  real one; `Credentials` stores it via KSafe (hardware-backed encryption per platform), wired in
  through the same per-platform initializer pattern as the rest of sync; a startup check logs a
  warning if the encryption key ever falls back to software-only storage instead of the platform's
  secure enclave/keystore
- sync requests now identify themselves as e.g. `Conifer (Android 36)` instead of Ktor's default
  `ktor-client`, so the app password shows up under a recognizable name in Nextcloud's
  Settings → Security device list (Nextcloud names the token after the `User-Agent` of the
  login-flow request)
- a full review pass over the sync stack (see `sync_review.md`) found and fixed: pull now
  downloads a bucket's files with parallelism ≤ 6 instead of one at a time; a brand-new server now
  gets the `.sync/meta/manifest.json` marker the spec calls for; re-pushing a bit now preserves any
  JSON fields a newer app version might have added instead of silently dropping them; a device
  returning from a long stretch offline no longer resurrects a post another device deleted and
  garbage-collected while it was away (a stopgap, not the full spec mitigation - still needs a
  settings UI to ask the user, see `sync_review.md` #1)
- sync settings UI wired up to the app bar, mirroring the mockup's sync entry point: a cloud icon
  (muted/tinted/spinning for disconnected/connected/syncing) opens a sheet to connect through
  Login Flow v2 in the system browser, change the destination folder ("app root") independently of
  reconnecting, trigger a manual sync, or disconnect; once connected, tapping the icon instead
  opens a short status glance - account, whether/when the last sync happened, "Sync now" and a way
  into the sync settings - with the troubleshooting fields (device id, app root, last sync/GC time,
  root ETag, last error, last tally) tucked behind an expand button in its header instead of
  always on screen
- on the widest windows (Material's large width class, 1200.dp — a maximized desktop or web
  window, an unfolded foldable in landscape) sync is no longer a sheet over the bits but a third
  pane to the right of them, opened and closed by the same cloud icon: status, account,
  "Sync now"/"Disconnect", the server and app-folder fields and the troubleshooting details all in
  one place, since a pane that is already on screen has no reason to keep half of itself behind a
  "Sync settings…" button.
  Crossing the breakpoint slides the pane in and out beside the day sidebar instead of snapping,
  and below it the glance/sheet is unchanged
- before connecting, warns and requires an explicit "connect anyway" confirmation if the resulting
  credentials would land in a weaker key custody than usual (e.g. no OS keyring reachable), instead
  of silently storing them less securely
- a log file per app start (`logs/conifer-<start time>.log`, keeping the newest 10 runs) recording
  what sync actually did, each line stamped with the local date and time: what triggered a round
  (manual, after an edit, periodic, after connecting, after an app-folder change), how long it took
  and what it moved, which buckets changed, when the fast path skipped the whole round, GC and
  readable-rendering passes, and every warning/error along the way. Written on Android (private
  `files/logs/`), iOS (`Documents/logs/`, so it comes along in a backup or file transfer) and
  desktop (next to the app's `data/` folder); the web target has no file system and keeps logging
  to the browser console only. Contains no credentials and no bit content: nothing in the sync
  stack logs an app password, poll token or login URL, every line additionally passes through a
  redactor for URL credentials/tokens echoed back by exceptions, and a bit that fails to parse is
  now logged by size instead of by dumping its JSON
- HTTP traffic now goes into that log file as well, tagged `[http]` so it can be read - or filtered
  out - on its own: method, URL, status and headers for every sync and login request. Bodies are
  deliberately left out (the Login Flow v2 poll response *is* the app password) and the
  `Authorization` header is replaced before a line is ever written, so the file still holds no
  credentials
- the packaged desktop release starts and can reach the network again. All of the following only
  ever went wrong in the shrunk release build and never when running from Gradle, so none of it
  showed up until the distributable itself was used:
  - every HTTP request failed on a missing Ktor engine, which is chosen at runtime through
    `ServiceLoader`, so nothing referenced it statically and the shrinker deleted it
  - the macOS app couldn't be started after unzipping the release zip, whose writer didn't record
    Unix permissions and so dropped the launcher's executable bit
  - the release build itself no longer fails outright on ProGuard warnings about OkHttp's
    optional TLS providers (Conscrypt, BouncyCastle, OpenJSSE), none of which are on the
    classpath

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
- the two-pane layout from the mockup: from Material's medium window width class up (600.dp — so
  desktop and web windows, tablets and unfolded foldables) a day sidebar sits next to the bits —
  "All days" plus the last 30 days, each with the day strip's dots and the number of bits written
  that day — and takes over
  the day selection from the composer's picker, leaving it with just the time slider; compact
  windows keep the single-pane layout with the day strip. Crossing the breakpoint — by resizing a
  desktop window, say — slides the sidebar in and out and collapses the day strip instead of
  snapping the whole layout over
- the desktop window can no longer be resized below 300 x 480 dp, where the layout starts to break
  down (still under the two-pane breakpoint, so the single-pane layout stays reachable)

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