# Nextcloud sync — review findings

Review of `shared/src/*/kotlin/eu/heha/conifer/{sync,auth}` and the sync-related pieces of
`model/database`, `prefs`, and DI, against `docs/nexcloud_sync_spec.md`. Ranked by importance.

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

Revisit once a settings/sync UI exists to make option 3 worthwhile (see #5).

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

## 4. Test coverage gaps (code looks correct by inspection, just not exercised end-to-end)

- Spec test case #5 (tombstone: delete on A → gone on B, day file re-rendered, then GC'd after 90
  days) has no single test stitching the whole pipeline together — the pieces are each tested
  separately (`MergePolicyTest` for tombstone-as-equals, `ReadableRendererTest`/`bitsForDay`'s
  `deleted = 0` filter, `GarbageCollectorTest` for physical deletion).
- Spec test case #7 ("no feedback loop") has no explicit 2-device test proving that writing
  readable files never perturbs `rootEtag`/the fast path — it's only implicitly covered.

## 5. The big one: nothing is wired into the running app

`KtorWebDavStore(`, `SyncEngine(`, and `LoginFlowV2(` have zero call sites outside class
declarations and tests. No DI bindings, no trigger anywhere (app foreground / manual refresh /
debounced-after-edit per spec §5), and no glue code connecting a completed
`LoginFlowV2.LoginResult` to `Credentials`/`SyncPrefs.setServerUrl()`. Expected — no
settings/login screen exists yet — but it means none of the sync code runs in the shipped app
today.
