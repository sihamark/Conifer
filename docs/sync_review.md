# Nextcloud sync — review findings

Review of `shared/src/*/kotlin/eu/heha/conifer/{sync,auth}` and the sync-related pieces of
`model/database`, `prefs`, and DI, against `docs/nexcloud_sync_spec.md`. Ranked by importance.

**Re-evaluated against the current implementation (2026-07-26):** findings #2, #3, and #5 are
confirmed still fixed/resolved; #1 and #4 are confirmed still open, unchanged in nature. #5 in
particular — at the time the single biggest gap — is now resolved and its section rewritten below
to describe what shipped instead of what was missing.

## 1. Resurrection risk isn't mitigated (spec §8) **partially fixed (stopgap)**

`SyncEngine.pushModified`'s 412-conflict handler used to unconditionally re-upload a post as new
once its file was gone:

```kotlin
val currentEtag = remoteStore.etag(path)
if (currentEtag == null) {
    // The file vanished between our failed PUT and this check - push it as new.
    pushNewSingle(postsRoot, bit.copy(remoteEtag = null))
    return
}
```

Since stage 5 (GC) physically deletes old tombstones, this path is genuinely reachable: a device
offline >90 days that still has a dirty edit on a post another device deleted-and-GC'd in the
meantime would silently resurrect it. The spec's full prescribed mitigation is: "if the own device
was offline > 90 days, force a full pull before the first push, and flag dirty posts whose remote
file is missing **and** which were created before `lastSyncAt` for manual confirmation."

**What's implemented (option 1 of 3 considered, see below): detect-and-stall.**
`SyncEngine.isReturningFromLongOffline()` compares `SyncPrefs.lastSyncAt()` against
`GarbageCollector.TOMBSTONE_RETENTION` (the same 90-day window GC uses — made `internal` so both
share one source of truth). When a bit's file has vanished **and** this device is returning from
a long-offline gap, `pushModified` no longer resurrects it — it logs a warning and leaves the bit
dirty, same as the existing "gave up after too many conflicts" path. Covered by
`SyncEngineTest.aBitWhoseFileWasGcdWhileThisDeviceWasLongOfflineIsNotResurrected`.

**What's still missing — deliberately, not an oversight:** this is the safe half only, not a
resolution. There's a `TODO` on the exact spot in `SyncEngine.pushModified` (search
`spec §8 resurrection mitigation`). The options considered, for whoever picks this back up:

1. **Detect-and-stall** *(implemented)* — smallest change, no schema/UI. Downside: the edit is now
   stuck dirty forever with no way to clear it, since nothing surfaces it anywhere.
2. **Detect-and-auto-resolve** — same detection, but instead of stalling, accept the remote
   deletion as authoritative: mark the local bit deleted, clear dirty. Converges on its own, but
   silently discards the user's edit without asking — a different flavor of the same data-loss
   problem, just picked automatically instead of by accident.
3. **Full spec fix** — add a `needsConfirmation`-style column (Room schema bump + migration),
   detect the same condition, and park the bit there for a future settings/inbox screen to
   resolve. The only option matching spec's actual intent (a human decides), but it's a real
   schema change for a flag with zero payoff until that UI exists.

A settings/sync UI now exists (see #5), but only for connect/disconnect/app-root/debug info — it
has no conflict-inbox surface. Revisit option 3 once that surface exists.

## 2. Unknown JSON fields are dropped on re-push (spec §3.1) **fixed**

`BitJson.toBitOrNull` stamps the raw JSON into `Bit.payload` "so a future app version's unknown
fields survive" — but `SyncEngine.encode(bit)` called `bit.toJson()`, which rebuilt a `BitJson`
from scratch using only the fields this app version understands. `payload` was never
re-serialized. So if this device's copy won a merge and got re-pushed, any field a newer app
version added (e.g. a hypothetical `attachments`) was silently dropped from the server copy. Spec
§3.1 is explicit: "unknown fields must be preserved on merge (take the JSON as a whole, don't
rebuild it field by field)."

**Fixed:** `encode(bit)` now parses `bit.payload` (the last known server-side JSON, if any) into
a `JsonObject`, encodes `bit.toJson()` into another `JsonObject` for the fields this app owns, and
merges the two (`base + known`, so known fields win, everything else survives) before
serializing. A bit with no prior payload (never synced) just encodes its own fields, same as
before. Covered by `SyncEngineTest.pushPreservesUnknownJsonFieldsFromAnEarlierPull`.

*(Status: implemented as a follow-up to this review — see commit history.)*

## 3. Mechanical fixes (small, no design decisions needed) **completed**

