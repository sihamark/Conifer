# Changelog

## Version 1.2.5 (16.08.2026)

- sync now follows the app rather than the clock. It used to run every five minutes for as long as
  the app had a process — which on a phone is long after you have put it away, so it kept syncing,
  and kept the phone awake for it, for bits nobody was reading. It syncs when you come back to the
  app instead, and every five minutes while you are in it. Coming back within those five minutes
  costs nothing, so turning the phone or glancing at another app does not start a round
- the app no longer says it went away unannounced when it was simply put away. It says goodbye in
  its log on leaving the screen, but a phone app put away is not stopped — its sync kept writing
  afterwards, burying the goodbye. That is noted where nothing can write over it now, so the notice
  means what it says again
- the bits of the day you left no longer stay standing behind the ones you picked. Opening the app
  and landing on a day sometimes left the previous list showing through underneath it — a second
  screenful of bits from other days, sitting still while the real list scrolled over them, until
  something emptied the list and swept them away. They were the bits on their way out: a bit leaving
  the list used to fade rather than go, and a whole list leaving at once left one of those fades
  hanging. A bit that goes is gone at once now, and the ones below it slide up into its place
- the notice about the last run's error no longer takes over the screen. An error is as long as it
  cares to be, and the notice grew to fit whatever it was handed — until *Share report* and
  *Dismiss* had been pushed off the bottom of the window and it could neither be sent nor got rid
  of. It now shows as much of the error as the window can spare; the report still carries the whole
  of it
- picking an exact time no longer takes the desktop app down with it. The dialog opens on its typed
  fields wherever there is a keyboard, which on the desktop is always, and in the packaged app those
  fields could not run at all: the step that trims the app for release had rewritten Material's own
  code into something the Java runtime refused. It is left alone now, and the app is no larger for
  it
- the shortcuts card no longer offers a *debug crash* button. It is there to end the run on purpose,
  which is how the notice above gets tried out, and it now shows up only in a build being worked on

## Version 1.2.4 (13.08.2026)

- turning the screen with the keyboard up no longer takes the app down with it. The new-bit field is
  given as many lines as the room left over affords it, so a screen put away lying down and picked
  up standing has the field come back a different size than it went — and a field of one line
  scrolls sideways where a taller one scrolls down, so it came back scrolling the wrong way, which
  was enough to end the run. It scrolls down now whatever its size: a one-line field looks exactly
  as it did, and a bit too long for the width wraps rather than running off the end
- opening the keyboard no longer pushes the newest bits out of sight. The composer grows by the
  height of the keyboard and takes that room off the list, and the list stayed where it was — so the
  bits at the bottom, the ones you had just been reading and had just written, slid behind it. The
  list now moves with it: whatever was at the bottom edge stays at the bottom edge, and putting the
  keyboard away hands the room back instead of leaving you a screenful further on
- the app now says when the last run ended badly. A crash was already being written down, into a
  file nobody was ever told about — the next start now says so on screen instead: when it happened
  and what the error was. It also notices the endings that leave nothing behind at all, where the
  app was closed by the system, killed for memory or stopped by something below it: the log now says
  goodbye whenever the app is put away, so one that simply stops means something. *Share report*
  hands the whole thing to the share sheet on a phone, drops it in a folder on the desktop or
  downloads it in a browser, and *Copy details* puts it on the clipboard instead: the build, what
  went wrong, and the log of that run — the part that says what the app had been doing — and nothing
  you have written. The notice stays until you dismiss it
- the log says which build wrote it. Its second line now names the version, the commit the app was
  built from and when it was built — so a log sent in can be lined up against what was actually
  shipped, instead of against a version number that covers a month of changes. A build made on
  someone's own machine from work in progress says so, since its commit describes source that exists
  nowhere else
- a crash is written down. A run that ended badly used to stop mid-sentence: the log said what the
  app had been doing and nothing said that it had died, so a log handed over afterwards had no
  answer for the one question worth asking. Now the error and its whole stack trace are the last
  thing that file gets, whichever part of the app it came out of — a thread, a background job, the
  screen itself. It goes to disk on the spot rather than queueing up behind the lines still in
  flight, because an app on its way out has no later. Nothing about the crash itself changes: it
  ends the same way it ended before, and the report your phone offers to send, the trace on the
  desktop console and the crash log on an iPhone are all still made
