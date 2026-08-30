# Conifer — App Functions

A single-screen note-taking app where a **Bit** is a short, dated note. All logic and Compose UI
live in `shared/`; the platform modules (`androidApp`, `desktopApp`, `iosApp`, `webApp`) are thin
launchers.

## Core features

- **Create a Bit** — type into the "New Bit" field and tap the check icon (or IME "Done"). Text is
  trimmed; blank input is ignored. Same-date inserts get +1ms so ordering is preserved
  (`BitsViewModel.onClickAdd`, `BitsRepository.add`, `BitsPane.NewBitText`).
- **Edit a Bit** — double-click a bit card or use its "⋮" menu → Edit. The text loads into the
  input field (label switches to "Edit Bit"), date/time load into the selector; save updates in
  place, cancel discards. Id and `createdAt` are kept
  (`BitsViewModel.startEditing`/`cancelEdit`, `BitsRepository.update`). Editing never touches the
  day filter, and ending it puts back whatever date/time the selector held before, so the list is
  not rebuilt (and its scroll position not lost) around an edit.
- **Delete a Bit** — "⋮" menu → Delete, with a confirmation dialog before permanent deletion.
- **Grouping by day with sticky headers** — bits are grouped per calendar day, newest first
  (`ORDER BY date DESC, created_at DESC`); each card shows only its time of day.
- **Date picker** — expandable selector with a horizontal row of the last 30 days, showing the
  locale's short weekday + day and month (see **Localized dates and times**), today highlighted.
  Selecting a day filters the list and dates
  new/edited bits; re-tapping deselects. The two are separate state
  (`BitsPaneState.filterDate`/`composerDate`): the day lists highlight the filtered day, the chip
  shows the day being written to, and a bit written for another day pulls the filter along with it.
  Each day shows 1–3 dots: one for any bit that day, two
  once both morning (before 12:00) and afternoon have at least one, three past that once the day
  holds more than 3 bits (`DatedBits.dots`).
- **Time picker** — slider in 15-minute steps (00:00–23:45) for the bit's time of day.
- **Localized dates and times** — everything the reader sees is spelled by the platform:
  `DateTimeFormats` (five methods — time of day, short weekday, day-and-month, whole date, date with
  weekday) is passed to `ConiferApp.initialize` like the other platform interfaces, and reaches the
  UI
  through `LocalDateTimeFormats`, provided once in `AppContent`; `BitsViewModel` takes it by
  constructor for the clipboard heading. Covers bit times, both day lists, the sticky day headers,
  the
  date chip, the time slider's label, "last synced" and the copied heading. Per platform:
  `java.time`
  localized styles on desktop (`JvmDateTimeFormats`), **ICU** on Android (`android.icu`, skeletons
  `jm`/`Md`/`EEE`) and iOS (`NSDateFormatter` templates), `Intl.DateTimeFormat` on web. The default
  is
  `IsoDateTimeFormats` — ISO dates, 24-hour times, English weekdays — which is what previews and
  tests
  get, so a rendering does not depend on the machine it runs on. Storage is deliberately *not*
  localized: the database converters, the sync files (`ReadableRenderer`), the log timestamps and
  the
  sync debug rows all stay ISO, since those are read by machines and by other devices.
