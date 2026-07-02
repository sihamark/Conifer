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
  3-letter weekday + day.month, a dot on days that have entries, today highlighted. Selecting a
  day filters the list and dates new/edited bits; re-tapping deselects.
- **Time picker** — slider in 15-minute steps (00:00–23:45) for the bit's time of day.
- **"Now" default with live clock** — without an explicit selection, the current instant is used;
  the displayed time ticks each minute and rolls the date over at midnight.

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

`Bit` (table `bits`):

| Column       | Type      | Notes                                                                  |
|--------------|-----------|------------------------------------------------------------------------|
| `id`         | `String`  | primary key, random UUID                                               |
| `text`       | `String`  | note content, trimmed on save                                          |
| `created_at` | `Instant` | insertion timestamp                                                    |
| `date`       | `Instant` | user-facing instant used for grouping/sorting, defaults to `createdAt` |

Room 3, schema v2, with an auto-migration 1→2 that dropped `concerned_at` and initialized
`date = created_at`. The DAO (`BitDao`) offers `getAllBits()` as a Flow, `hasBitAtDate`, `upsert`,
`delete`, and `getBitsByIds` (used by the Android notification history).

## Build-level extras (not runtime features)

- **NextCloud uploader** — Gradle task uploads release artifacts via WebDAV, zipping APKs since
  Android browsers block raw `.apk` downloads (`buildSrc/.../UploadToNextcloud.kt`).
- **Desktop release packaging** — `:desktopApp:buildDesktopRelease` into `./releases`.