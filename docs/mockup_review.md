# Mockup vs. real app — review findings

Comparison of `docs/conifer-mockup.html` against the actual Compose UI
(`shared/src/commonMain/kotlin/eu/heha/conifer/ui/`) and the real string/color resources. The
mockup has been updated to match where the fix was unambiguous; this file documents what changed
and the open questions that need a decision rather than a mechanical fix.

## 1. The two-pane / sidebar layout — implemented (was: needs a decision)

Originally this section flagged the mockup's "Two-pane" toggle and `.sidebar` as pure design
exploration: `BitsPane.kt` was a single `Column` with no responsive layout code at all. That
direction has since been taken, so the caveats ("(proposal)", the footer note) are gone from the
mockup: `BitsPane` now puts the day list into a
`DaySidebar` next to the bits, which then replaces the day strip inside the composer's picker
(`DateTimeSelector(isDaySelectionVisible = false)`) — the sidebar owns the day, exactly as in the
mockup.

Four notes on how the real layout differs from (or goes beyond) the mockup:

- **Window size class, not platform.** The mockup ties two panes to the Desktop/Web previews. The
  real app asks Material for the window's size class (`currentWindowAdaptiveInfoV2()`, from
  `org.jetbrains.compose.material3.adaptive:adaptive`) and lays out both panes from the medium
  width breakpoint (`isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)`, 600.dp) up, so desktop
  and web windows get it from the start, as do tablets and unfolded foldables, while phones stay
  single-pane — and a narrowed desktop window falls back to the phone layout instead of squeezing a
  sidebar in.
- **Weekday abbreviation.** The mockup's sidebar keeps Title Case ("Mon") while its day strip was
  corrected to the app's all-caps `dayOfWeek.name.take(3)` (§4). The sidebar follows the day strip
  here, so both day lists read alike.
- **Dots in the sidebar.** The mockup's sidebar rows only had the count badge, so the day strip's
  1–3 dots (§5) were the one thing the sidebar dropped when it took the day selection over. The real
  sidebar draws both — the dots for how a day's bits are spread over it, the badge for how many
  there are — from a `DayDots` composable now shared with the strip. **The mockup was updated to
  match** (`.side-item .dots`, reusing `dotsFor()`), so this is no longer a divergence.
- **"Days" title doesn't scroll.** In the mockup the whole `aside` scrolls. In the real app the top
  bar floats over the *main* pane only, so anything scrolling up in the sidebar would slide into an
  uncovered band next to it; the title stays put above the scrolling days instead.

## 2. Color palette was invented, not derived — now fixed

The mockup used a hand-picked forest-green/amber palette (`--pine:#24402f`, `--resin:#c9862b`,
...). The real app's `ConiferTheme` is a Material-generated olive/rust scheme
(`shared/.../ui/theme/Color.kt`) that looks nothing like it. Updated every CSS custom property to
the real hex values (see the comments in `:root`/`body[data-theme="dark"]` for the exact
Material role each one now maps to — `--pine`→primary, `--moss`→outline, `--resin`→tertiary,
`--fog`/`--card`→background/surface, etc.). Variable *names* were kept to minimize the diff.

This also surfaced a structural mismatch the mockup didn't have a variable for: real headings
(app bar title, dialog titles, sidebar title) use **plain `onSurface` text**, not an accent color
— only small badges/CTAs (time chips, the slider's time label, the permission prompt's bold lead
text, selected states) actually use `primary`. Added a `--heading` variable and repointed the
pure-heading selectors to it; the accent-only ones keep using `--pine` (now correctly = primary).

Also fixed along the way, once the accent-vs-heading split made the mismatches visible:

- `.day-header h2` was rendered in the cursive display font; the real `DateHeader` uses plain
  `bodyMedium` (Lato). Cursive font removed.
- `.bit .time` (the time badge) was colored with the outline-family value; real code colors it
  `primary`.
- The permission prompt's dashed border was outline-colored; real code uses `tertiary`.
- `.bit.editing`, `.field.editing input/label`: were accent-colored; the real `BitItem` card only
  gets a plain `colorScheme.outline` border while editing — the composer's text field itself has
  *no* editing-specific styling at all (only its label text and the submit icon change). Removed
  the field-level override entirely and fixed the card highlight to use the outline color.
- `.day-cell.today`: real code borders the current day with `primary`, not a separate accent;
  removed the second special-cased "today" text color that didn't correspond to anything in the
  real `DaySelection` (only the border differs there).
