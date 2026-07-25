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
  (`BitsViewModel.startEditing`/`cancelEdit`, `BitsRepository.update`).
- **Delete a Bit** — "⋮" menu → Delete, with a confirmation dialog before permanent deletion.
- **Grouping by day with sticky headers** — bits are grouped per calendar day, newest first
  (`ORDER BY date DESC, created_at DESC`); each card shows only its time of day.
- **Date picker** — expandable selector with a horizontal row of the last 30 days, showing
  3-letter weekday + day.month, today highlighted. Selecting a day filters the list and dates
  new/edited bits; re-tapping deselects. Each day shows 1–3 dots: one for any bit that day, two
  once both morning (before 12:00) and afternoon have at least one, three past that once the day
  holds more than 3 bits (`DatedBits.dots`).
- **Time picker** — slider in 15-minute steps (00:00–23:45) for the bit's time of day.
- **"Now" default with live clock** — without an explicit selection, the current instant is used;
  the displayed time ticks each minute and rolls the date over at midnight.
- **"Beginning of your bits" marker** — a fixed 🌱 note pinned above the oldest visible day
  whenever the list has at least one bit (even while filtered to a single day); distinct from the
  empty-state message, which only shows when there's nothing to display at all
  (`BitsPane.BeginningNote`).

## Secondary features

- **Copy a day to clipboard** — copy icon in each date header exports that day as a markdown block
  (`##### Bits of <weekday>, <date>:` + one line per bit, chronological). Only shown when the
  platform provides a `ClipboardController`.
- **Notification permission prompt** — in-app card with rationale + grant button when permission
  is missing (Android only in practice).
- **Custom typography** — handwriting-style "Story Script" for titles, "Lato" for body text.
- There is **no settings screen** and no markdown *rendering* in the app.

## Platform-specific

| Platform                      | Features                                                                                                                                                                                                                                                                                                                          |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Android**                   | Quick-add via conversation notification with inline reply — replying creates a bit even with the app closed, and the notification keeps the last 3 bits as history (`NotificationController`, `ConversationBroadcastReceiver`). POST_NOTIFICATIONS permission handling, edge-to-edge UI, system clipboard, bundled SQLite driver. |
| **Desktop (JVM)**             | Compose window with app icon/title; AWT clipboard; DB in a `data/` folder next to the jar. No notifications.                                                                                                                                                                                                                      |
| **iOS**                       | Compose UIViewController bridge; `UIPasteboard` clipboard; DB in the documents directory.                                                                                                                                                                                                                                         |
| **Web (wasmJs, in progress)** | `ComposeViewport` entry point; Room via `WebWorkerSQLiteDriver` with the vendored SQLite web worker, persisting to OPFS. No clipboard/notifications yet.                                                                                                                                                                          |

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
- **Desktop release packaging** — `:desktopApp:buildDesktopRelease` into `./releases`.