- **Keyboard shortcuts** — all of the screen's shortcuts live in one place, `handleShortcut` in
  `KeyboardShortcuts.kt`, and so work with or without the text field focused. `Alt`+the arrows
  adjust what the
  composer will stamp on the bit: `↑/↓` the time by one slider slot, `←/→` the day — left is older,
  matching the strip's `reverseLayout` — with `Alt+PageUp/PageDown` as a synonym for the day pair
  (`Alt+←/→` is word-jump on macOS). `Shift+Alt+←/→` skips to the nearest day that has bits,
  `Alt+T`/`Alt+Home` return to today, `Alt+N`/`Alt+End` hand the time back to the clock without
  touching either day (`resetTime`, the time's counterpart of `selectToday`) and `Alt+0` shows all
  days (keeping a chosen time, as the day lists' "All days" does). Those two have a letter each
  because Apple's laptops have no Home/End keys at all — there they are `fn`+`←/→`, which with the
  chord on top is unusable — so, as with `Alt+H`, the letter is the spelling that works on every
  keyboard and is the one the overlay lists first. `Esc` cancels an edit, or failing that resets the
  selection — the filter, the
  composer date *and* a nudged time, so one press puts the clock back in charge of both
  (`resetSelection`; a nudged time alone is enough to arm it, see `BitsPaneState.hasSelection`). The
  day keys are clamped to `DAY_LIST_DAYS` so the selected day is always one a day
  list can mark (`dateShiftedBy`, `nearestDateWithBits`); unlike tapping a day they move only the
  composer date and pull the filter along solely when one is already set, so stepping days mid-edit
  re-dates the bit in hand without throwing the list about. The field keeps only `Enter` to save and
  `Shift+Enter` for a line break (`NewBitText`). Because a key event only reaches the focused node
  and
  its ancestors — and, with nothing focused, only key input *above* the root focus node — the pane
  holds a `focusTarget` of its own, taken once on the way in and immediately ceded to the field.
- **Shortcut overlay** — `Alt+H` (or `F1`) shows the whole list over the screen, grouped by what
  each
  key acts on; `Esc`, `Alt+H`, the Close button or a click off the card puts it away
  (`ShortcutsOverlay`, listed from `SHORTCUT_GROUPS` in the same file as `handleShortcut`, so a key
  wired up and never documented is visible in one screen). It sits in the tree rather than in a
  `Dialog`: a dialog is its own window and would take focus with it, leaving the key that opened the
  overlay unable to close it — in the tree the pane keeps the keyboard, and while the overlay is up
  `handleShortcut` swallows every other key so nothing lands in the text field behind it. The
  top-bar
  icon that opens it appears only where a keyboard exists to use it: `Platform.hasHardwareKeyboard`
  (true on desktop and web, false on Android/iOS) or, failing that, the first `Alt` event seen —
  which
  is how a tablet with a keyboard attached later earns the icon without the platform being asked
  something it cannot answer. `BitsRoute` injects the platform; the pane takes a plain
  `hasHardwareKeyboard` argument so previews and tests stay ordinary composables.
- **"Now" default with live clock** — without an explicit selection, the current instant is used;
  the displayed time ticks each minute and rolls the date over at midnight.
- **"Beginning of your bits" marker** — a fixed 🌱 note pinned above the oldest visible day
  whenever the list has at least one bit (even while filtered to a single day); distinct from the
  empty-state message, which only shows when there's nothing to display at all
  (`BitsPane.BeginningNote`).
- **Ambient emoji scenes** — the empty state's 🌲 and the beginning marker's 🌱 are large and sway
  from the base, with smaller emoji (leaves and pine nuts; water and sparkles over the seedling)
  falling in staggered lanes behind them and out of the bottom (`EmojiFallScene`). Emoji only, no
  assets; each fall is a single `graphicsLayer`, so a frame of it costs no recomposition. The empty
  state's scene is sized from what the pane has left over after its message, and is dropped
  altogether when that isn't enough — a phone in landscape with the keyboard up.
- **Animal visitors** — each scene gets the kind of visit its plant allows, every ~20–35s, off a
  long cycle of which only ~10–20% is the visit itself; the rest is an empty scene. Both tables are
  hand-picked rather than random, so a scene is identical on every open and in every preview, and in
  each one exactly one animal sits mid-visit at the animations' initial values, so even a still
  frame
  (preview, test) has one. The 🌲 is climbed down: 🐿️/🐦 drop in over the top, come down one flank in
  front of the tree and bolt off sideways and out through the bottom clip
  (`Scamper`/`ScamperingAnimal`). The 🌱 is circled instead: 🐞/🐁 walk in from one side, go 1.5 times
  round its foot on a flattened ellipse and out the other side (`Round`/`CirclingAnimal`). To get
  the
  far half of that loop *behind* the plant, each circling animal is drawn twice — once before the
  big
  emoji and once after — sharing one hoisted animation, each copy `alpha = 0` outside its half; they
  hand over at the two widest points of the loop, where nothing overlaps.

## Secondary features

- **Copy a day to clipboard** — copy icon in each date header exports that day as plain text
  (`Bits of <weekday>, <date>` in the reader's own locale + one line per bit, chronological). Only
  shown when the
  platform provides a `ClipboardController`.
- **Notification permission prompt** — in-app card with rationale + grant button when permission
  is missing (Android only in practice).
- **Custom typography** — handwriting-style "Story Script" for titles, "Lato" for body text.
- There is **no settings screen** and no markdown *rendering* in the app.

## Platform-specific

| Platform                      | Features                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Android**                   | Quick-add via conversation notification with inline reply — replying creates a bit even with the app closed, and the notification keeps the last 3 bits as history (`NotificationController`, `ConversationBroadcastReceiver`). POST_NOTIFICATIONS permission handling, edge-to-edge UI, system clipboard, bundled SQLite driver.                                                                                                               |
| **Desktop (JVM)**             | Compose window with app icon/title; AWT clipboard; database, preferences, logs and reports in one per-user folder outside the installed app — `~/Library/Application Support/Conifer`, `%LOCALAPPDATA%\Conifer`, `$XDG_DATA_HOME/conifer` (`JvmDataFolder.kt`, which also moves data out of the pre-1.2.6 folder beside the jar; a `run` from Gradle gets a `-dev` folder of its own, and `-Dconifer.dataFolder=` overrides). No notifications. |
| **iOS**                       | Compose UIViewController bridge; `UIPasteboard` clipboard; DB in the documents directory.                                                                                                                                                                                                                                                                                                                                                       |
| **Web (wasmJs, in progress)** | `ComposeViewport` entry point; Room via `WebWorkerSQLiteDriver` with the vendored SQLite web worker, persisting to OPFS. No clipboard/notifications yet.                                                                                                                                                                                                                                                                                        |

## Data model

`Bit` (table `bits`) — the columns the UI actually uses, plus sync bookkeeping the UI never
touches (see [Nextcloud sync](#nextcloud-sync-implemented-but-not-reachable-from-the-app) below):

| Column        | Type            | Notes                                                                                                    |
|---------------|-----------------|----------------------------------------------------------------------------------------------------------|
| `id`          | `String`        | primary key, random UUID                                                                                 |
| `text`        | `String`        | note content, trimmed on save                                                                            |
| `created_at`  | `Instant`       | immutable insertion timestamp; also fixes the bit's sync bucket (`yyyy-MM`), even if re-dated            |
| `date`        | `LocalDateTime` | zoneless user-facing date/time (grouping/sorting key); doesn't drift when the device's time zone changes |
| `modified_at` | `Instant`       | *sync only* — last-write-wins basis                                                                      |
| `modified_by` | `String`        | *sync only* — device id, LWW tiebreaker                                                                  |
| `deleted`     | `Boolean`       | *sync only* — tombstone flag; hidden from all UI queries regardless                                      |
| `payload`     | `String?`       | *sync only* — last known server-side JSON for this bit, `null` until ever synced                         |
| `dirty`       | `Boolean`       | *sync only* — locally changed, not yet pushed                                                            |
| `remote_etag` | `String?`       | *sync only* — server file ETag as of the last pull/push                                                  |
| `bucket`      | `String`        | *sync only* — `yyyy-MM` derived once from `created_at`                                                   |

Room 3, schema v4. Auto-migrations: 1→2 dropped `concerned_at` and initialized `date = created_at`
(deprecated `Instant`-based `date`, since replaced); 2→3 converted `date` to a zoneless
`LocalDateTime`; 3→4 added all the sync bookkeeping columns above, defaulting existing rows to
dirty (queued for a first push once sync is ever wired in). `BitDao` — what the UI actually
uses — offers `getAllBits()` as a Flow (`WHERE deleted = 0`), `hasBitAtDate`, `upsert`, `delete`,
and `getBitsByIds` (Android notification history). A separate `SyncDao` covers everything sync
needs; the UI never touches it.

Sync also owns three more tables the UI never sees: `bucket_state` (per-month folder ETags),
`readable_state` (content hash of each day's last-uploaded Markdown rendering), and
`readable_pending` (days whose rendering is queued to (re-)upload).

## Nextcloud sync (implemented, but not reachable from the app)

Everything `docs/nexcloud_sync_spec.md` describes for syncing bits across devices via a stock
Nextcloud instance exists as thoroughly-tested library code in `shared/.../sync/` and
`.../auth/` — but **nothing in the running app calls any of it**. No settings screen, no DI
wiring, no button, no background trigger. A user cannot turn sync on today; in practice the app
is still 100% local-only, on every platform.

What exists, as library code:

- `RemoteStore` / `KtorWebDavStore` — a WebDAV transport (PROPFIND/GET/PUT/MKCOL/DELETE, bulk
  upload with a sequential fallback), verified against a real containerized Nextcloud.
- `SyncEngine` — fast-path check, pull (parallel downloads, ≤6 at a time), push, tombstone GC,
  and finalize, resolving conflicts via `MergePolicy` (last-write-wins, deterministic tiebreak on
  device id).
- `ReadableRenderer` / `ReadableModule` — a deterministic, human-readable Markdown file per day
  next to the machine JSON, skipped when the rendering hasn't actually changed.
- `GarbageCollector` — physically deletes tombstones 90 days after they were pushed clean, at
  most once a week.
- `LoginFlowV2` / `Credentials` — Nextcloud's poll-based login flow (the app never sees the real
  password) and KSafe-encrypted app-password storage, hardware-backed per platform.

What's missing before a user could actually use any of this: a settings/login screen, Koin
bindings for the classes above, and a trigger (app foreground / manual refresh /
debounced-after-edit, per the spec) that ever calls `SyncEngine.sync()`. `docs/conifer-mockup.html`
has a proposed sync entry point/settings sheet with no code behind it yet.

See `sync_review.md` at the repo root for the full review of the sync implementation, including
one known open correctness gap: a device returning from a long stretch offline has only a
stopgap against resurrecting a post another device deleted-and-garbage-collected while it was
away, not the full spec mitigation (which needs a UI to ask the user).

## Build-level extras (not runtime features)

- **NextCloud uploader** — Gradle task uploads release artifacts via WebDAV, zipping APKs since
  Android browsers block raw `.apk` downloads (`buildSrc/.../UploadToNextcloud.kt`).
- **Desktop release packaging** — `:desktopApp:buildDesktopRelease` into `./releases`. The release
  build shrinks with ProGuard (`desktopApp/compose-desktop.pro`, optimization off); every rule in
  that file is there because something is reached by *name* rather than by reference — Room's
  generated implementation, Ktor's ServiceLoader engine, and JNA, whose native dispatch looks its
  methods up through JNI, so shrinking it left KSafe unable to load the OS secret store and quietly
  keeping its key in a file. Anything reached reflectively or from native code needs a rule here,
  and
  only a packaged build shows the difference.