- `.submit` (composer's add/save button): real `FilledIconButton` defaults to
  primary/onPrimary, not tertiary.
- `.field input:focus`: real `OutlinedTextField`'s focused border is `primary`, not outline.

**Not changed, flagged as a suggestion instead:** the real delete confirmation's "Delete" button
is a plain `TextButton` (primary-colored text, no red, no fill) — Material3's AlertDialog default.
The mockup's filled red "Delete" button is more conventional destructive-action styling and was
left as-is rather than watered down to match. **Suggestion:** consider giving the real
`DeleteConfirmationDialog`'s confirm button an error-colored treatment.

## 3. Missing feature: the "beginning of your bits" marker

`BitsPane.kt` always renders a fixed `BeginningNote` (🌱 + "This is where your Conifer sprouted.
Keep adding bits and watch it grow!") above the oldest visible day, *whenever the list has at
least one bit* — including when a day filter is applied to a day that has bits. This is distinct
from the empty state (no bits at all, or none on the filtered day), which the mockup already had
correct. The persistent marker was missing entirely. **Added** (`.beginning-note`), copied
verbatim from `strings.xml`'s `bits_message_beginning`.

Worth double-checking with product intent: is showing this marker even while filtered to a single
day (so it appears right above that day's bits, not just at the true beginning of the whole
collection) intentional, or should it only show in the unfiltered view? Replicated as-is since
that's what the code does today.

## 4. Day/time formatting was invented, not accurate

- **Day headers**: the mockup showed "Today" / "Yesterday" / "Thursday, 25.7." — the real
  `DateHeader` just prints the plain ISO date (`LocalDate.print()` = `LocalDate.Formats.ISO`),
  e.g. "2026-07-25", with no weekday name and no "Today"/"Yesterday" friendliness at all.
  **Fixed** to match (`dayKey()` was already producing the same ISO string, so this reused it).
- **Date/time chip's custom label**: same underlying `print()`/`label()` call — shows "Today" only
  for the literal current date, else the plain ISO date. The mockup's "Weekday D.M" format was
  wrong here too. **Fixed.**
- **Day-picker weekday abbreviation**: real code is `dayOfWeek.name.take(3)` — an un-localized,
  always-English, all-caps three-letter code ("MON", "FRI"), not a Title Case one. **Fixed**
  (`.toUpperCase()` on the existing abbreviation).

**Suggestion:** the real day header's plain ISO date is a lot less friendly than what the mockup
had. Worth considering adopting the mockup's "Today"/"Yesterday"/weekday-name treatment in the
real `DateHeader` — the exact same `label()` logic already used for the date/time chip could be
reused there directly.

## 5. Day-chip dot indicator was a simplified stand-in

The mockup only had a binary has-a-bit/doesn't dot. The real `DatedBits.dots` logic is richer: one
dot for any bit that day, two once both morning (before 12:00) and afternoon have at least one,
three past that once the day holds more than 3 bits total. **Implemented** the same rule
(`dotsFor()`) and changed the day cell to render 0–3 small dots instead of toggling one.

## 6. Edit-mode details that were simplified away

- **Submit button icon**: real `NewBitText` swaps the icon between a checkmark (adding) and a
  pencil (saving an edit) — the mockup always showed a checkmark. **Fixed.**
- **Per-bit `⋮` menu**: real `BitItem` flips its first menu item to "Cancel Edit" for whichever
  bit is currently being edited, instead of always offering "Edit". **Fixed.**

**Bug found in the real app, not the mockup:** `NewBitText`'s icon `contentDescription` is backed
by the *wrong* string in both branches — `bits_content_save_bit` ("Save Bit") is used for the
add-a-new-bit checkmark, and `bits_content_edit_bit` ("Edit Bit") for the save-an-edit pencil, the
exact opposite of what each icon does. A screen reader user adding a new bit hears "Edit Bit" on
the button that adds it. Worth a real fix — swap which string each branch uses in `BitsPane.kt`'s
`NewBitText`. The mockup was **not** changed to copy this bug; it uses "Add bit"/"Save bit"
sensibly instead.

## 7. Copy that didn't match `strings.xml` / Android string resources

- Delete dialog: "Delete this bit?" / "It will be gone for good — Conifer keeps no trash." /
  "Keep it" → real strings are "Delete bit?" / "This bit will be permanently deleted." / "Cancel".
  **Fixed.**
- Bit counter: mockup used lowercase "bit"/"bits"; the real plural string is "%d Bit"/"%d Bits"
  (capitalized). **Fixed.**
- Empty-state copy and the permission prompt's lead/body text already matched `strings.xml`
  exactly — no change needed there.

## 8. Structural things the mockup can't represent and weren't touched

- The real top app bar *floats over* the list in a `Box` (the list gets a spacer item so it can
  scroll fully out from under the bar) and hides entirely while the IME is visible, so more of the
  screen is free for typing. The mockup's app bar is a normal block element that just sits above
  the list. Reproducing the floating/IME-hide behavior faithfully would need real scroll-position
  and virtual-keyboard tracking that doesn't map cleanly onto a static HTML mockup; left as a
  known, unfixable gap rather than a half-faithful approximation.

## 9. The sync UI added earlier has no real-code equivalent yet

The sync entry point, sheet, and debug panel (added in an earlier pass) are still pure proposal —
`SyncEngine`/`KtorWebDavStore`/`LoginFlowV2` aren't wired into any real screen (see
`sync_review.md` §5). They were restyled with the corrected palette for internal consistency, but
there's nothing in the real app to compare them against yet.

The debug panel moved from a collapsed toggle buried in the settings sheet to a popover anchored
directly on the app bar's status icon: pressing it once connected opens the debug glance (device
id, last sync, root ETag, pending changes, last error) immediately, with a "Sync settings…" link
back to the full sheet for connect/disconnect. Before connecting, the same press still opens the
full sheet instead (there's nothing to debug yet). Pure UX proposal, same caveat as the rest of
this section.