- **`dataRoot/meta/manifest.json` was never created.** Spec §9 "First device / empty server":
  `mkdirs` for `posts/` and `meta/`, then create `manifest.json` with `{"schema": 1}` via
  `If-None-Match: *`.
- **Pull GETs were sequential, not parallelized.** Spec §4 and §9 both call for "parallelism ≤ 6"
  on pull.
- Stale doc comment in `KtorWebDavStore.kt`: "the sync engine (spec §5, not yet implemented)" —
  implemented since stage 3.

*(Status: implemented as a follow-up to this review — see commit history.)*

## 4. Test coverage gaps (code looks correct by inspection, just not exercised end-to-end) **fixed**

- Spec test case #5 (tombstone: delete on A → gone on B, day file re-rendered, then GC'd after 90
  days) had no single test stitching the whole pipeline together — the pieces were each tested
  separately (`MergePolicyTest` for tombstone-as-equals, `ReadableRendererTest`/`bitsForDay`'s
  `deleted = 0` filter, `GarbageCollectorTest` for physical deletion).
- Spec test case #7 ("no feedback loop") had no explicit 2-device test proving that writing
  readable files never perturbs `rootEtag`/the fast path — it was only implicitly covered.

**Fixed:** both gaps closed with two more tests in `SyncEngineTest.kt`, using the file's existing
two-simulated-device harness (`device()` + shared `FakeRemoteStore`) rather than any new test
infrastructure — these are pure sync-engine/data-layer correctness questions with no UI surface,
so nothing about them called for Compose UI testing.

- `aDeletedBitDisappearsOnAnotherDeviceThenIsPhysicallyRemovedByGc`: deletes on device A, syncs
  both devices, asserts device B's local row is a tombstone and its day file no longer mentions the
  bit while the server file still exists (I5: a tombstone, not a file DELETE) — then forces device
  B's GC cooldown to have elapsed and asserts a subsequent sync physically removes both the local
  row and the server file.
- `writingReadableFilesOnTwoDevicesNeverPerturbsEitherDevicesFastPath`: after both devices have
  each written their own copy of the same readable day file, wraps each device's next sync in
  `CountingRemoteStore` and asserts it costs exactly 1 request — proving neither device's own
  `appRoot` writes perturbed the `dataRoot/bits/` root ETag its own fast path depends on.

*(Status: implemented as a follow-up to this review — see commit history.)*

## 5. The big one: nothing is wired into the running app **fixed**

At the time of the original review: `KtorWebDavStore(`, `SyncEngine(`, and `LoginFlowV2(` had zero
call sites outside class declarations and tests. No DI bindings, no trigger anywhere (app
foreground / manual refresh / debounced-after-edit per spec §5), and no glue code connecting a
completed `LoginFlowV2.LoginResult` to `Credentials`/`SyncPrefs.setServerUrl()`. No settings/login
screen existed either, so none of the sync code ran in the shipped app.

**Fixed — all of it is now wired end to end:**

- **DI:** `SyncCoordinator` and `SyncViewModel` are both bound in Koin's `coreModule`
  (`di/DependencyModules.kt`), which `ConiferApp.kt` installs at real app startup — not just in
  tests.
- **UI composition root:** `ConiferApp.kt`'s top-level composable calls `BitsRoute`, which resolves
  `SyncViewModel` via `koinViewModel` alongside `BitsViewModel` and passes its state/actions into
  `BitsPane`. `BitsPane`'s `Topbar` renders `SyncStatusIcon` (`ui/SyncPane.kt`), the app bar's cloud
  icon, which opens `SyncSettingsSheet` (connect/disconnect, app-root config) and
  `SyncDebugPopover` (troubleshooting glance) — this **is** the settings/login screen that was
  missing.
- **Login Flow v2 → Credentials/SyncPrefs glue:** `SyncCoordinator.connect()` drives the login
  session end to end and, on success, writes `credentials.username`, `credentials.appPassword`,
  and `syncPrefs.setServerUrl(result.server)` — exactly the missing glue.
- **All four spec §5 triggers are wired in `SyncViewModel`:** manual ("Sync now" button),
  immediately
  after `connect()` succeeds, debounced ≥10s after a local edit
  (`bitsRepository.getBits().drop(1).debounce(10.seconds)`), and periodic every 5 minutes while the
  app is running (standing in for "app foreground," since this single-screen app has no separate
  foreground/background lifecycle signal to hook into instead).

*(Status: implemented since this review was written — see commit history around the sync-settings
UI and Login Flow v2 wiring.)*

One related gap remains, called out under #1: there is still no settings/inbox surface for a user
to resolve a bit stuck dirty by the resurrection-mitigation stopgap. The sheet added here covers
connect/disconnect/app-root/debug info, not per-bit conflict resolution.
