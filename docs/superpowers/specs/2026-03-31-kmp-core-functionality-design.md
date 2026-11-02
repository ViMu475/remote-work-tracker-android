# Design Spec: KMP Core Functionality (Sub-project 1)

**Date:** 2026-03-31
**Scope:** Add edit entries, settings UI, quarterly auto-adjustment, dark mode, and language switch to the KMP Android app
**Approach:** Minimal changes to existing files, follow current patterns

---

## 1. Edit Entries

### Current State
Users can only delete entries and re-add them. No edit capability.

### Changes

**TrackerRepository.kt** — Add `updateEntry()`:
- Parameters: `state: TrackerState`, `entryId: String`, `datum: String`, `startTime: String`, `endTime: String`, `pauseMinutes: Int`, `remoteHours: Double`
- Logic: find and remove old entry by ID, validate new values (overlap check excluding self, holiday/weekend check, pause validation), create new entry with same ID, insert into daily records, recalculate monthly totals
- Returns: `Result<String>` with success message or error

**MainActivity.kt** — Reuse existing Add Entry dialog for editing:
- Add `editingEntryId` state variable (`mutableStateOf<String?>(null)`)
- When `editingEntryId != null`: dialog title changes to "Eintrag bearbeiten", fields pre-filled with existing values, save calls `updateEntry()` instead of `addWorkEntry()`
- Entry cards get a pencil icon button next to the existing X delete button
- Tapping pencil sets `editingEntryId` and opens dialog with values pre-filled
- Cancel/dismiss resets `editingEntryId` to null

---

## 2. Settings Bottom Sheet

### Current State
`individuelleQuoteProzent` (40.0) and `sollStundenProTag` (8.0) stored in state but no UI to change them.

### Changes

**TrackerRepository.kt** — Add `updateSettings()`:
- Parameters: `state: TrackerState`, `newQuote: Double`, `newSollStunden: Double`
- Validates: quote 40.0-60.0, hours 1.0-10.0
- Updates both values in state
- Calls `recalculateMonthlyTotals()` to refresh dashboard
- Saves state
- Returns: `Result<String>`

**MainActivity.kt** — Add `ModalBottomSheet`:
- Triggered by gear icon (⚙) in top app bar
- State: `showSettingsSheet` (`mutableStateOf(false)`)
- Contents:
  - Header: "Einstellungen" / "Settings"
  - Slider: Remote-Quote (40-60%, step 1%, shows current value as label)
  - OutlinedTextField: Soll-Stunden/Tag (1.0-10.0, numeric keyboard)
  - Save button: calls `updateSettings()`, closes sheet, shows Snackbar confirmation
- Uses `rememberModalBottomSheetState()`

---

## 3. Quarterly Auto-Quote Adjustment

### Current State
KMP stores `currentQuartal` string and calculates quartal field in history, but no auto-adjustment logic when closing a quarter-end month.

### Changes

**TrackerRepository.kt** — Modify `closeSpecificMonth()`:
- After archiving month to history, check if closing month is last of its quarter:
  - Q1: März, Q2: Juni, Q3: September, Q4: Dezember
- If quarter-end:
  1. Collect all history entries matching the closed quarter
  2. If 3 months present: sum `istRemoteStundenLaufend` across all 3
  3. Calculate `gesamtMaxRemote = sum(sollStundenGesamt) * (quote / 100)`
  4. If `gesamtIstRemote > gesamtMaxRemote`: reduce quote by 10% (min 40%)
  5. Else: increase quote by 10% (max 60%)
  6. Update `state.individuelleQuoteProzent`
  7. Return adjustment message string (or null if not quarter-end)

**Constants in Models.kt or TrackerCalculations.kt:**
- `QUOTEN_ANPASSUNG_PROZENT = 10.0`
- `MIN_QUOTE = 40.0`
- `MAX_QUOTE = 60.0`
- These already match the Flask config values

**MainActivity.kt:**
- After `closeMonth()`, check returned message
- If quarterly adjustment occurred: show AlertDialog with the adjustment info
- Message format: "Quartal abgeschlossen. Neue Quote: X%" / "Quarter closed. New quota: X%"

---

## 4. Dark Mode

### Current State
`TrackerState` has `darkMode: Boolean = false`. `RemoteWorkTrackerTheme` is hardcoded to `lightColorScheme` only. No toggle.

### Changes

