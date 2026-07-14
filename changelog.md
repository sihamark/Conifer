# Changelog

## Version 1.1.2 (XX.07.2026)

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