- a bit can be given an exact time. The slider moves in quarter hours, which is the right size for
  *about now* and the wrong one for *seven minutes past* — the time beside it opens a proper clock
  now, and the minute you set there is the minute the bit gets. It is your system's clock: twelve or
  twenty-four hours as your machine has it, opening on the keypad where there is a keyboard and on
  the dial where there is a finger. Nothing is kept until you press *Set time*, and the slider's
  thumb settles on the nearest quarter hour while the exact time is written out beside it
- the day sidebar's rows are a single line again. *Today* had been wrapping beside the current day
  and leaving that row twice as tall as the rest; where the system spells the short weekday out in
  full — *sábado*, *Jumamosi* — every row did it. Names with no room left are shortened to *sáb…*
  now, and the sidebar is a little wider
- text you have typed but not yet added is kept. Half a sentence in the field used to be gone the
  moment the app was recycled in the background, a desktop window was closed or a browser tab was
  reloaded — it is written down half a second after you stop typing now, and put back the next time
  the screen opens. The date and time you had picked come back with it, and an edit you were in the
  middle of comes back as that same edit rather than as a new bit. Adding the bit or cancelling the
  edit drops the draft, since by then you have said what you wanted done with it. It stays on the
  device: a draft is not a bit yet, so it is neither synced nor visible anywhere else
- on macOS the shortcuts have moved off ⌥ and onto **⌃⌥**: ⌃⌥←/→ for the day, ⌃⌥↑/↓ for the time,
  and likewise ⌃⌥Home, ⌃⌥0 and ⌃⌥H. ⌥←/→ is how that platform has moved and selected by word for as
  long as it has existed, and the screen was quietly swallowing it — it belongs to the text field
  again, wherever the cursor is. Nothing changes on Windows, Linux, Android or a browser on any of
  them, where Alt is nobody's and the keys stay as they were; the list of shortcuts (F1) writes out
  whichever of the two the machine you are reading it on actually answers to. An iPad with a
  keyboard counts as a Mac here, since its text editing does

## Version 1.2.3 (06.08.2026)

- saving an edited bit no longer throws the list around. The day filter and the date the composer
  writes with used to be one and the same, so starting an edit filtered the list to that bit's day
  and saving it lifted the filter again — the list was rebuilt twice around every edit, and the
  scroll position had nothing left to hold on to. They are two things now: the day lists (the day
  strip and the sidebar) filter, the date chip dates, and editing only ever borrows the chip. So
  the list stays exactly where it was, and a day you picked stays picked
- dates and times are written the way your own system writes them, instead of the way this app used
  to insist on. Half past two in the afternoon is *2:30 PM* where that is how the clock is read and
  *14:30* where it isn't; the sixth of August is *8/6* or *06.08* depending on which comes first
  where you are; and weekdays and months are named in your language. That covers the time on every
  bit, the day strip, the sidebar, the day headings over the list, the date chip, *last synced*, and
  the heading on a day copied to the clipboard. Each platform is asked in its own words — Android
  and
  iOS through ICU, the browser through `Intl`, desktop through Java's locale data
- what is *stored* has not changed and will not: the database, the sync files and the logs stay ISO,
  so a bit written on a German phone still reads back as the same day on an American one
- the shortcuts are written down: **Alt+H** (or F1) shows the whole list over the screen, grouped by
  what it acts on, and Esc or another Alt+H puts it away. While it is up the keyboard belongs to it,
  so nothing lands in the text field behind it. The keyboard icon that opens it appears only where
  there is a keyboard to use — never on a phone, and on a tablet as soon as one is plugged in and
  used
