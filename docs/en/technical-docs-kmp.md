# Technical Documentation -- Remote Work Tracker (KMP Android)

**Version 1.0.0**

---

## Table of Contents

1. [Architecture](#1-architecture)
2. [Data Model](#2-data-model)
3. [Repository Layer](#3-repository-layer-trackerrepositorykt)
4. [Calculations Engine](#4-calculations-engine-trackercalculationskt)
5. [Android UI](#5-android-ui-mainactivitykt)
6. [PDF Import](#6-pdf-import-pdfparserkt)
7. [PDF Report Generation](#7-pdf-report-generation-reportgeneratorkt)
8. [Build and Deployment](#8-build-and-deployment)
9. [Data Persistence](#9-data-persistence)
10. [ArbZG Compliance](#10-arbzg-compliance)
11. [Security and Privacy](#11-security-and-privacy)

---

## 1. Architecture

### 1.1 Technology Stack

| Layer            | Technology                              | Version / Notes                       |
|------------------|-----------------------------------------|---------------------------------------|
| Language         | Kotlin                                  | 1.9.21                                |
| Framework        | Kotlin Multiplatform (KMP)              | Shared module targets Android + iOS   |
| UI Toolkit       | Jetpack Compose                         | BOM 2023.10.01                        |
| Design System    | Material 3 (Material Design 3)         | via `androidx.compose.material3`      |
| Serialization    | kotlinx.serialization                   | 1.6.2 (JSON codec)                   |
| Date/Time        | kotlinx.datetime                        | 0.5.0                                 |
| PDF Parsing      | pdfbox-android (Tom Roush)              | 2.0.27.0                              |
| PDF Generation   | Android `android.graphics.pdf.PdfDocument` | Native Android API                 |
| Local Storage    | SharedPreferences                       | Platform `expect/actual` abstraction  |
| Build System     | Gradle (Kotlin DSL)                     | AGP 8.2.0                             |
| CI/CD            | Codemagic                               | codemagic.yaml in project root        |

### 1.2 Project Structure

```
RemoteWorkTrackerKMP/
├── build.gradle.kts                 # Root build script (plugin versions)
├── settings.gradle.kts              # Module registration
├── gradle.properties                # Gradle configuration properties
├── codemagic.yaml                   # CI/CD pipeline definition
│
├── shared/                          # KMP shared module (commonMain)
│   ├── build.gradle.kts             # KMP plugin config, serialization, datetime
│   └── src/
│       ├── commonMain/kotlin/com/remotework/tracker/
│       │   ├── Models.kt            # Data classes: TrackerState, WorkEntry, MonthData
│       │   ├── TrackerRepository.kt # Business logic, persistence, CRUD operations
│       │   └── TrackerCalculations.kt # Time math, validation, holiday calendar
│       ├── androidMain/kotlin/com/remotework/tracker/
│       │   └── LocalStorage.kt      # Android SharedPreferences implementation
│       └── iosMain/kotlin/com/remotework/tracker/
│           └── LocalStorage.kt      # iOS NSUserDefaults implementation
│
├── androidApp/                      # Android application module
│   ├── build.gradle.kts             # Android app config, Compose, pdfbox-android
│   └── src/main/
│       ├── AndroidManifest.xml      # Activity, FileProvider declarations
│       ├── kotlin/com/remotework/tracker/android/
│       │   ├── MainActivity.kt      # Compose UI, state management, navigation
│       │   ├── PdfParser.kt         # SAP TimeStatement PDF import logic
│       │   └── ReportGenerator.kt   # Monthly PDF report generation
│       └── res/
│           ├── xml/file_paths.xml   # FileProvider path configuration
│           ├── values/strings.xml   # App name and base strings
│           └── mipmap/              # Launcher icons
│
└── iosApp/                          # iOS target (SwiftUI, not covered here)
```

### 1.3 Architecture Diagram

The application follows a unidirectional data flow pattern with immutable state:

```
┌─────────────────────────────────────────────────────────┐
│                    Android UI Layer                      │
│              (Jetpack Compose + Material 3)              │
│                                                         │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Scaffold │  │  Composable  │  │  Dialog/Sheet    │  │
│  │ TopBar   │  │  Components  │  │  Components      │  │
│  │ FABs     │  │  (Cards,     │  │  (AddEntry,      │  │
│  │          │  │   Progress)  │  │   Settings,      │  │
│  │          │  │              │  │   PdfImport)     │  │
│  └────┬─────┘  └──────┬───────┘  └────────┬─────────┘  │
│       │               │                   │             │
│       └───────────────┼───────────────────┘             │
│                       │                                 │
│              MutableState<TrackerState>                  │
│              (Compose recomposition trigger)             │
└───────────────────────┼─────────────────────────────────┘
                        │
                        ▼
┌───────────────────────────────────────────────────────┐
│                 Shared Business Layer                   │
│                   (commonMain KMP)                      │
│                                                        │
│  ┌─────────────────────┐  ┌─────────────────────────┐ │
│  │  TrackerRepository   │  │  TrackerCalculations    │ │
│  │                      │  │                         │ │
│  │  - addWorkEntry()    │  │  - timeToMinutes()      │ │
│  │  - updateEntry()     │  │  - calculateNetHours()  │ │
│  │  - deleteEntry()     │  │  - isValidWorkingDay()  │ │
│  │  - closeMonth()      │  │  - checkTimeOverlap()   │ │
│  │  - updateSettings()  │  │  - calculateSollStunden │ │
│  │  - generateCsv()     │  │    Month()              │ │
│  │  - serializeState()  │  │  - getQuartalForMonth() │ │
│  └──────────┬───────────┘  └─────────────────────────┘ │
│             │                                          │
└─────────────┼──────────────────────────────────────────┘
              │
              ▼
┌───────────────────────────────────────────────────────┐
│                 Platform Storage Layer                  │
│                (expect/actual pattern)                  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  expect class LocalStorage                       │  │
│  │    fun save(key: String, value: String)          │  │
│  │    fun load(key: String): String?                │  │
│  │    fun clear(key: String)                        │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  Android: SharedPreferences("remote_work_tracker")     │
│  iOS:     NSUserDefaults.standardUserDefaults           │
└───────────────────────────────────────────────────────┘
```

**State Flow:**

1. User interacts with the Compose UI (button press, form submission).
2. Event handler calls a `TrackerRepository` method with the current immutable `TrackerState`.
3. The repository performs validation (via `TrackerCalculations`), computes new values, and returns a new `TrackerState` instance.
4. The UI layer assigns the new state to the `MutableState<TrackerState>` holder.
5. Compose detects the state change and recomposes only affected composables.
6. The repository persists the state to `LocalStorage` as serialized JSON.

### 1.4 KMP expect/actual Pattern

The `LocalStorage` class uses the Kotlin Multiplatform `expect/actual` mechanism to abstract platform-specific persistence:

```kotlin
// commonMain - Interface declaration
expect class LocalStorage {
    fun save(key: String, value: String)
    fun load(key: String): String?
    fun clear(key: String)
}
```

```kotlin
// androidMain - Android implementation
actual class LocalStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("remote_work_tracker", Context.MODE_PRIVATE)

    actual fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun load(key: String): String? {
        return prefs.getString(key, null)
    }

    actual fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }
}
```

---

## 2. Data Model

All data classes are annotated with `@Serializable` from kotlinx.serialization and reside in `shared/src/commonMain/kotlin/com/remotework/tracker/Models.kt`.

### 2.1 TrackerState (Root State Object)

`TrackerState` is the single source of truth for the entire application. It is immutable -- every modification produces a new copy via Kotlin's `data class copy()`.

```kotlin
@Serializable
data class TrackerState(
    val aktuellerMonat: String,              // Current month name (German), e.g. "Januar"
    val aktuellesJahr: Int,                  // Current year, e.g. 2026
    val currentMonthData: MonthData,         // Aggregated data for the active month
    val currentQuartal: String,              // Quarter string, e.g. "Q1/2026"
    val dailyRecords: Map<String, List<WorkEntry>> = emptyMap(),
                                             // Key: "YYYY-MM-DD", Value: entries for that day
    val monatlicheHistorie: List<MonthData> = emptyList(),
                                             // Archived months after closure
    val individuelleQuoteProzent: Double = 40.0,
                                             // Remote work quota percentage (40-60)
    val sollStundenProTag: Double = 8.0,     // Target work hours per day (1-10)
    val language: String = "de",             // UI language: "de" or "en"
    val darkMode: Boolean = false            // Dark mode toggle
)
```

**Field Reference Table:**

| Field                      | Type                           | Default      | Description                                     |
|----------------------------|--------------------------------|--------------|-------------------------------------------------|
| `aktuellerMonat`           | `String`                       | (computed)   | German month name of the active view            |
| `aktuellesJahr`            | `Int`                          | (computed)   | Year of the active view                         |
| `currentMonthData`         | `MonthData`                    | (computed)   | Aggregated statistics for the current month     |
| `currentQuartal`           | `String`                       | (computed)   | Quarter identifier, format "Qn/YYYY"            |
| `dailyRecords`             | `Map<String, List<WorkEntry>>` | `emptyMap()` | All work entries indexed by ISO date            |
| `monatlicheHistorie`       | `List<MonthData>`              | `emptyList()`| Closed months with final statistics             |
| `individuelleQuoteProzent` | `Double`                       | `40.0`       | Allowed remote work percentage                  |
| `sollStundenProTag`        | `Double`                       | `8.0`        | Contractual hours per working day               |
| `language`                 | `String`                       | `"de"`       | Active locale for UI translations               |
| `darkMode`                 | `Boolean`                      | `false`      | Whether dark color scheme is active             |

### 2.2 WorkEntry

Represents a single work session logged by the user. Multiple entries per day are supported (e.g., split shifts).

```kotlin
@Serializable
data class WorkEntry(
    val id: String,                    // Unique ID: "YYYY-MM-DD|HH:MM|HH:MM|timestamp"
    val datum: String,                 // ISO date string "YYYY-MM-DD"
    val startTime: String,             // Start time "HH:MM"
    val endTime: String,               // End time "HH:MM"
    val pauseMinutes: Double,          // Break duration in minutes
    val remoteStundenHeute: Double,    // Remote hours for this entry
    val totalStundenHeute: Double,     // Net total hours (gross - pause)
    val pauseWarning: String? = null,  // ArbZG violation warning, or null
    val source: String? = null         // "pdf_import" for entries from a statement, otherwise null
)
```

**Imported entries:** A statement lists work sessions without breaks, so the pause is stored as 0.
`importWorkEntry(...)` (instead of `addWorkEntry(...)`) sets `source = "pdf_import"` and stores **no**
break warning; the UIs also hide the warning for entries with `source == "pdf_import"`. Manually
entered entries are validated as before.

**ID Generation:**

Entry IDs are composite strings ensuring uniqueness:

```kotlin
val entryId = "$datum|$startTime|$endTime|${System.currentTimeMillis()}"
// Example: "2026-03-15|08:00|16:30|1711234567890"
```

The ID is preserved during updates (`updateEntry()`) to maintain referential integrity.

### 2.3 MonthData

Aggregated monthly statistics. Used both for the current month view and for archived history entries.

```kotlin
@Serializable
data class MonthData(
    val monat: String,                     // German month name
    val jahr: Int,                         // Year
    val individuelleQuote: Double,         // Remote quota % at time of recording
    val sollStundenGesamt: Double,         // Target hours for the month
    val maxRemoteStunden: Double,          // Maximum allowed remote hours
    val istRemoteStundenLaufend: Double,   // Actual remote hours logged
    val istArbeitszeitLaufend: Double,     // Actual total work hours logged
    val tatsaechlicheRemoteQuote: Double = 0.0,
                                           // Actual remote percentage achieved
    val quartal: String? = null            // Quarter assignment (set on close)
)
```

**Calculation Relationships:**

```
sollStundenGesamt    = workingDays(month) * sollStundenProTag
maxRemoteStunden     = sollStundenGesamt * (individuelleQuote / 100)
tatsaechlicheRemoteQuote = (istRemoteStundenLaufend / sollStundenGesamt) * 100
remaining            = maxRemoteStunden - istRemoteStundenLaufend
```

### 2.4 Helper Classes

#### MonthNames

Provides bidirectional mapping between German month names and their numeric indices. All month references in the data model use German names regardless of the UI language setting.

```kotlin
object MonthNames {
    val german = listOf(
        "Januar", "Februar", "Marz", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )

    val monthNumbers = german.mapIndexed { index, name -> name to index + 1 }.toMap()

    fun getMonthNumber(name: String): Int = monthNumbers[name] ?: 1
    fun getMonthName(number: Int): String = german.getOrElse(number - 1) { "Januar" }
}
```

#### BavarianHolidays

Static sets of public holiday dates for the German state of Bavaria (Bayern) for 2025 and 2026. Used by the validation engine to reject time entries on holidays.

```kotlin
object BavarianHolidays {
    val holidays2025 = setOf(
        "2025-01-01",  // Neujahr
        "2025-01-06",  // Heilige Drei Koenige
        "2025-04-18",  // Karfreitag
        "2025-04-21",  // Ostermontag
        "2025-05-01",  // Tag der Arbeit
        "2025-05-29",  // Christi Himmelfahrt
        "2025-06-09",  // Pfingstmontag
        "2025-06-19",  // Fronleichnam
        "2025-08-15",  // Maria Himmelfahrt
        "2025-10-03",  // Tag der Deutschen Einheit
        "2025-11-01",  // Allerheiligen
        "2025-12-25",  // 1. Weihnachtsfeiertag
        "2025-12-26"   // 2. Weihnachtsfeiertag
    )

    val holidays2026 = setOf(
        "2026-01-01", "2026-01-06", "2026-04-03", "2026-04-06",
        "2026-05-01", "2026-05-14", "2026-05-25", "2026-06-04",
        "2026-08-15", "2026-10-03", "2026-11-01", "2026-12-25", "2026-12-26"
    )

    fun isHoliday(date: String): Boolean = date in holidays2025 || date in holidays2026
}
```

**Note:** Holiday sets must be updated annually. Moveable feasts (Easter-dependent dates) change each year.

#### TimeResult

Return type for the `calculateNetHours()` function:

```kotlin
data class TimeResult(
    val grossHours: Double,        // Total hours before pause deduction
    val netHours: Double,          // Hours after pause deduction
    val pauseWarning: String?      // ArbZG violation warning or null
)
```

#### ValidationResult

Return type for the `isValidWorkingDay()` function:

```kotlin
data class ValidationResult(
    val isValid: Boolean,          // Whether the date is a valid working day
    val errorMessage: String?      // Human-readable error reason or null
)
```

---

## 3. Repository Layer (TrackerRepository.kt)

The `TrackerRepository` class encapsulates all business logic and acts as the single point of contact between the UI and the data layer. It is located in `shared/src/commonMain/` and is therefore available on all platforms.

### 3.1 Initialization

```kotlin
class TrackerRepository(private val storage: LocalStorage) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val stateKey = "tracker_state"
}
```

The `Json` instance is configured with `ignoreUnknownKeys = true` to ensure forward compatibility -- if a newer version adds fields, older versions will not crash when deserializing stored data.

### 3.2 Persistence

#### loadState()

Loads the application state from local storage. Falls back to a freshly computed default state if no data exists or if deserialization fails.

```kotlin
fun loadState(): TrackerState {
    val stored = storage.load(stateKey)
    return if (stored != null) {
        try {
            json.decodeFromString<TrackerState>(stored)
        } catch (e: Exception) {
            createDefaultState()
        }
    } else {
        createDefaultState()
    }
}
```

**Error Recovery:** Any `Exception` during deserialization (corrupt data, schema mismatch) silently falls back to defaults. This prevents the app from crashing on launch due to incompatible stored data.

#### saveState()

Serializes the full `TrackerState` to JSON and writes it to local storage:

```kotlin
fun saveState(state: TrackerState) {
    storage.save(stateKey, json.encodeToString(state))
}
```

#### clearAll()

Removes the stored state key entirely:

```kotlin
fun clearAll() {
    storage.clear(stateKey)
}
```

#### serializeState() / deserializeState()

Backup and restore functions that convert the state to/from JSON strings for export via share intents:

```kotlin
fun serializeState(state: TrackerState): String {
    return json.encodeToString(state)
}

fun deserializeState(jsonString: String): TrackerState? {
    return try {
        json.decodeFromString<TrackerState>(jsonString)
    } catch (e: Exception) {
        null  // Returns null on invalid JSON
    }
}
```

### 3.3 Work Entry Management

#### addWorkEntry()

The primary entry point for recording work time. Performs full validation before creating the entry.

```kotlin
fun addWorkEntry(
    state: TrackerState,
    datum: String,          // ISO date "YYYY-MM-DD"
    startTime: String,      // "HH:MM"
    endTime: String,        // "HH:MM"
    pauseMinutes: Double,
    remoteHours: Double?    // null = all hours are remote
): Result<TrackerState>
```

**Validation Pipeline:**

1. **Working Day Check** -- `isValidWorkingDay(datum)` rejects holidays, Sundays, and Saturdays.
2. **Time Overlap Check** -- `checkTimeOverlap()` ensures no existing entry on the same day has conflicting times.
3. **Net Hours Calculation** -- `calculateNetHours()` must return a non-negative result.
4. **Remote Hours Bound** -- If explicitly provided, `remoteHours` must not exceed `netHours`.

**Return Value:** `Result<TrackerState>` -- either a `Success` with the new state (including recalculated monthly totals) or a `Failure` with a descriptive error message.

#### updateEntry()

Edits an existing entry while preserving its original ID. The process removes the old entry, validates the new data (excluding the entry's own ID from overlap checks), and inserts the updated entry:

```kotlin
fun updateEntry(
    state: TrackerState,
    entryId: String,        // ID of the entry to update
    datum: String,
    startTime: String,
    endTime: String,
    pauseMinutes: Double,
    remoteHours: Double?
): Result<TrackerState>
```

The key difference from `addWorkEntry()` is the `excludeId` parameter passed to `checkTimeOverlap()`:

```kotlin
val overlapError = TrackerCalculations.checkTimeOverlap(
    datum, startTime, endTime, existingEntries, excludeId = entryId
)
```

This prevents the entry from conflicting with itself during edits.

#### deleteEntry()

Removes an entry by ID. If removing the entry leaves a day with zero entries, the entire day key is removed from `dailyRecords`:

```kotlin
fun deleteEntry(state: TrackerState, entryId: String): TrackerState {
    val updatedRecords = state.dailyRecords.toMutableMap()
    for ((datum, entries) in updatedRecords) {
        val filteredEntries = entries.filter { it.id != entryId }
        if (filteredEntries.size != entries.size) {
            if (filteredEntries.isEmpty()) {
                updatedRecords.remove(datum)
            } else {
                updatedRecords[datum] = filteredEntries
            }
            break
        }
    }
    return recalculateMonthlyTotals(state.copy(dailyRecords = updatedRecords))
}
```

### 3.4 Month Management

#### recalculateMonthlyTotals()

Iterates all `dailyRecords` for the active month/year, sums up remote and total hours, and updates `currentMonthData`:

```kotlin
fun recalculateMonthlyTotals(state: TrackerState): TrackerState
```

This function is called after every state mutation (add, update, delete, settings change) to keep derived values consistent.

**Key Calculations:**

```
sollGesamt        = calculateSollStundenMonth(year, month, sollStundenProTag)
maxRemote         = sollGesamt * (individuelleQuoteProzent / 100.0)
tatsaechlicheQuote = if (sollGesamt > 0) (totalRemote / sollGesamt * 100) else 0.0
```

#### calculateMonthData()

Computes `MonthData` for any specific month from the current `dailyRecords`, not just the active month. Used when switching views and when closing specific months.

```kotlin
fun calculateMonthData(state: TrackerState, year: Int, monthNum: Int): MonthData
```

#### closeMonth()

Closes the current month, archives it to `monatlicheHistorie`, and advances to the next month:

```kotlin
fun closeMonth(state: TrackerState): Pair<TrackerState, String?>
```

**Process:**

1. Archives `currentMonthData` with its quarter assignment.
2. Appends to `monatlicheHistorie`.
3. Calls `checkQuarterlyAdjustment()` if applicable.
4. Creates fresh `MonthData` for the next month.
5. Returns the updated state and an optional quarter-adjustment message.

#### closeSpecificMonth()

Closes a specific month (not necessarily the current one). This is the function actually called from the UI. It handles:

- Duplicate prevention (skips history addition if already archived).
- Removal of the month's entries from `dailyRecords`.
- Automatic view switching to the next available month.
- History sorting by chronological order.

```kotlin
fun closeSpecificMonth(state: TrackerState, year: Int, monthNum: Int): Pair<TrackerState, String?>
```

#### switchToMonth()

Changes the active month view without modifying any data:

```kotlin
fun switchToMonth(state: TrackerState, year: Int, monthNum: Int): TrackerState {
    val monthName = MonthNames.getMonthName(monthNum)
    val monthData = calculateMonthData(state, year, monthNum)
    return state.copy(
        aktuellerMonat = monthName,
        aktuellesJahr = year,
        currentMonthData = monthData,
        currentQuartal = TrackerCalculations.getQuartalForMonth(monthName, year)
    )
}
```

#### getMonthsFromEntries()

Scans all `dailyRecords` keys to determine which months contain entries:

```kotlin
fun getMonthsFromEntries(state: TrackerState): List<Pair<Int, Int>>
// Returns: List of (year, monthNumber) sorted chronologically
```

#### getEntriesForMonth()

Filters `dailyRecords` to return only entries belonging to a specific month:

```kotlin
fun getEntriesForMonth(state: TrackerState, year: Int, monthNum: Int): List<WorkEntry>
// Returns: Entries sorted by datum (ascending)
```

### 3.5 Settings

#### updateSettings()

Validates and applies new quota and target-hours settings:

```kotlin
fun updateSettings(state: TrackerState, newQuote: Double, newSollStunden: Double): Result<TrackerState> {
    if (newQuote < 40.0 || newQuote > 60.0)
        return Result.failure(Exception("Quote must be 40-60%"))
    if (newSollStunden < 1.0 || newSollStunden > 10.0)
        return Result.failure(Exception("Hours must be 1-10"))
    val updated = state.copy(
        individuelleQuoteProzent = newQuote,
        sollStundenProTag = newSollStunden
    )
    return Result.success(recalculateMonthlyTotals(updated))
}
```

**Constraints:**

| Setting                    | Minimum | Maximum | Step |
|----------------------------|---------|---------|------|
| `individuelleQuoteProzent` | 40%     | 60%     | 5%   |
| `sollStundenProTag`        | 1.0h    | 10.0h   | free |

### 3.6 Quarterly Adjustment

#### checkQuarterlyAdjustment()

Automatically adjusts the remote work quota at quarter boundaries. This is a private function called internally by `closeMonth()` and `closeSpecificMonth()`.

```kotlin
private fun checkQuarterlyAdjustment(
    state: TrackerState,
    monthNum: Int,
    year: Int
): Pair<TrackerState, String?>
```

**Trigger Conditions:**

- Only runs when `monthNum` is 3 (Q1 end), 6 (Q2 end), 9 (Q3 end), or 12 (Q4 end).
- Requires all 3 months of the quarter to be present in `monatlicheHistorie`.

**Adjustment Logic:**

```
quarterRemoteTotal  = sum of istRemoteStundenLaufend for the 3 quarter months
quarterMaxAllowed   = sum of maxRemoteStunden for the 3 quarter months

if quarterRemoteTotal > quarterMaxAllowed:
    newQuote = max(currentQuote - 10%, 40%)   // Penalty: reduce by 10%
else:
    newQuote = min(currentQuote + 10%, 60%)   // Reward: increase by 10%
```

The adjustment is clamped to the 40-60% range. A human-readable message is returned for UI display.

### 3.7 Data Export

#### generateCsv()

Produces a CSV string for a specific month's entries:

```kotlin
fun generateCsv(state: TrackerState, year: Int, monthNum: Int): String
```

**Output Format:**

```csv
Datum,Start,Ende,Pause (min),Remote (h),Gesamt (h)
2026-03-10,08:00,16:30,30.0,8.0,8.0
2026-03-11,09:00,17:30,45.0,8.0,8.0
```

### 3.8 History and Final Report (FinalReport.kt)

`FinalReport` (module `shared`, `commonMain`) holds the platform-independent history logic. Android
and iOS call the same functions, and the rules are identical in the other variants of the app.

| Function | Purpose |
|---|---|
| `isOverQuota(month)` | `true` when `istRemoteStundenLaufend > maxRemoteStunden` (compared on 2 decimals); the UI paints the card **red** |
| `sortHistory(history, newestFirst)` | sorts by year and month; the history is shown newest first |
| `build(history)` | final report: months grouped by quarter, quarters ascending, a duplicate month counts once, a missing quarter label is derived |
| `rate(remote, limit)` | rating of a quarter |

**Quarter rating** (share of the quarter's remote limit, the sum of the monthly limits): up to 90 %
**GOOD**, up to 100 % **TIGHT**, above 100 % **EXCEEDED**. A quarter with fewer than 3 months is
incomplete (`complete = false`). The report also contains sums, the actual quota, the number of months
over the limit and an overall rating (`Overall`); with no closed months `overall.rating` is `null`.

Tests: `shared/src/commonTest/.../FinalReportTest.kt` (13 cases, the same as in the other variants).

---

## 4. Calculations Engine (TrackerCalculations.kt)

The `TrackerCalculations` object provides stateless utility functions for all time and date calculations. It resides in `commonMain` and has no side effects.

### 4.1 Time Calculation

#### timeToMinutes()

Converts a "HH:MM" string to total minutes since midnight:

```kotlin
fun timeToMinutes(timeStr: String): Int {
    val parts = timeStr.split(":")
    return if (parts.size == 2) {
        parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
    } else 0
}
```

**Examples:**

| Input    | Output |
|----------|--------|
| "08:00"  | 480    |
| "16:30"  | 990    |
| "00:00"  | 0      |
| "23:59"  | 1439   |
| "invalid"| 0      |

#### calculateNetHours()

Computes gross and net working hours from start/end times and a pause duration. Handles overnight shifts (end time < start time).

```kotlin
fun calculateNetHours(startTime: String, endTime: String, pauseMinutes: Double): TimeResult {
    val startMin = timeToMinutes(startTime)
    var endMin = timeToMinutes(endTime)

    // Overnight shift handling
    if (endMin < startMin) {
        endMin += 24 * 60  // Add 24 hours
    }

    val grossMin = endMin - startMin
    val grossHours = grossMin / 60.0
    val pauseHours = pauseMinutes / 60.0
    val netHours = grossHours - pauseHours

    // ArbZG statutory pause check
    val statutoryPause = when {
        grossHours >= 9 -> 0.75   // 45 minutes
        grossHours >= 6 -> 0.5    // 30 minutes
        else -> 0.0
    }

    val pauseWarning = if (statutoryPause > pauseHours) {
        "WARNUNG: Pause (${pauseHours}h) < gesetzl. Minimum (${statutoryPause}h)!"
    } else null

    return TimeResult(grossHours, netHours, pauseWarning)
}
```

**Example Calculations:**

| Start  | End    | Pause | Gross | Net   | Warning? |
|--------|--------|-------|-------|-------|----------|
| 08:00  | 16:30  | 30    | 8.5h  | 8.0h  | No       |
| 08:00  | 16:30  | 15    | 8.5h  | 8.25h | Yes (30 min required) |
| 22:00  | 06:00  | 30    | 8.0h  | 7.5h  | No       |
| 09:00  | 18:30  | 30    | 9.5h  | 9.0h  | Yes (45 min required) |

### 4.2 Validation

#### isValidWorkingDay()

Checks whether a date is a valid day for logging work time:

```kotlin
fun isValidWorkingDay(dateString: String): ValidationResult
```

**Rejection Rules (in order):**

1. **Invalid format** -- Any string that cannot be parsed as `LocalDate` returns an error.
2. **Bavarian holiday** -- Checked against `BavarianHolidays.isHoliday()`.
3. **Sunday** -- Unconditionally rejected.
4. **Saturday** -- Rejected with a note about approved overtime.

All error messages are in German, consistent with the domain language.

#### checkTimeOverlap()

Detects whether a proposed time range overlaps with any existing entry on the same day:

```kotlin
fun checkTimeOverlap(
    datum: String,
    startTime: String,
    endTime: String,
    existingEntries: List<WorkEntry>,
    excludeId: String? = null       // Skip this entry (for updates)
): String?                           // Returns error message or null
```

**Overlap Detection Logic:**

The function checks four overlap conditions (any one being true means overlap):

```
Condition 1: existingStart <= newStart < existingEnd
Condition 2: existingStart < newEnd <= existingEnd
Condition 3: newStart <= existingStart AND newEnd >= existingEnd  (new contains existing)
Condition 4: existingStart <= newStart AND existingEnd >= newEnd  (existing contains new)
```

Both new and existing time ranges are normalized for overnight shifts (add 24h if end < start).

### 4.3 Monthly Calculation

#### calculateSollStundenMonth()

Dynamically calculates the target working hours for a given month by counting actual working days:

```kotlin
fun calculateSollStundenMonth(year: Int, month: Int, sollStundenProTag: Double): Double {
    val firstDay = LocalDate(year, month, 1)
    val lastDay = LocalDate(year, month, 1)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)

    var totalHours = 0.0
    var currentDay = firstDay

    while (currentDay <= lastDay) {
        val dateString = currentDay.toString()
        val dayOfWeek = currentDay.dayOfWeek

        if (dayOfWeek != DayOfWeek.SATURDAY &&
            dayOfWeek != DayOfWeek.SUNDAY &&
            !BavarianHolidays.isHoliday(dateString)
        ) {
            totalHours += sollStundenProTag
        }
        currentDay = currentDay.plus(1, DateTimeUnit.DAY)
    }

    return totalHours
}
```

**Example:** March 2026 has 22 working days (31 days minus 8 weekend days minus 1 holiday). With 8h/day: `22 * 8 = 176.0h`.

#### getQuartalForMonth()

Maps a month name to its quarter string:

```kotlin
fun getQuartalForMonth(monthName: String, year: Int): String {
    val monthNum = MonthNames.getMonthNumber(monthName)
    val q = ((monthNum - 1) / 3) + 1
    return "Q$q/$year"
}
```

| Month Range | Result       |
|-------------|-------------|
| Jan-Mar     | "Q1/2026"   |
| Apr-Jun     | "Q2/2026"   |
| Jul-Sep     | "Q3/2026"   |
| Oct-Dec     | "Q4/2026"   |

#### getCurrentDate()

Returns the current system date using kotlinx.datetime:

```kotlin
fun getCurrentDate(): LocalDate {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}
```

---

## 5. Android UI (MainActivity.kt)

The entire Android UI is built with Jetpack Compose in a single-activity architecture. All composables are defined in `MainActivity.kt`.

### 5.1 Theme

The app defines a custom Material 3 theme with light and dark variants:

```kotlin
@Composable
fun RemoteWorkTrackerTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkMode) {
        darkColorScheme(
            primary = Color(0xFF3B82F6),       // Blue
            background = Color(0xFF1F2937),    // Dark slate
            surface = Color(0xFF141922),        // Darker slate
            onBackground = Color(0xFFE6E9EF),  // Light text
            onSurface = Color(0xFFE6E9EF),
            secondary = Color(0xFF10B981),     // Emerald green
            error = Color(0xFFEF4444)          // Red
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4F46E5),       // Indigo
            secondary = Color(0xFF10B981),     // Emerald green
            error = Color(0xFFEF4444)          // Red
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

**Color Palette:**

| Role         | Light Mode       | Dark Mode        |
|-------------|------------------|------------------|
| Primary     | #4F46E5 (Indigo) | #3B82F6 (Blue)   |
| Secondary   | #10B981 (Green)  | #10B981 (Green)  |
| Error       | #EF4444 (Red)    | #EF4444 (Red)    |
| Background  | System default   | #1F2937 (Slate)  |
| Surface     | System default   | #141922 (Dark)   |
| PDF FAB     | #8B5CF6 (Purple) | #8B5CF6 (Purple) |
| Warning     | #F59E0B (Amber)  | #F59E0B (Amber)  |

### 5.2 Localization

The app supports German (de) and English (en) through an in-memory translations map. Language switching is instant with no app restart required.

```kotlin
private val translations = mapOf(
    "de" to mapOf(
        "add_entry" to "Eintrag hinzufuegen",
        "settings" to "Einstellungen",
        "remote_quote" to "Remote-Quote",
        // ... 30+ keys total
    ),
    "en" to mapOf(
        "add_entry" to "Add Entry",
        "settings" to "Settings",
        "remote_quote" to "Remote Quota",
        // ... 30+ keys total
    )
)

private fun t(key: String, lang: String): String =
    translations[lang]?.get(key) ?: key
```

**Translation Keys (complete list):**

| Key               | German (de)                | English (en)            |
|-------------------|----------------------------|-------------------------|
| add_entry         | Eintrag hinzufuegen        | Add Entry               |
| edit_entry        | Eintrag bearbeiten         | Edit Entry              |
| settings          | Einstellungen              | Settings                |
| remote_quote      | Remote-Quote               | Remote Quota            |
| target_hours      | Soll-Stunden/Tag           | Target Hours/Day        |
| save              | Speichern                  | Save                    |
| cancel            | Abbrechen                  | Cancel                  |
| delete            | Loeschen                   | Delete                  |
| close_month       | Monat abschliessen         | Close Month             |
| clear_all         | Alles loeschen             | Clear All               |
| entries           | Eintraege                  | Entries                 |
| no_entries        | Noch keine Eintraege       | No entries yet          |
| history           | Historie                   | History                 |
| soll              | Soll                       | Target                  |
| max_remote        | Max Remote                 | Max Remote              |
| ist_remote        | Ist Remote                 | Actual Remote           |
| remaining         | Verbleibend                | Remaining               |
| confirm_delete    | Wirklich loeschen?         | Really delete?          |
| confirm_close     | Monat wirklich abschl.?    | Really close this month?|
| confirm_clear     | Komplette Historie loeschen?| Clear complete history? |
| pdf_import        | PDF Import                 | PDF Import              |
| remote_hours      | Remote-Stunden             | Remote Hours            |
| available_months  | Verfuegbare Monate         | Available Months        |
| entry_saved       | Eintrag gespeichert        | Entry saved             |
| entry_deleted     | Eintrag geloescht          | Entry deleted           |
| csv_export        | CSV Export                 | CSV Export              |
| pdf_report        | PDF Bericht                | PDF Report              |
| backup            | Backup                     | Backup                  |
| restore           | Wiederherstellen           | Restore                 |
| export_import     | Export / Import            | Export / Import         |
| record_time       | Arbeitszeit erfassen       | Record Work Time        |
| quote_label       | Quote                      | Quote                   |

### 5.3 Main Layout

The `TrackerApp` composable is the root layout component:

```
┌────────────────────────────────────────┐
│  TopAppBar                             │
│  [Remote Work Tracker]    [EN][☽][⚙]  │
├────────────────────────────────────────┤
│  LazyColumn (scrollable content)       │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │  Message Card (snackbar-like)    │  │  <- Conditional, auto-dismiss 3s
│  └──────────────────────────────────┘  │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │  MonthSelectorRow (FilterChips)  │  │  <- Only if >1 month has entries
│  └──────────────────────────────────┘  │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │  MonthOverviewCard               │  │  <- Month name, quarter, quote
│  │  "Maerz 2026  Q1/2026 | 40%"   │  │
│  └──────────────────────────────────┘  │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │  RemoteProgressCard              │  │  <- Progress bar + remaining
│  │  ████████░░░░░░░  32.00/70.40h  │  │
│  │  Remaining: 38.40h              │  │
│  └──────────────────────────────────┘  │
│                                        │
│  ┌──────────┬──────────┬────────────┐  │
│  │  Target  │Max Remote│Act. Remote │  │  <- QuickStatsRow
│  │  176.0h  │  70.4h   │  32.0h     │  │
│  └──────────┴──────────┴────────────┘  │
│                                        │
│  Entries (Maerz 2026)                  │
│  ┌──────────────────────────────────┐  │
│  │  WorkEntryCard  [✎] [X]         │  │  <- Per entry, edit/delete
│  │  2026-03-15                      │  │
│  │  08:00 - 16:30                   │  │
│  │  Remote: 8.00h | Target: 8.00h  │  │
│  └──────────────────────────────────┘  │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │  Export / Import                 │  │
│  │  [CSV] [PDF] [Backup] [Restore] │  │
│  └──────────────────────────────────┘  │
│                                        │
│  History                               │
│  ┌──────────────────────────────────┐  │
│  │  HistoryCard                     │  │
│  │  Februar 2026                    │  │
│  │  Remote: 64.00h / 70.40h        │  │
│  └──────────────────────────────────┘  │
│                                        │
│  [Close Month]      [Clear All]        │
│                                        │
│  (Spacer 80dp for FAB clearance)       │
│                                        │
├────────────────────────────────────────┤
│                              [PDF]     │  <- FABs (stacked vertically)
│                              [ + ]     │
└────────────────────────────────────────┘
```

### 5.4 Composable Components

#### TrackerApp

The main composable that holds all state and manages the `Scaffold`:

- **State management:** Uses `MutableState<TrackerState>` with `remember`.
- **PDF launcher:** `ActivityResultContracts.OpenDocument()` for PDF file picking.
- **Restore launcher:** `ActivityResultContracts.OpenDocument()` for JSON backup restore.
- **Auto-switch:** `LaunchedEffect` watches `dailyRecords` and switches to the latest month after import.
- **Message auto-dismiss:** `LaunchedEffect` clears `message` after 3 seconds.

#### MonthOverviewCard

Displays the current month name, year, quarter, and active quota percentage on an indigo-tinted background.

#### RemoteProgressCard

Shows a `LinearProgressIndicator` with color-coded remaining hours:

| Remaining Hours | Color  | Meaning          |
|----------------|--------|------------------|
| >= 10h         | Green  | On track         |
| 0-10h          | Amber  | Approaching limit|
| < 0h (over)    | Red    | Quota exceeded   |

#### QuickStatsRow

Three `StatCard` composables in a horizontal `Row`:
- Target hours (Soll)
- Maximum remote hours
- Actual remote hours

#### WorkEntryCard

Displays a single entry with date, time range, remote/total hours, and optional ArbZG warning. Includes edit (pencil icon) and delete (X) buttons.

#### AddEntryDialog

A Material 3 `AlertDialog` with five `OutlinedTextField` inputs:

| Field  | Default Value         | Format     |
|--------|-----------------------|------------|
| Date   | Today (ISO format)    | YYYY-MM-DD |
| Start  | "08:00"               | HH:MM      |
| End    | "16:30"               | HH:MM      |
| Pause  | "30"                  | Minutes    |
| Remote | "" (empty = all)      | Hours      |

When `isEditing` is true, the dialog title changes and fields are pre-populated with existing values.

#### SettingsBottomSheet

A `ModalBottomSheet` with:

- **Quota Slider:** Range 40-60%, steps of 5% (positions: 40, 45, 50, 55, 60).
- **Target Hours TextField:** Free-form input, validated to 1-10 range on save.
- **Save Button:** Calls `repository.updateSettings()`.

#### MonthSelectorRow

A row of `FilterChip` composables for switching between months that have entries. Only rendered when more than one month has data. Shows abbreviated month names (first 3 characters).

#### HistoryCard

Displays archived month data with remote hours used vs. allowed and achieved quota percentage.

#### PdfImportDialog

Shows parsed PDF results in a scrollable `LazyColumn` with:
- Entry count
- Individual entries with date/time (remote entries highlighted in indigo)
- Error/warning section
- "Import All" confirmation button

### 5.5 Event Handling

#### Share Intents

The app uses Android's `ACTION_SEND` intent with `FileProvider` URIs for sharing exported files:

```kotlin
// CSV Export
val file = File(context.cacheDir, "Export_${state.aktuellerMonat}_${state.aktuellesJahr}.csv")
file.writeText(csv)
val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/csv"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(intent, t("csv_export", lang)))
```

The same pattern is used for PDF reports (`application/pdf`) and JSON backups (`application/json`).

#### ActivityResultLauncher

Two launchers handle file selection:

1. **PDF Import Launcher** -- Opens a system file picker filtered to `application/pdf`. On result, passes the URI to `PdfParser.parsePdf()`.
2. **Restore Launcher** -- Opens a file picker for `application/json`. On result, reads the JSON content, deserializes it, and replaces the current state.

#### State Observation

Compose recomposition is triggered automatically when `state` (the `MutableState<TrackerState>`) is updated via the `by` delegate:

```kotlin
var state by stateHolder  // Compose tracks reads and recomposes
```

---

## 6. PDF Import (PdfParser.kt)

The `PdfParser` class handles parsing of SAP TimeStatement PDFs to extract work entries. It uses pdfbox-android for text extraction.

### 6.1 Architecture

```kotlin
class PdfParser(private val context: Context) {
    init {
        PDFBoxResourceLoader.init(context)  // Required initialization
    }

    fun parsePdf(uri: Uri): PdfParseResult
}
```

### 6.2 Data Types

```kotlin
data class ParsedWorkEntry(
    val datum: String,           // "YYYY-MM-DD"
    val startTime: String,       // "HH:MM"
    val endTime: String,         // "HH:MM"
    val pauseMinutes: Double,    // Default: 30.0
    val remoteHours: Double?,    // null until calculated
    val isRemote: Boolean = false // Remote work flag
)

data class PdfParseResult(
    val entries: List<ParsedWorkEntry>,
    val errors: List<String>
)
```

### 6.3 Parsing Strategy

The parser uses a two-tier approach:

**Tier 1: SAP TimeStatement Format**

Extracts the month/year from header patterns:
- English: `from DD.MM.YYYY to DD.MM.YYYY`
- German: `vom DD.MM.YYYY bis DD.MM.YYYY`

Then scans for day entries using regex:

```kotlin
// Day line pattern (English + German weekdays)
val dayPattern = Regex(
    """^(\d{2})\s+(MO|TU|WE|TH|FR|SA|SU|DI|MI|DO|SO)""",
    RegexOption.IGNORE_CASE
)

// Time entry pattern: "HH:MM HH:MM hours"
val timePattern = Regex("""(\d{2}):(\d{2})\s+(\d{2}):(\d{2})\s+(\d+[,\.]\d+)""")
```

Remote work detection uses keyword matching:

```kotlin
private fun isRemoteWorkLine(line: String): Boolean {
    val remoteKeywords = listOf(
        "remote work", "remote", "telearbeit", "home office", "homeoffice"
    )
    return remoteKeywords.any { line.lowercase().contains(it) }
}
```

**Tier 2: Generic Format Fallback**

If the SAP parser finds no entries, it falls back to line-by-line parsing with five regex patterns:

| Pattern | Format                              | Example                        |
|---------|-------------------------------------|--------------------------------|
| 1       | DD.MM.YYYY HH:MM HH:MM            | 15.03.2026 08:00 16:30        |
| 2       | YYYY-MM-DD HH:MM HH:MM            | 2026-03-15 08:00 16:30        |
| 3       | DD.MM.YYYY HH:MM HH:MM pause      | 15.03.2026 08:00 16:30 30     |
| 4       | Weekday DD.MM. HH:MM HH:MM        | Mo 15.03. 08:00 16:30         |
| 5       | DD.MM.YYYY...HH:MM-HH:MM          | 15.03.2026 08:00-16:30        |

### 6.4 Import Flow

1. User taps the purple PDF FAB.
2. System file picker opens, filtered to PDF files.
3. Selected PDF is loaded via `contentResolver.openInputStream()`.
4. pdfbox-android extracts full text content.
5. SAP parser attempts structured extraction.
6. On failure, generic parser scans each line.
7. Results are displayed in `PdfImportDialog`.
8. User confirms "Import All".
9. Each `ParsedWorkEntry` is fed to `repository.addWorkEntry()`.
10. Remote entries (isRemote=true) get `remoteHours = null` (defaults to full hours).
11. Office entries get `remoteHours = 0.0`.

---

## 7. PDF Report Generation (ReportGenerator.kt)

The `ReportGenerator` creates professional monthly PDF reports using the native Android `PdfDocument` API.

### 7.1 Page Setup

- **Format:** A4 (595 x 842 points, 72 DPI)
- **Margins:** 40pt left, 40pt right (effective content width: 515pt)
- **Page break threshold:** Y position > 790pt triggers new page

### 7.2 Report Structure

```
┌─────────────────────────────────────────────────┐
│                                                 │
│  Monatsbericht Maerz 2026            (Title)    │
│                                                 │
│  Soll-Stunden: 176.0h                          │
│  Ist Arbeitszeit: 168.0h                        │
│  Ist Remote: 64.0h                              │
│  Max Remote: 70.4h                              │
│  Quote: 40.0%                                   │
│  ───────────────────────────────────────────     │
│                                                 │
│  Datum      Start  Ende   Pause  Remote  Gesamt │
│  ───────────────────────────────────────────     │
│  2026-03-02  08:00  16:30  30min  8.0h   8.0h   │
│  2026-03-03  09:00  17:30  30min  8.0h   8.0h   │
│  ...                                             │
│                                                 │
└─────────────────────────────────────────────────┘
```

### 7.3 Implementation

```kotlin
object ReportGenerator {
    fun generateMonthlyPdf(
        context: Context,
        monthData: MonthData,
        entries: List<WorkEntry>
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = 50f

        // Paint styles
        val titlePaint = Paint().apply {
            textSize = 20f; color = Color.BLACK; isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            textSize = 12f; color = Color.DKGRAY; isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            textSize = 11f; color = Color.BLACK
        }

        // ... draw title, summary, table header, entries ...

        // Automatic pagination
        for (entry in sortedEntries) {
            if (yPos > 790f) {
                document.finishPage(page)
                val newPageInfo = PdfDocument.PageInfo.Builder(
                    595, 842, document.pages.size + 1
                ).create()
                page = document.startPage(newPageInfo)
                canvas = page.canvas
                yPos = 50f
            }
            // Draw entry row
            yPos += 16f
        }

        // Write to cache file
        val file = File(
            context.cacheDir,
            "Monatsbericht_${monthData.monat}_${monthData.jahr}.pdf"
        )
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }
}
```

### 7.4 Table Column Layout

| Column  | X Position | Content                                |
|---------|------------|----------------------------------------|
| Datum   | 40pt       | ISO date string                        |
| Start   | 160pt      | Start time HH:MM                       |
| Ende    | 230pt      | End time HH:MM                         |
| Pause   | 300pt      | Pause in minutes                       |
| Remote  | 370pt      | Remote hours with 1 decimal            |
| Gesamt  | 450pt      | Total hours with 1 decimal             |

### 7.5 Output

The generated PDF is written to `context.cacheDir` (app-private temporary storage) and shared via `FileProvider` and `ACTION_SEND` intent. The file is named `Monatsbericht_<Month>_<Year>.pdf`.

---

## 8. Build and Deployment

### 8.1 Prerequisites

- **JDK:** 17 (Gradle and Android Gradle Plugin 8.2 need JDK 17; the bytecode target of the app is 1.8)
- **Android SDK:** Compile SDK 34, `local.properties` with `sdk.dir=...` or `ANDROID_HOME`
- **Kotlin:** 1.9.21
- **Gradle:** wrapper 8.5 is included (`./gradlew`, `gradlew.bat` on Windows)
- **iOS:** additionally macOS with Xcode 15 or newer (see 8.7)

### 8.2 Local Build

```bash
# Debug APK (unsigned, debuggable)
./gradlew :androidApp:assembleDebug

# Output: androidApp/build/outputs/apk/debug/androidApp-debug.apk

# Install directly to connected device
./gradlew :androidApp:installDebug

# Clean build
./gradlew clean :androidApp:assembleDebug
```

### 8.3 Release Build

```bash
# Release APK (requires signing configuration)
./gradlew :androidApp:assembleRelease
```

**Signing Configuration (to be added to `androidApp/build.gradle.kts`):**

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**Note:** The current `build.gradle.kts` has `isMinifyEnabled = false` for the release build type. For production releases, enable R8/ProGuard minification and configure signing.

### 8.4 Dependencies

**Root `build.gradle.kts` (plugin versions):**

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    kotlin("android") version "1.9.21" apply false
    kotlin("multiplatform") version "1.9.21" apply false
    kotlin("plugin.serialization") version "1.9.21" apply false
}
```

**Shared module dependencies:**

| Dependency                          | Version | Purpose                   |
|-------------------------------------|---------|---------------------------|
| kotlinx-serialization-json          | 1.6.2   | JSON encode/decode        |
| kotlinx-datetime                    | 0.5.0   | Cross-platform date/time  |

**Android app dependencies:**

| Dependency                          | Version    | Purpose                      |
|-------------------------------------|------------|------------------------------|
| compose-bom                         | 2023.10.01 | Compose version management   |
| compose-ui                          | (BOM)      | Core Compose UI              |
| compose-material3                   | (BOM)      | Material Design 3 components |
| activity-compose                    | 1.8.2      | Compose Activity integration |
| lifecycle-runtime-compose           | 2.7.0      | Lifecycle-aware composables  |
| lifecycle-viewmodel-compose         | 2.7.0      | ViewModel Compose support    |
| core-ktx                            | 1.12.0     | Android KTX extensions       |
| appcompat                           | 1.6.1      | Backward compatibility       |
| material                            | 1.11.0     | Material Design components   |
| pdfbox-android                      | 2.0.27.0   | PDF text extraction          |

### 8.5 SDK Configuration

| Setting      | Value | Notes                                    |
|--------------|-------|------------------------------------------|
| compileSdk   | 34    | Android 14 (API 34)                      |
| minSdk       | 24    | Android 7.0 Nougat -- covers 98%+ devices|
| targetSdk    | 34    | Android 14 behavior                      |
| versionCode  | 1     | Internal build number                    |
| versionName  | "1.0" | User-facing version string               |

### 8.6 CI/CD

The project has two cloud builds:

- **GitHub Actions** (`.github/workflows/ios-build.yml`, workflow "iOS Build"): on every push to `main`,
  on pull requests and on demand (Actions -> iOS Build -> Run workflow) it builds the iOS simulator app
  on a macOS runner and the Android debug APK on Ubuntu. Results are the artifacts `ios-simulator-app`
  and `android-debug-apk`.
- **Codemagic** (`codemagic.yaml`): `ios-workflow` (iOS without signing, simulator/IPA) and
  `android-workflow` (release and debug APK; needs the keystore `remote_work_keystore` and the group
  `google_play` in Codemagic, and the placeholder e-mail address must be replaced).

### 8.7 Build per Platform (Overview)

| Target | Build on | Command | Result |
|---|---|---|---|
| Android debug APK | Linux, macOS, Windows (JDK 17, Android SDK 34) | `./gradlew :androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| Android release APK / AAB | as above, own signing key | `./gradlew :androidApp:assembleRelease` or `:androidApp:bundleRelease` | `androidApp/build/outputs/apk/release/` (unsigned until a signing config is added to `androidApp/build.gradle.kts`) |
| iOS simulator app | macOS only (Xcode 15+) | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`, then `xcodebuild` | `iosApp/build/simulator/.../iosApp.app` |
| iOS without a Mac | GitHub Actions or Codemagic | workflow "iOS Build" or `ios-workflow` | artifact `ios-simulator-app` / IPA |
| iOS device / App Store | macOS or Codemagic, Apple Developer account | signing in Xcode or Codemagic | signed IPA |

iOS cannot be built on Linux or Windows; there Gradle skips the iOS targets, and the Android build and
unit tests still work.

**iOS on a Mac, step by step:**
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64     # Intel Mac: :shared:linkDebugFrameworkIosX64
cd iosApp
xcodebuild build -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -derivedDataPath build/simulator \
  CODE_SIGN_IDENTITY="-" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```
Alternatively open `iosApp/iosApp.xcodeproj` in Xcode and press Run.

**Tests:**
```bash
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest      # JVM, no emulator
./gradlew :androidApp:connectedDebugAndroidTest                         # needs an emulator/device
```
Current state: 119 unit tests in `shared`, 33 in `androidApp`. The iOS UI has no tests of its own; its
logic is the tested `shared` module.

---

## 9. Data Persistence

### 9.1 Storage Mechanism

Data is persisted using Android `SharedPreferences` with the preference file name `remote_work_tracker`:

```kotlin
context.getSharedPreferences("remote_work_tracker", Context.MODE_PRIVATE)
```

The entire application state is stored as a single JSON string under the key `"tracker_state"`.

### 9.2 Serialization

The `kotlinx.serialization` library handles JSON encoding/decoding. The `Json` instance is configured as:

```kotlin
val json = Json {
    ignoreUnknownKeys = true   // Forward compatibility
    prettyPrint = true         // Human-readable backup files
}
```

**Typical serialized state size:** 5-50 KB depending on number of entries (approximately 200 bytes per work entry).

### 9.3 Write Strategy

State is saved after every user action that modifies data:

- Adding a work entry
- Editing a work entry
- Deleting a work entry
- Changing settings (quota, target hours)
- Closing a month
- Clearing all data
- Toggling language or dark mode
- Restoring from backup

`SharedPreferences.edit().apply()` is used (asynchronous write) rather than `commit()` for better UI responsiveness.

### 9.4 Error Recovery

```
App Launch
    │
    ▼
Load JSON from SharedPreferences
    │
    ├── JSON exists and parses successfully ──► Use loaded TrackerState
    │
    ├── JSON exists but fails to parse ──► Create default TrackerState
    │   (corrupt data, schema change)        (previous data is lost)
    │
    └── No JSON stored ──► Create default TrackerState
        (first launch)      (fresh installation)
```

There is no data migration strategy between schema versions. If the serialized format changes incompatibly, the app falls back to defaults.

### 9.5 Backup and Restore

**Backup:** The full `TrackerState` is serialized to a JSON file in `cacheDir` and shared via Android's share intent:

```
Cache file: Backup_<Month>_<Year>.json
MIME type:  application/json
```

**Restore:** A JSON file is selected via the system file picker, read in full, deserialized, and replaces the current state. Invalid JSON returns `null` from `deserializeState()`, showing an error message without crashing.

### 9.6 Data Location

| Item              | Path / Key                                        |
|-------------------|---------------------------------------------------|
| Preferences file  | `/data/data/com.remotework.tracker.android/shared_prefs/remote_work_tracker.xml` |
| State key         | `"tracker_state"`                                 |
| Cache files       | `/data/data/com.remotework.tracker.android/cache/` |
| Exported CSV      | `Export_<Month>_<Year>.csv` (in cache)            |
| Exported PDF      | `Monatsbericht_<Month>_<Year>.pdf` (in cache)    |
| Backup JSON       | `Backup_<Month>_<Year>.json` (in cache)          |

---

## 10. ArbZG Compliance

The German Working Hours Act (Arbeitszeitgesetz, ArbZG) imposes mandatory break regulations. The app enforces these through validation warnings.

### 10.1 Statutory Break Regulations

As implemented in `calculateNetHours()`:

| Gross Working Hours | Minimum Break Required | ArbZG Reference |
|--------------------|-----------------------|-----------------|
| < 6 hours          | None                  | Section 4 ArbZG        |
| 6 to < 9 hours     | 30 minutes            | Section 4 Sentence 1   |
| >= 9 hours          | 45 minutes            | Section 4 Sentence 2   |

```kotlin
val statutoryPause = when {
    grossHours >= 9 -> 0.75   // 45 minutes in hours
    grossHours >= 6 -> 0.5    // 30 minutes in hours
    else -> 0.0
}
```

When the user-entered pause is less than the statutory minimum, a warning is generated:

```
WARNUNG: Pause (0.25h) < gesetzl. Minimum (0.50h)!
```

**Important:** The app generates warnings but does not block entry creation. This allows flexibility for legitimate scenarios while making the user aware of potential compliance issues.

### 10.2 Holiday Calendar (Bavaria 2025-2030)

The following Bavarian public holidays are hardcoded:

**2025:**

| Date       | Holiday                     |
|------------|-----------------------------|
| 2025-01-01 | Neujahr (New Year's Day)    |
| 2025-01-06 | Heilige Drei Koenige        |
| 2025-04-18 | Karfreitag (Good Friday)    |
| 2025-04-21 | Ostermontag (Easter Monday) |
| 2025-05-01 | Tag der Arbeit              |
| 2025-05-29 | Christi Himmelfahrt          |
| 2025-06-09 | Pfingstmontag               |
| 2025-06-19 | Fronleichnam                |
| 2025-08-15 | Maria Himmelfahrt           |
| 2025-10-03 | Tag der Deutschen Einheit   |
| 2025-11-01 | Allerheiligen               |
| 2025-12-25 | 1. Weihnachtsfeiertag       |
| 2025-12-26 | 2. Weihnachtsfeiertag       |

**2026:**

| Date       | Holiday                     |
|------------|-----------------------------|
| 2026-01-01 | Neujahr                     |
| 2026-01-06 | Heilige Drei Koenige        |
| 2026-04-03 | Karfreitag                  |
| 2026-04-06 | Ostermontag                 |
| 2026-05-01 | Tag der Arbeit              |
| 2026-05-14 | Christi Himmelfahrt          |
| 2026-05-25 | Pfingstmontag               |
| 2026-06-04 | Fronleichnam                |
| 2026-08-15 | Maria Himmelfahrt           |
| 2026-10-03 | Tag der Deutschen Einheit   |
| 2026-11-01 | Allerheiligen               |
| 2026-12-25 | 1. Weihnachtsfeiertag       |
| 2026-12-26 | 2. Weihnachtsfeiertag       |

**Maintenance Note:** Holiday dates for years beyond 2026 must be added to `BavarianHolidays` before those years begin. Easter-dependent holidays (Karfreitag, Ostermontag, Christi Himmelfahrt, Pfingstmontag, Fronleichnam) change annually and must be recalculated.

### 10.3 Weekend Validation

Work entries are blocked on:

- **Sundays** -- Unconditionally rejected per ArbZG Section 9.
- **Saturdays** -- Rejected with a note that approved overtime is required. The rejection message reads: "Arbeitszeitprotokollierung an Samstagen ist nur bei genehmigter Mehrarbeit erlaubt."

### 10.4 Remote Work Quota Management

The quota system enforces company-level remote work policies:

| Parameter           | Range   | Default | Notes                              |
|---------------------|---------|---------|-------------------------------------|
| Individual Quota    | 40-60%  | 40%     | Adjustable in settings              |
| Quarterly Adjustment| +/-10%  | --      | Automatic at quarter boundaries     |
| Quota Floor         | 40%     | --      | Cannot go below via auto-adjustment |
| Quota Ceiling       | 60%     | --      | Cannot go above via auto-adjustment |

---

## 11. Security and Privacy

### 11.1 Data Storage

All application data is stored exclusively on the device:

- **SharedPreferences** -- Stored in the app's private directory (`MODE_PRIVATE`). Only accessible to the app itself (enforced by Android's sandboxing).
- **Cache files** -- Temporary export files are written to `cacheDir`, which is app-private and may be cleaned by the system at any time.

### 11.2 No Cloud Synchronization

The app performs **no network requests** of any kind:

- No analytics or tracking
- No crash reporting
- No cloud backup
- No API calls
- No advertising SDKs

The `AndroidManifest.xml` does not declare `INTERNET` permission.

### 11.3 No User Authentication

The app does not implement any authentication mechanism. It is designed as a single-user, single-device application. Anyone with physical access to the unlocked device can access all stored data.

### 11.4 FileProvider for Secure File Sharing

Exported files (CSV, PDF, JSON) are shared with other apps via `FileProvider` rather than raw file paths, following Android security best practices:

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

The `file_paths.xml` configuration restricts shared paths to the cache directory only. URI permissions are granted per-intent with `FLAG_GRANT_READ_URI_PERMISSION`, so receiving apps get temporary read-only access to the specific file being shared.

### 11.5 Data in Transit

When the user shares an export file (via email, messaging, cloud storage), the data leaves the device through the chosen transport mechanism. The app itself provides no encryption for exported files. Users should be aware that backup JSON files contain all work time data in plain text.

### 11.6 Privacy Summary

| Aspect                  | Status                                    |
|-------------------------|-------------------------------------------|
| Data stored locally     | Yes (SharedPreferences, app-private)      |
| Network access          | None (no INTERNET permission)             |
| Cloud sync              | None                                      |
| User authentication     | None                                      |
| Data encryption at rest | Android default (device encryption only)  |
| Export encryption        | None (plain text JSON/CSV)               |
| Third-party SDKs        | pdfbox-android only (offline, no tracking)|
| GDPR compliance         | Inherent (no data leaves device)          |

---

## Appendix A: AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.RemoteWorkTracker">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.RemoteWorkTracker">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

## Appendix B: Glossary

| Term                        | Meaning                                            |
|-----------------------------|-----------------------------------------------------|
| ArbZG                       | Arbeitszeitgesetz (German Working Hours Act)        |
| Soll-Stunden                | Target/contractual working hours                    |
| Ist-Stunden                 | Actual working hours logged                         |
| Remote-Quote                | Percentage of work time allowed to be remote        |
| Quartal                     | Calendar quarter (Q1=Jan-Mar, Q2=Apr-Jun, etc.)    |
| Monatliche Historie         | Monthly history archive of closed months            |
| Pause                       | Break/pause during working hours                    |
| Brutto                      | Gross (before deductions)                           |
| Netto                       | Net (after deductions)                              |
| Feiertag                    | Public holiday                                      |
| KMP                         | Kotlin Multiplatform                                |
| BOM                         | Bill of Materials (dependency version management)   |
| FAB                         | Floating Action Button                              |

---

*Last updated: March 2026*
*Generated from source code analysis of RemoteWorkTrackerKMP v1.0.0*
