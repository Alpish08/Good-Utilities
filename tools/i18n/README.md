# Translations

The app's text lives in `app/src/main/res/values/strings_gu.xml` (generated, English) and
`strings_gu_manual.xml` (hand-written: plurals and names). French and Turkish are generated into
`values-fr/` and `values-tr/` from `translations/*.txt` (`key | French | Turkish`; plurals as `key#one`, `key#other`).

- `report.py <file.kt>…` lists the user-visible string literals in Kotlin files, with indexes.
- `apply.py <file.kt> <prefix> "<indexes to skip>"` moves them into resources as `t(R.string.…)`.
- `mp.py spec.json` applies hand edits (plurals, enums) and adds entries to `strings_gu_manual.xml`.
- `build_tr.py` writes `values-fr` / `values-tr` and refuses translations whose placeholders don't match the English.