**MainActivity.kt** — Update theme:
- `RemoteWorkTrackerTheme` reads `darkMode` from state
- If true: use `darkColorScheme()` with custom colors:
  - Background: `#1f2937`
  - Surface/Cards: `#141922`
  - Primary: `#3b82f6` (blue)
  - OnBackground/OnSurface: `#e6e9ef`
- If false: keep existing `lightColorScheme`

**Toggle button:**
- Moon icon (🌙) in top app bar
- Toggles `state.darkMode`
- Saves state immediately
- Theme recomposes automatically via state observation

---

## 5. Language Switch

### Current State
`TrackerState` has `language: String = "de"`. All UI strings hardcoded in German.

### Changes

**MainActivity.kt** — Add translations map:
```kotlin
val translations = mapOf(
    "de" to mapOf(
        "app_title" to "Remote Work Tracker",
        "add_entry" to "Eintrag hinzufügen",
        "edit_entry" to "Eintrag bearbeiten",
        "settings" to "Einstellungen",
        "remote_quote" to "Remote-Quote",
        "target_hours" to "Soll-Stunden/Tag",
        "save" to "Speichern",
        "cancel" to "Abbrechen",
        "delete" to "Löschen",
        "close_month" to "Monat abschließen",
        "clear_all" to "Alles löschen",
        "entries" to "Einträge",
        "no_entries" to "Noch keine Einträge",
        "history" to "Historie",
        "soll" to "Soll",
        "max_remote" to "Max Remote",
        "ist_remote" to "Ist Remote",
        "remaining" to "Verbleibend",
        "confirm_delete" to "Wirklich löschen?",
        "confirm_close_month" to "Monat wirklich abschließen?",
        "confirm_clear_all" to "Komplette Historie löschen?",
        "quarter_adjusted" to "Quartal abgeschlossen. Neue Quote:",
        "pause_warning" to "Pausenwarnung (ArbZG)",
        "weekend_blocked" to "Wochenende - kein Eintrag möglich",
        "holiday_blocked" to "Feiertag - kein Eintrag möglich",
        "overlap_detected" to "Zeitüberschneidung erkannt",
        "pdf_import" to "PDF Import",
        "dark_mode" to "Dunkelmodus",
        "language" to "Sprache"
    ),
    "en" to mapOf(
        "app_title" to "Remote Work Tracker",
        "add_entry" to "Add Entry",
        "edit_entry" to "Edit Entry",
        "settings" to "Settings",
        "remote_quote" to "Remote Quota",
        "target_hours" to "Target Hours/Day",
        "save" to "Save",
        "cancel" to "Cancel",
        "delete" to "Delete",
        "close_month" to "Close Month",
        "clear_all" to "Clear All",
        "entries" to "Entries",
        "no_entries" to "No entries yet",
        "history" to "History",
        "soll" to "Target",
        "max_remote" to "Max Remote",
        "ist_remote" to "Actual Remote",
        "remaining" to "Remaining",
        "confirm_delete" to "Really delete?",
        "confirm_close_month" to "Really close this month?",
        "confirm_clear_all" to "Clear complete history?",
        "quarter_adjusted" to "Quarter closed. New quota:",
        "pause_warning" to "Break warning (ArbZG)",
        "weekend_blocked" to "Weekend - no entry allowed",
        "holiday_blocked" to "Holiday - no entry allowed",
        "overlap_detected" to "Time overlap detected",
        "pdf_import" to "PDF Import",
        "dark_mode" to "Dark Mode",
        "language" to "Language"
    )
)
```

**Helper function:**
```kotlin
fun t(key: String, lang: String): String = translations[lang]?.get(key) ?: key
```

**Toggle button:**
- Flag icon (🇬🇧/🇩🇪) in top app bar
- Toggles `state.language` between "de" and "en"
- Saves state immediately
- All composables use `t("key", state.language)` instead of hardcoded strings

---

## Files Changed

| File | Changes |
|------|---------|
| `shared/.../TrackerRepository.kt` | `updateEntry()`, `updateSettings()`, quarterly adjustment in `closeSpecificMonth()` |
| `shared/.../TrackerCalculations.kt` | Add `QUOTEN_ANPASSUNG_PROZENT`, `MIN_QUOTE`, `MAX_QUOTE` constants (if not in Models) |
| `androidApp/.../MainActivity.kt` | Edit dialog, settings bottom sheet, dark mode theme, language translations, quarterly message dialog |

## Not Included (Sub-project 2)
- CSV export
- PDF report generation
- JSON backup/restore UI
