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
- **Calendar** — a button beside the date chip, and beside the sidebar's "Days" heading in the
  two-pane layout, opens Material's `DatePickerDialog` for a day too far back to scroll to
  (`DayPickerDialog`). Picking a day does what a day chip does — filter the list, date the bit — and
  additionally grows the day lists to count back far enough to mark it (`BitsViewModel.pickDate`,
  through the same `movedTo` the date hotkeys use); there is no deselecting, a calendar having no
  way to say "not this day either". Days after today are not selectable (`DaysUpTo`), the year grid
  spans the oldest day with bits — and five years past it — up to this year (`yearRangeBackTo`), and
  where there is a hardware keyboard it opens on the typed field rather than the grid.
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
- **Sync with your own Nextcloud** — optional, and off until an account is connected: the cloud icon
  in the app bar is the whole of it, opening a pane, a sheet or a popover depending on the window
  (`SyncStatusIcon`, `SyncPresentation`). Signing in goes through Login Flow v2 in the browser, so
  the app only ever holds an app-scoped token; from then on bits travel between that person's own
  devices through their own server, and a round runs on request, after a connect, ten seconds after
  an edit settles, on coming back to the app and every five minutes while it is on screen. The whole
  of it is its own section below — see
  [Nextcloud sync](#nextcloud-sync-optional-off-until-connected).

## Secondary features

- **Copy a day to clipboard** — copy icon in each date header exports that day as plain text
  (`Bits of <weekday>, <date>` in the reader's own locale + one line per bit, chronological). Only
  shown when the
  platform provides a `ClipboardController`.
- **Notification permission prompt** — in-app card with rationale + grant button when permission
  is missing (Android only in practice).
- **Custom typography** — handwriting-style "Story Script" for titles, "Lato" for body text.
- There is **no settings screen** beyond sync's own sheet, and no markdown *rendering* in the
  app.

## Platform-specific

Everything above is common code and behaves the same everywhere. What differs is what each platform
lends the app — and what it has not got to lend.

| Platform          | What it has here                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Android**       | Quick-add via a conversation notification with inline reply — replying creates a bit even with the app closed, and the notification keeps the last 3 bits as history (`NotificationController`, `ConversationBroadcastReceiver`); POST_NOTIFICATIONS handling and the in-app rationale card. Edge-to-edge UI, system clipboard, share sheet for a report (as a `content://` file out of `cacheDir/reports`), bundled SQLite driver, Ktor on OkHttp, ICU date formats. Sync: the key for the stored app password in the Android Keystore, the login URL opened with an `ACTION_VIEW` intent. Put away and brought back with the activity lifecycle, counted across activities.                                                                                                                                                                                                                                                                                                                                                                               |
| **Desktop (JVM)** | Compose window with its own icon and title; AWT clipboard; database, preferences, `logs/` and `reports/` in one per-user folder outside the installed app — `~/Library/Application Support/Conifer`, `%LOCALAPPDATA%\Conifer`, `$XDG_DATA_HOME/conifer` (`JvmDataFolder.kt`, which also moves data out of the pre-1.2.6 folder beside the jar; a `run` from Gradle gets a `-dev` folder of its own, and `-Dconifer.dataFolder=` overrides). A report is written into `reports/` and that folder opened in the file manager, a desktop having no share sheet. `java.time` date formats, Ktor on OkHttp, bundled SQLite driver. Sync: the key in the OS secret store (macOS Keychain, Windows DPAPI, Linux Secret Service), the login URL through AWT's `Desktop.browse` — which Linux JVMs routinely refuse even where a browser opens fine from a shell, and then the sheet offers the URL instead. The run ends on a JVM shutdown hook: a closed window, `exitApplication`, Ctrl+C or a `SIGTERM`, but deliberately not a `SIGKILL`. **No notifications.** |
| **iOS**           | Compose `UIViewController` bridge (`ConiferAppViewController`, `IosConiferApp.initialize()`); `UIPasteboard` clipboard; database in the documents directory; the report through `UIActivityViewController`, which is how anything leaves an iPhone at all. `NSDateFormatter` templates for dates, Ktor on Darwin. Sync: the key in the Keychain, the login URL through `UIApplication.openURL`. Put away and brought back on the background/foreground notifications, since `applicationWillTerminate` is not called for a suspended app the system reclaims. **No notifications, no quick-add.** Functional but not in daily use, and not released: only the simulator test suite exercises it.                                                                                                                                                                                                                                                                                                                                                            |
| **Web (wasmJs)**  | `ComposeViewport` entry point; Room through `WebWorkerSQLiteDriver` and the vendored SQLite web worker, persisting to **OPFS — which needs the page cross-origin isolated**, so the dev server sends `COOP`/`COEP` (`webApp/webpack.config.d/`) and a production host must send them too. Logs, preferences and the credentials all share the origin's `localStorage`, a handful of megabytes between them, which is why fewer and shorter logs are kept than on a disk (`WasmLogFileInitializer`, `MAX_WEB_LOG_RUNS`/`MAX_WEB_LOG_CHARS`). A report is handed over as a download (`data:` URL). `Intl.DateTimeFormat` for dates, Ktor on the JS engine. Sync: the login URL through `window.open`, which a browser may refuse outside a user gesture — the sheet then offers it to copy — and the credential key held by the browser (a non-extractable WebCrypto key) rather than by an OS store. The run says goodbye on `pagehide`, `pageshow` bringing it back. **No clipboard, no notifications.**                                                    |

## Data model

`Bit` (table `bits`) — the columns the UI actually uses, plus sync bookkeeping the UI never
touches (see [Nextcloud sync](#nextcloud-sync-optional-off-until-connected) below):

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
dirty, so a first connect pushes everything already written. `BitDao` — what the UI actually
uses — offers `getAllBits()` as a Flow (`WHERE deleted = 0`), `hasBitAtDate`, `upsert`, `delete`,
and `getBitsByIds` (Android notification history). A separate `SyncDao` covers everything sync
needs; the UI never touches it.

Sync also owns three more tables the UI never sees: `bucket_state` (per-month folder ETags),
`readable_state` (content hash of each day's last-uploaded Markdown rendering), and
`readable_pending` (days whose rendering is queued to (re-)upload).

## Nextcloud sync (optional, off until connected)

Bits across a person's own devices, through a stock Nextcloud instance over WebDAV — no server-side
code of any kind, no account of Conifer's own. It is opt-in and off until an account is connected:
until then nothing here touches the network and the app is the local-only app it has always been
(`SyncCoordinator.state` stays `Disconnected`). `docs/nexcloud_sync_spec.md` is the specification
this implements.

### On the screen

- **The cloud icon in the app bar** (`SyncStatusIcon`) — muted while disconnected, tinted once
  connected, spinning while a round is in flight. It is the whole entry point: pressing it opens the
  sync surface, pressing it again closes it.
- **Which surface that is** is `SyncPresentation`, decided by the window size classes and
  overridable like the rest of the layout: `Pane` where the window is wide *and* tall enough to hand
  sync a third pane beside the bits — the mirror of the day sidebar on the other side — and `Sheet`
  everywhere else, where a connected app gets `SyncDebugPopover` anchored on the icon and
  `SyncSettingsSheet` over the window on request or whenever there is no account yet.
  `SyncUiState.isSyncOpen` only records that the user asked to see sync; the presentation is what
  turns that into a surface.
- **Connecting** (`DisconnectedContent`) — a server field (the scheme may be left off, `https://` is
  assumed, a trailing slash trimmed) and one button, which starts Login Flow v2 and opens the
  browser. Conifer never sees the account password, only the app-scoped token the flow hands back.
  A browser that would not open does not lose the login: the URL is offered to copy or to open
  again, and the poll started with it goes on waiting throughout (`ConnectingContent`,
  `SyncCoordinator.retryOpenLoginUrl`).
- **A warning before anything is written** (`InsecureKeyWarning`) — where the encryption key has no
  OS-backed store to live in (a headless Linux with no keyring, a browser), the sheet says so and
  names what would hold it instead, *before* connecting: once the username and app password are
  written they are written through that same weaker custody either way
  (`SyncCoordinator.insecureKeyCustody`).
- **Once connected** (`ConnectedContent`) — the account, "last synced …" in the reader's own locale,
  **Sync now**, the **app folder** field and **Disconnect**, which forgets the credentials but
  deliberately keeps the local sync state, so reconnecting the same account resumes rather than
  pulling everything down again.
- **Changing the app folder** re-syncs into it wholesale: every bit goes back to dirty with no
  remote identity and the bucket/rendering caches are cleared
  (`SyncDao.resetForNewAppRoot`), because only dirty bits are ever pushed and the new folder is
  empty. It then syncs at once instead of waiting for the next trigger. Blank input is ignored
  rather than stored, and a path equal to the current one is a no-op, so neither can force a
  pointless full re-push.
- **Troubleshooting details** (`SyncDebugPopover` → `SyncDebugDetails`) — server, device id, app
  root, last sync, root ETag, last GC, last error and the last round's tally (pushed · pulled ·
  merged). Those rows are spelled ISO on purpose: they are read off the screen into bug reports and
  compared against server-side timestamps, where a reader's locale would only be in the way — the
  status line above them is localized. Never a bit's content.

### When it runs

`SyncViewModel` owns the triggers spec §5 asks for; `SyncCoordinator` itself only ever runs when
told to, which is what keeps sync opt-in whatever is scheduled. A round starts on **Sync now**,
**immediately after a connect succeeds**, **ten seconds after a local edit settles** (debounced),
**when the app comes back to the front** (`AppPresence`) and **every five minutes while it is on
screen**. Rounds never overlap, and one that starts while disconnected is skipped. Each says in the
log what started it (`SyncTrigger`) — "it synced twice and then stopped" is otherwise unanswerable.

### Underneath

- `SyncCoordinator` — connect, disconnect, run, app root, debug snapshot: the layer between the
  engine (which knows nothing about the UI) and the view model (which knows nothing about WebDAV or
  the login flow).
- `RemoteStore` / `KtorWebDavStore` — a WebDAV transport (PROPFIND/GET/PUT/MKCOL/DELETE, bulk
  upload with a sequential fallback), verified against a real containerized Nextcloud.
- `SyncEngine` — fast-path check, pull (parallel downloads, ≤6 at a time), push, tombstone GC,
  and finalize, resolving conflicts via `MergePolicy` (last-write-wins, deterministic tiebreak on
  device id).
- `ReadableRenderer` / `ReadableModule` — a deterministic, human-readable Markdown file per day
  next to the machine JSON, skipped when the rendering hasn't actually changed. Write-only: it is
  generated, never read back, never merged.
- `GarbageCollector` — physically deletes tombstones 90 days after they were pushed clean, at
  most once a week.
- `LoginFlowV2` / `Credentials` — Nextcloud's poll-based login flow (the app never sees the real
  password) and KSafe-encrypted app-password storage, hardware-backed per platform.

See `docs/sync_review.md` for the full review of the sync implementation, including the one known
open correctness gap: a device returning from a long stretch offline has only a stopgap against
resurrecting a bit another device deleted-and-garbage-collected while it was away — it refuses to
push, and the bit stays dirty — rather than the full spec mitigation, which needs a surface to ask
the user with and does not have one.

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