- days can be switched from the keyboard. Alt+←/→ steps back and forth a day the way the day strip
  runs — today at the right, the month behind it to the left — and holding one down walks through
  the
  month; Shift skips the empty days and goes straight to the next day that has bits. Alt+Home is
  today again, Alt+0 is all days, and Esc backs out of whatever you are in: the edit first, and
  after
  that everything you had picked at all — the day, the date and the time, so one press hands both
  back to the clock. Alt+PageUp/PageDown do what the arrows do, for anyone who wants Alt+←/→ left
  alone for jumping words. All of it works whether or not the text field has the cursor, and a step
  taken while writing leaves the list where it is — it re-dates the bit in hand, and only follows
  along if you were already looking at a single day
- Alt+↑/↓ nudges the time from anywhere on the screen too, not only from inside the text field. It
  and the day keys are one gesture — Alt and the arrows, the time one way and the day the other — so
  they are now one thing in one place, and neither of them needs the cursor to be anywhere in
  particular
- the tree of the empty state and the seedling above the oldest bit are much larger now, and each
  has a little scene of its own: leaves, pine nuts and — over the seedling — water and sparkles
  drift down behind it and out of the bottom, forever and never quite the same way twice. All emoji,
  no artwork. The tree takes only the room the pane has to spare beyond its message and steps aside
  altogether on a window as short as a phone in landscape with the keyboard up
- something lives in that tree. Every half minute or so a squirrel or a bird comes down it, bobbing
  along, and then bolts off to one side and out of the scene. Rarely enough that catching one still
  feels like catching it
- the seedling gets visitors of its own: a ladybug or a mouse wanders in from one side, goes round
  the foot of it — behind it and then in front, twice round — and wanders off out the other side
- a new bit that lands right below the last one in view is scrolled to properly. A few pixels of it
  peeking over the bottom edge counted as visible, so the list left it there, half under the input —
  the one place a bit just written must not be. It now has to be in view in full, clear of the
  bottom edge and of the top bar floating over the list, before the jump is considered unnecessary
- the date chip goes back to what it showed before an edit when the edit is saved or cancelled,
  instead of falling back to "now" — a day picked for the bits you are writing survives editing an
  older one in passing
- a bit written for a day other than the one the list is filtered to now pulls the filter to that
  day, so it can't be saved into thin air
- **back to now** next to the date chip no longer lifts the day filter as well; it clears the chip,
  which is what it sits next to. Use the selected day again (or *All days* in the sidebar) to see
  every day
- a long bit now wraps into several lines instead of scrolling sideways through a single one. How
  many depends on the room actually left — the window minus the keyboard, so a phone counts what it
  has while typing, not what it has with the keyboard away: two lines on a phone held upright or a
  small desktop window, five on a tall one, and a single line whenever what is left is as short as
  a phone in landscape
- the time nudge moved from ↑/↓ to **Alt+↑/↓** (⌥ on macOS), because in a field that can hold
  several lines the bare arrows belong to the cursor. Alt+↑/↓ move the time by 15 minutes wherever
  the cursor happens to be and leave it there, so a bit can be timed mid-sentence and typed on
- Enter submits the bit; Shift+Enter puts a line break in it

## Version 1.2.2 (03.08.2026)

- while typing a bit, ↑/↓ move its time by 15 minutes (one time slider step); ←/→ still move the
  cursor. Times off the quarter hour snap in the direction pressed (from 12:07, ↑ 12:15, ↓ 12:00),
  and 00:00/23:45 hold instead of rolling into the next day. Needs a hardware keyboard
- the bit list no longer jumps to the bottom while a sync is running; it re-anchors to the newest
  bit only on first load and when the day filter changes. Saving a bit the current filter hides no
  longer leaves it jumping on every update either
- on a wide window the bit list also scrolls when the drag starts in the empty margins beside the
  bits, not just on the bits themselves
- signing in to Nextcloud no longer stalls silently when the browser doesn't open (no AWT `BROWSE`
  support on Linux, a blocked popup on the web, a device with no browser). The sync panel shows the
  login URL with *Copy link* and *Try again*, and keeps polling, so the sign-in completes as soon as
  the URL is reached by any route
- fixed the weaker-credential-protection warning showed a literal backslash in "won\'t" — Compose
  resources don't unescape Android's `\'`

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