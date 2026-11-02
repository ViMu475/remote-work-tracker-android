# Technische Dokumentation -- Remote Work Tracker (KMP Android)

**Version 1.0.0**
**Stand: Maerz 2026**

---

## Inhaltsverzeichnis

1. [Architektur](#1-architektur)
2. [Datenmodell](#2-datenmodell)
3. [Repository (TrackerRepository.kt)](#3-repository-trackerrepositorykt)
4. [Berechnungen (TrackerCalculations.kt)](#4-berechnungen-trackercalculationskt)
5. [Android UI (MainActivity.kt)](#5-android-ui-mainactivitykt)
6. [PDF-Import (PdfParser.kt)](#6-pdf-import-pdfparserkt)
7. [PDF-Berichtgenerierung (ReportGenerator.kt)](#7-pdf-berichtgenerierung-reportgeneratorkt)
8. [Build und Deployment](#8-build-und-deployment)
9. [Datenpersistenz](#9-datenpersistenz)
10. [ArbZG-Konformitaet](#10-arbzg-konformitaet)
11. [Sicherheit und Datenschutz](#11-sicherheit-und-datenschutz)

---

## 1. Architektur

### 1.1 Technologie-Stack

| Komponente              | Technologie                                   | Version      |
|-------------------------|-----------------------------------------------|--------------|
| Programmiersprache      | Kotlin                                        | 1.9.21       |
| Multiplatform-Framework | Kotlin Multiplatform (KMP)                    | 1.9.21       |
| UI-Framework            | Jetpack Compose                               | BOM 2023.10.01 |
| Design-System           | Material 3 (Material Design 3)                | aktuell      |
| JSON-Serialisierung     | kotlinx.serialization                         | 1.6.2        |
| Datumsberechnungen      | kotlinx.datetime                              | 0.5.0        |
| PDF-Parsing             | pdfbox-android (com.tom-roush)                | 2.0.27.0     |
| PDF-Generierung         | Android PdfDocument API                       | Plattform    |
| Lokale Speicherung      | SharedPreferences                             | Plattform    |
| Build-System            | Gradle (Kotlin DSL)                           | 8.2.0        |
| Android Compile SDK     | 34                                            |              |
| Android Min SDK         | 24 (Android 7.0 Nougat)                       |              |
| Android Target SDK      | 34 (Android 14)                               |              |
| JVM-Target              | 1.8                                           |              |

### 1.2 Projektstruktur

```
RemoteWorkTrackerKMP/
├── build.gradle.kts                 # Root-Build-Konfiguration (Plugin-Versionen)
├── settings.gradle.kts              # Projektmodule: :shared, :androidApp
├── gradle.properties                # Gradle-Einstellungen
├── gradlew / gradlew.bat            # Gradle-Wrapper
├── codemagic.yaml                   # CI/CD-Konfiguration (Codemagic)
│
├── shared/                          # Plattformuebergreifender Code (KMP)
│   ├── build.gradle.kts             # KMP-Konfiguration (Android + iOS Targets)
│   └── src/
│       ├── commonMain/kotlin/com/remotework/tracker/
│       │   ├── Models.kt            # Datenmodelle (TrackerState, WorkEntry, MonthData)
│       │   ├── TrackerRepository.kt # Geschaeftslogik, Persistenz, expect-Deklaration
│       │   └── TrackerCalculations.kt # Zeitberechnung, Validierung, Feiertage
│       ├── androidMain/kotlin/com/remotework/tracker/
│       │   └── LocalStorage.kt      # Android-Implementierung (SharedPreferences)
│       └── iosMain/kotlin/com/remotework/tracker/
│           └── LocalStorage.kt      # iOS-Implementierung (NSUserDefaults)
│
├── androidApp/                      # Android-spezifischer Code
│   ├── build.gradle.kts             # Android-App-Konfiguration, Compose, Abhaengigkeiten
│   └── src/main/
│       ├── kotlin/com/remotework/tracker/android/
│       │   ├── MainActivity.kt      # Jetpack Compose UI (alle Composables)
│       │   ├── PdfParser.kt         # SAP-Zeitnachweis PDF-Parser
│       │   └── ReportGenerator.kt   # Monats-PDF-Berichtgenerierung
│       ├── AndroidManifest.xml      # App-Manifest
│       └── res/                     # Android-Ressourcen
│
└── iosApp/                          # iOS-App (SwiftUI) -- separate Dokumentation
```

### 1.3 Architektur-Diagramm

```
┌─────────────────────────────────────────────────────────┐
│                    Android UI Layer                      │
│  ┌─────────────────────────────────────────────────┐    │
│  │  MainActivity.kt (Jetpack Compose)              │    │
│  │  - TrackerApp (Haupt-Composable)                │    │
│  │  - MonthOverviewCard, RemoteProgressCard         │    │
│  │  - WorkEntryCard, AddEntryDialog                 │    │
│  │  - SettingsBottomSheet, HistoryCard              │    │
│  │  - PdfImportDialog, MonthSelectorRow             │    │
│  └───────────────┬─────────────────────────────────┘    │
│                  │ State<TrackerState>                    │
│  ┌───────────────┴─────────────────────────────────┐    │
│  │  PdfParser.kt        ReportGenerator.kt         │    │
│  │  (PDF-Import)        (PDF-Export)                │    │
│  └──────────┬──────────────────┬───────────────────┘    │
└─────────────┼──────────────────┼────────────────────────┘
              │                  │
┌─────────────┼──────────────────┼────────────────────────┐
│             │   Shared (commonMain)                      │
│  ┌──────────┴──────────────────┴───────────────────┐    │
│  │  TrackerRepository.kt                           │    │
│  │  - loadState() / saveState()                    │    │
│  │  - addWorkEntry() / updateEntry() / deleteEntry()│   │
│  │  - closeMonth() / closeSpecificMonth()          │    │
│  │  - recalculateMonthlyTotals()                   │    │
│  │  - generateCsv() / serializeState()             │    │
│  └───────────────┬─────────────────────────────────┘    │
│  ┌───────────────┴─────────────────────────────────┐    │
│  │  TrackerCalculations.kt                         │    │
│  │  - timeToMinutes() / calculateNetHours()        │    │
│  │  - isValidWorkingDay() / checkTimeOverlap()     │    │
│  │  - calculateSollStundenMonth()                  │    │
│  └─────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────┐    │
│  │  Models.kt                                      │    │
│  │  TrackerState, WorkEntry, MonthData              │    │
│  │  MonthNames, BavarianHolidays                   │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
              │
┌─────────────┴───────────────────────────────────────────┐
│  Platform Layer (expect/actual)                          │
│  ┌─────────────────┐  ┌────────────────────────────┐    │
│  │ androidMain     │  │ iosMain                    │    │
│  │ LocalStorage    │  │ LocalStorage               │    │
│  │ (SharedPrefs)   │  │ (NSUserDefaults)           │    │
│  └─────────────────┘  └────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 1.4 State-Management-Prinzip

Die App verwendet ein **immutables State-Modell** mit manueller Rekomposition:

```
TrackerState (immutable data class)
       │
       ▼
MutableState<TrackerState>  ←──  Compose beobachtet Aenderungen
       │
       ▼
Repository-Methode aufrufen  →  Neuer TrackerState zurueckgegeben
       │
       ▼
state = newState  →  Compose-Rekomposition ausgeloest
       │
       ▼
repository.saveState(state)  →  SharedPreferences aktualisiert
```

Jede Zustandsaenderung erzeugt ein **neues TrackerState-Objekt** via `copy()`. Die UI
beobachtet den `MutableState<TrackerState>` und rekomponiert automatisch bei Aenderungen.

---

## 2. Datenmodell

Alle Datenmodelle befinden sich in `shared/src/commonMain/kotlin/com/remotework/tracker/Models.kt`
und sind mit `@Serializable` (kotlinx.serialization) annotiert fuer JSON-Persistenz.

### 2.1 TrackerState (Root State)

Der zentrale Anwendungszustand. Enthaelt alle Daten der App in einer einzigen serialisierbaren Struktur.

```kotlin
@Serializable
data class TrackerState(
    val aktuellerMonat: String,              // Aktueller Monatsname (deutsch), z.B. "Januar"
    val aktuellesJahr: Int,                  // Aktuelles Jahr, z.B. 2026
    val currentMonthData: MonthData,         // Berechnete Daten des aktuellen Monats
    val currentQuartal: String,              // Aktuelles Quartal, z.B. "Q1/2026"
    val dailyRecords: Map<String, List<WorkEntry>> = emptyMap(),
        // Schluessel: Datum "YYYY-MM-DD", Wert: Liste der Eintraege pro Tag
    val monatlicheHistorie: List<MonthData> = emptyList(),
        // Archivierte Monatsdaten nach Monatsabschluss
    val individuelleQuoteProzent: Double = 40.0,
        // Individuelle Remote-Quote (40-60%)
    val sollStundenProTag: Double = 8.0,
        // Soll-Arbeitsstunden pro Tag (1-10)
    val language: String = "de",
        // Sprache: "de" oder "en"
    val darkMode: Boolean = false
        // Dunkelmodus aktiv/inaktiv
)
```

**Felder im Detail:**

| Feld                      | Typ                           | Standard     | Beschreibung                                    |
|---------------------------|-------------------------------|--------------|-------------------------------------------------|
| `aktuellerMonat`          | `String`                      | --           | Deutscher Monatsname des angezeigten Monats     |
| `aktuellesJahr`           | `Int`                         | --           | Jahr des angezeigten Monats                     |
| `currentMonthData`        | `MonthData`                   | --           | Berechnete Zusammenfassung des Monats           |
| `currentQuartal`          | `String`                      | --           | Format: "Q{1-4}/{Jahr}"                         |
| `dailyRecords`            | `Map<String, List<WorkEntry>>`| `emptyMap()` | Alle Arbeitszeiteintraege, nach Datum gruppiert  |
| `monatlicheHistorie`      | `List<MonthData>`             | `emptyList()`| Archiv abgeschlossener Monate                   |
| `individuelleQuoteProzent`| `Double`                      | `40.0`       | Remote-Quote in Prozent                         |
| `sollStundenProTag`       | `Double`                      | `8.0`        | Tagessoll in Stunden                            |
| `language`                | `String`                      | `"de"`       | Anzeigesprache                                  |
| `darkMode`                | `Boolean`                     | `false`      | Dunkelmodus                                     |

### 2.2 WorkEntry

Repraesentiert einen einzelnen Arbeitszeitblock an einem Tag. Mehrere Eintraege pro Tag sind moeglich.

```kotlin
@Serializable
data class WorkEntry(
    val id: String,                    // Eindeutige ID: "YYYY-MM-DD|HH:MM|HH:MM|timestamp"
    val datum: String,                 // Datum im Format "YYYY-MM-DD"
    val startTime: String,             // Startzeit "HH:MM"
    val endTime: String,               // Endzeit "HH:MM"
    val pauseMinutes: Double,          // Pausendauer in Minuten
    val remoteStundenHeute: Double,    // Remote-Arbeitsstunden (netto)
    val totalStundenHeute: Double,     // Gesamtarbeitsstunden (netto, nach Pausenabzug)
    val pauseWarning: String? = null,  // Warnung bei ArbZG-Verstoss (oder null)
    val source: String? = null         // "pdf_import" bei Import aus einem Zeitnachweis, sonst null
)
```

**ID-Format:** Die ID setzt sich zusammen aus `"Datum|Startzeit|Endzeit|System.currentTimeMillis()"`,
z.B. `"2026-03-15|08:00|16:30|1711532400000"`. Bei Bearbeitung (`updateEntry`) bleibt die
urspruengliche ID erhalten.

**Pausenwarnung:** Wenn die eingegebene Pause unter dem gesetzlichen Minimum liegt,
wird ein Warntext gesetzt, z.B.:
`"WARNUNG: Pause (0.25h) < gesetzl. Minimum (0.50h)!"`

**Importierte Eintraege:** Ein Zeitnachweis listet Arbeitsabschnitte ohne Pausen, die Pause wird
deshalb mit 0 gespeichert. `importWorkEntry(...)` (statt `addWorkEntry(...)`) setzt `source =
"pdf_import"` und speichert **keine** Pausenwarnung. Die Oberflaechen blenden die Warnung fuer
Eintraege mit `source == "pdf_import"` aus. Manuell erfasste Eintraege werden unveraendert geprueft.

### 2.3 MonthData

Zusammenfassende Berechnung fuer einen Monat. Wird sowohl fuer den aktuellen Monat
als auch fuer archivierte Monate verwendet.

```kotlin
@Serializable
data class MonthData(
    val monat: String,                     // Monatsname deutsch, z.B. "Maerz"
    val jahr: Int,                         // Jahr
    val individuelleQuote: Double,         // Gueltige Quote zum Zeitpunkt der Berechnung
    val sollStundenGesamt: Double,         // Soll-Stunden gesamt (Arbeitstage x Soll/Tag)
    val maxRemoteStunden: Double,          // Maximum erlaubte Remote-Stunden
    val istRemoteStundenLaufend: Double,   // Tatsaechliche Remote-Stunden (laufend)
    val istArbeitszeitLaufend: Double,     // Tatsaechliche Gesamtarbeitszeit (laufend)
    val tatsaechlicheRemoteQuote: Double = 0.0,
        // Berechnete tatsaechliche Quote: istRemote / sollGesamt * 100
    val quartal: String? = null
        // Quartalskennzeichnung (wird beim Monatsabschluss gesetzt)
)
```

**Berechnungslogik:**
- `sollStundenGesamt` = Anzahl Arbeitstage im Monat x `sollStundenProTag`
- `maxRemoteStunden` = `sollStundenGesamt` x (`individuelleQuote` / 100)
- `tatsaechlicheRemoteQuote` = (`istRemoteStundenLaufend` / `sollStundenGesamt`) x 100
- Verbleibend = `maxRemoteStunden` - `istRemoteStundenLaufend`

### 2.4 Hilfsklassen

#### MonthNames

Zuordnung deutscher Monatsnamen zu Monatsnummern (1-12).

```kotlin
object MonthNames {
    val german = listOf(
        "Januar", "Februar", "Maerz", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    )

    val monthNumbers = german.mapIndexed { index, name -> name to index + 1 }.toMap()

    fun getMonthNumber(name: String): Int = monthNumbers[name] ?: 1
    fun getMonthName(number: Int): String = german.getOrElse(number - 1) { "Januar" }
}
```

Verwendung: Konvertierung zwischen Monatsnamen (fuer Anzeige/Persistenz) und
Monatsnummern (fuer Datumsberechnungen).

#### BavarianHolidays

Gesetzliche Feiertage in Bayern fuer 2025 und 2026.

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
        "2025-08-15",  // Mariae Himmelfahrt
        "2025-10-03",  // Tag der Deutschen Einheit
        "2025-11-01",  // Allerheiligen
        "2025-12-25",  // 1. Weihnachtsfeiertag
        "2025-12-26"   // 2. Weihnachtsfeiertag
    )

    val holidays2026 = setOf(
        "2026-01-01",  // Neujahr
        "2026-01-06",  // Heilige Drei Koenige
        "2026-04-03",  // Karfreitag
        "2026-04-06",  // Ostermontag
        "2026-05-01",  // Tag der Arbeit
        "2026-05-14",  // Christi Himmelfahrt
        "2026-05-25",  // Pfingstmontag
        "2026-06-04",  // Fronleichnam
        "2026-08-15",  // Mariae Himmelfahrt
        "2026-10-03",  // Tag der Deutschen Einheit
        "2026-11-01",  // Allerheiligen
        "2026-12-25",  // 1. Weihnachtsfeiertag
        "2026-12-26"   // 2. Weihnachtsfeiertag
    )

    fun isHoliday(date: String): Boolean = date in holidays2025 || date in holidays2026
}
```

**Hinweis:** Feiertage sind nur fuer 2025-2030 hinterlegt. Fuer spaetere Jahre muss die
Liste manuell erweitert werden. Bewegliche Feiertage (Ostern, Pfingsten, Christi Himmelfahrt,
Fronleichnam) aendern sich jaehrlich.

#### TimeResult

Rueckgabewert der Nettostundenberechnung.

```kotlin
data class TimeResult(
    val grossHours: Double,     // Bruttostunden (ohne Pausenabzug)
    val netHours: Double,       // Nettostunden (nach Pausenabzug)
    val pauseWarning: String?   // Gesetzliche Pausenwarnung oder null
)
```

#### ValidationResult

Rueckgabewert der Arbeitstag-Validierung.

```kotlin
data class ValidationResult(
    val isValid: Boolean,        // true = gueltiger Arbeitstag
    val errorMessage: String?    // Fehlermeldung bei ungueltigem Tag
)
```

---

## 3. Repository (TrackerRepository.kt)

Die Klasse `TrackerRepository` ist die zentrale Geschaeftslogik-Schicht. Sie verwaltet
den Anwendungszustand, fuehrt Validierungen durch und orchestriert alle Datenaenderungen.

```kotlin
class TrackerRepository(private val storage: LocalStorage) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val stateKey = "tracker_state"
    // ...
}
```

Die `LocalStorage`-Klasse ist eine `expect/actual`-Deklaration:

```kotlin
// commonMain -- expect-Deklaration
expect class LocalStorage {
    fun save(key: String, value: String)
    fun load(key: String): String?
    fun clear(key: String)
}

// androidMain -- actual-Implementierung
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

### 3.1 Persistenz

#### loadState()

Laedt den gespeicherten Zustand aus SharedPreferences. Bei fehlenden oder korrupten
Daten wird ein Standardzustand erzeugt.

```kotlin
fun loadState(): TrackerState {
    val stored = storage.load(stateKey)
    return if (stored != null) {
        try {
            json.decodeFromString<TrackerState>(stored)
        } catch (e: Exception) {
            createDefaultState()  // Fallback bei korrupten Daten
        }
    } else {
        createDefaultState()      // Erster Start
    }
}
```

**Verhalten:**
- Gespeicherte Daten vorhanden und gueltig: Deserialisierung und Rueckgabe
- Daten vorhanden aber korrupt (`Exception`): Neuer Standardzustand
- Keine Daten vorhanden (`null`): Neuer Standardzustand
- `ignoreUnknownKeys = true` stellt Vorwaertskompatibilitaet sicher

#### saveState()

Serialisiert den aktuellen Zustand und speichert ihn.

```kotlin
fun saveState(state: TrackerState) {
    storage.save(stateKey, json.encodeToString(state))
}
```

Die Serialisierung verwendet `prettyPrint = true` fuer lesbare JSON-Ausgabe (relevant fuer Backup-Export).

#### clearAll()

Loescht alle gespeicherten Daten.

```kotlin
fun clearAll() {
    storage.clear(stateKey)
}
```

#### serializeState() / deserializeState()

Konvertierung fuer Backup/Restore-Funktionalitaet.

```kotlin
fun serializeState(state: TrackerState): String {
    return json.encodeToString(state)
}

fun deserializeState(jsonString: String): TrackerState? {
    return try {
        json.decodeFromString<TrackerState>(jsonString)
    } catch (e: Exception) {
        null  // null bei ungueltigem JSON
    }
}
```

### 3.2 Arbeitszeiterfassung

#### addWorkEntry()

Fuegt einen neuen Arbeitseintrag hinzu. Fuehrt umfassende Validierung durch.

```kotlin
fun addWorkEntry(
    state: TrackerState,
    datum: String,           // "YYYY-MM-DD"
    startTime: String,       // "HH:MM"
    endTime: String,         // "HH:MM"
    pauseMinutes: Double,    // Pause in Minuten
    remoteHours: Double?     // Remote-Stunden (null = alle Stunden sind remote)
): Result<TrackerState>
```

**Validierungsschritte (in Reihenfolge):**

1. **Arbeitstag-Pruefung:** `isValidWorkingDay()` -- Feiertag? Wochenende?
2. **Ueberlappungspruefung:** `checkTimeOverlap()` -- Zeitkollision mit bestehenden Eintraegen?
3. **Stundenberechnung:** `calculateNetHours()` -- Brutto/Netto-Berechnung, Pausenwarnung
4. **Negativpruefung:** Nettostunden < 0? Dann Fehler.
5. **Remote-Stunden-Pruefung:** Remote > Netto? Dann Fehler.
6. **ID-Generierung:** `"Datum|Start|Ende|Timestamp"`
7. **State-Update:** Neuer Eintrag wird in `dailyRecords` eingefuegt
8. **Neuberechnung:** `recalculateMonthlyTotals()` aktualisiert `currentMonthData`

**Rueckgabe:** `Result<TrackerState>` -- entweder `Result.success(neuerState)` oder
`Result.failure(Exception(fehlermeldung))`.

#### updateEntry()

Bearbeitet einen bestehenden Eintrag. Die ID bleibt erhalten.

```kotlin
fun updateEntry(
    state: TrackerState,
    entryId: String,         // Bestehende Entry-ID (wird beibehalten)
    datum: String,
    startTime: String,
    endTime: String,
    pauseMinutes: Double,
    remoteHours: Double?
): Result<TrackerState>
```

**Ablauf:**
1. Validierung wie bei `addWorkEntry()` (Arbeitstag, Overlap, Stunden)
2. Ueberlappungspruefung mit `excludeId = entryId` (eigener Eintrag ausgeschlossen)
3. Alter Eintrag wird aus `dailyRecords` entfernt
4. Neuer Eintrag mit **gleicher ID** wird erstellt und eingefuegt
5. Neuberechnung der Monatstotale

#### deleteEntry()

Entfernt einen Eintrag anhand seiner ID.

```kotlin
fun deleteEntry(state: TrackerState, entryId: String): TrackerState
```

**Ablauf:**
1. Iteration ueber alle `dailyRecords`
2. Eintrag mit passender ID wird gefiltert
3. Leere Tageslisten werden entfernt (`updatedRecords.remove(datum)`)
4. Neuberechnung der Monatstotale

### 3.3 Monatsverwaltung

#### recalculateMonthlyTotals()

Neuberechnung aller Monatskennzahlen basierend auf den aktuellen Eintraegen.

```kotlin
fun recalculateMonthlyTotals(state: TrackerState): TrackerState
```

**Berechnung:**
1. Filtert `dailyRecords` nach aktuellem Monat/Jahr
2. Summiert `remoteStundenHeute` und `totalStundenHeute` aller Eintraege
3. Berechnet `sollStundenGesamt` ueber `calculateSollStundenMonth()`
4. Berechnet `maxRemoteStunden` = `sollGesamt` x (`quote` / 100)
5. Berechnet `tatsaechlicheQuote` = (`totalRemote` / `sollGesamt`) x 100
6. Gibt neuen State mit aktualisiertem `currentMonthData` zurueck

Diese Methode wird nach **jeder Datenaenderung** aufgerufen (Add, Update, Delete, Settings).

#### calculateMonthData()

Berechnet `MonthData` fuer einen beliebigen Monat (nicht nur den aktuellen).

```kotlin
fun calculateMonthData(state: TrackerState, year: Int, monthNum: Int): MonthData
```

Wird verwendet fuer:
- Monatsabschluss (`closeSpecificMonth`)
- Monatswechsel in der UI (`switchToMonth`)

#### closeMonth()

Schliesst den aktuellen Monat ab und wechselt zum naechsten.

```kotlin
fun closeMonth(state: TrackerState): Pair<TrackerState, String?>
```

**Ablauf:**
1. Aktuellen Monat als `MonthData` in `monatlicheHistorie` archivieren
2. Quartalsanpassung pruefen (`checkQuarterlyAdjustment`)
3. Naechsten Monat berechnen (Dezember -> Januar des Folgejahres)
4. Neue `MonthData` fuer den neuen Monat initialisieren
5. Rueckgabe: Neuer State + optionale Quartalsmeldung

#### closeSpecificMonth()

Erweiterte Version: Schliesst einen bestimmten Monat (nicht nur den aktuellen).

```kotlin
fun closeSpecificMonth(state: TrackerState, year: Int, monthNum: Int): Pair<TrackerState, String?>
```

**Besonderheiten:**
- Prueft ob der Monat bereits in der Historie ist (Duplikatvermeidung)
- Entfernt Eintraege des geschlossenen Monats aus `dailyRecords`
- Sortiert die Historie chronologisch
- Wechselt die Ansicht zum neuesten verbleibenden Monat
- Fuehrt Quartalsanpassung durch

#### switchToMonth()

Wechselt die Monatsansicht ohne Daten zu veraendern.

```kotlin
fun switchToMonth(state: TrackerState, year: Int, monthNum: Int): TrackerState
```

Aktualisiert `aktuellerMonat`, `aktuellesJahr`, `currentMonthData` und `currentQuartal`.

#### getMonthsFromEntries()

Ermittelt alle Monate, fuer die Eintraege existieren.

```kotlin
fun getMonthsFromEntries(state: TrackerState): List<Pair<Int, Int>>
// Rueckgabe: Liste von Pair(Jahr, Monatsnummer), chronologisch sortiert
```

#### getEntriesForMonth()

Filtert Eintraege fuer einen bestimmten Monat.

```kotlin
fun getEntriesForMonth(state: TrackerState, year: Int, monthNum: Int): List<WorkEntry>
// Rueckgabe: Nach Datum sortierte Eintraege
```

### 3.4 Einstellungen

#### updateSettings()

Aktualisiert Remote-Quote und Soll-Stunden mit Validierung.

```kotlin
fun updateSettings(
    state: TrackerState,
    newQuote: Double,         // Muss zwischen 40.0 und 60.0 liegen
    newSollStunden: Double    // Muss zwischen 1.0 und 10.0 liegen
): Result<TrackerState>
```

**Validierung:**
- Quote < 40% oder > 60% -> `Result.failure`
- Soll-Stunden < 1 oder > 10 -> `Result.failure`
- Bei Erfolg: Neuberechnung aller Monatskennzahlen

### 3.5 Quartalsanpassung

#### checkQuarterlyAdjustment()

Automatische Anpassung der Remote-Quote am Quartalsende.

```kotlin
private fun checkQuarterlyAdjustment(
    state: TrackerState,
    monthNum: Int,
    year: Int
): Pair<TrackerState, String?>
```

**Logik:**
- Wird nur bei Quartalsende-Monaten ausgefuehrt (3, 6, 9, 12)
- Benoetigt alle 3 Quartalsmonate in der Historie
- Vergleicht tatsaechliche Remote-Stunden mit maximal erlaubten Remote-Stunden
- **Ueber Quote:** Quote wird um 10% reduziert (Minimum 40%)
- **Unter Quote:** Quote wird um 10% erhoeht (Maximum 60%)
- Gibt optionale Meldung an die UI zurueck

```
Beispiel:
Q1/2026 (Januar, Februar, Maerz):
  Summe istRemote: 200h
  Summe maxRemote: 180h
  → Remote UEBER Quote → Quote von 50% auf 40% reduziert
  → Meldung: "Quartal abgeschlossen. Remote ueber Quote! Neue Quote: 40%"
```

### 3.6 Datenexport

#### generateCsv()

Erzeugt einen CSV-String fuer den Export eines Monats.

```kotlin
fun generateCsv(state: TrackerState, year: Int, monthNum: Int): String
```

**CSV-Format:**
```csv
Datum,Start,Ende,Pause (min),Remote (h),Gesamt (h)
2026-03-03,08:00,16:30,30.0,8.0,8.0
2026-03-04,09:00,17:30,30.0,8.0,8.0
```

Die Eintraege sind nach Datum sortiert. Die CSV-Datei wird im Cache-Verzeichnis
der App gespeichert und ueber einen Share-Intent geteilt.

### 3.7 Historie und Abschlussbericht (FinalReport.kt)

`FinalReport` (Modul `shared`, `commonMain`) enthaelt die plattformuebergreifende Logik fuer die
Historie. Android und iOS rufen dieselben Funktionen auf, die Regeln sind identisch zu den
anderen Varianten der App.

| Funktion | Aufgabe |
|---|---|
| `isOverQuota(month)` | `true`, wenn `istRemoteStundenLaufend > maxRemoteStunden` (verglichen auf 2 Nachkommastellen). Die Oberflaeche faerbt die Karte dann **rot**. |
| `sortHistory(history, newestFirst)` | sortiert nach Jahr und Monat; die Historie wird neueste zuerst angezeigt |
| `build(history)` | Abschlussbericht: Monate je Quartal gruppiert, Quartale aufsteigend, doppelte Monate zaehlen einmal, fehlendes Quartalslabel wird berechnet |
| `rate(remote, limit)` | Bewertung eines Quartals |

**Bewertung eines Quartals** (Anteil am Remote-Limit des Quartals, Summe der Monats-Limits):
bis 90 % **GOOD** (gut), bis 100 % **TIGHT** (knapp), ueber 100 % **EXCEEDED** (ueberschritten).
Ein Quartal mit weniger als 3 Monaten gilt als unvollstaendig (`complete = false`). Zusaetzlich
berechnet der Bericht Summen, die tatsaechliche Quote, die Zahl der Monate ueber dem Limit und eine
Gesamtbewertung (`Overall`). Ohne abgeschlossene Monate ist `overall.rating` gleich `null`.

Tests: `shared/src/commonTest/.../FinalReportTest.kt` (13 Faelle, dieselben wie in den anderen
Varianten der App).

---

## 4. Berechnungen (TrackerCalculations.kt)

Das Singleton-Objekt `TrackerCalculations` enthaelt alle rechnungsrelevanten
Funktionen. Es ist zustandslos und plattformunabhaengig.

### 4.1 Zeitberechnung

#### timeToMinutes()

Konvertiert eine Zeitangabe im Format "HH:MM" in Minuten seit Mitternacht.

```kotlin
fun timeToMinutes(timeStr: String): Int {
    val parts = timeStr.split(":")
    return if (parts.size == 2) {
        parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
    } else 0
}
```

**Beispiele:**
- `"08:00"` -> `480`
- `"16:30"` -> `990`
- `"00:00"` -> `0`
- Ungueltige Eingabe -> `0`

#### calculateNetHours()

Berechnet Nettostunden nach Pausenabzug und prueft gesetzliche Pausenregeln.

```kotlin
fun calculateNetHours(startTime: String, endTime: String, pauseMinutes: Double): TimeResult
```

**Berechnungslogik:**
1. Start- und Endzeit in Minuten konvertieren
2. **Nachtschicht-Handling:** Wenn `endMin < startMin`, wird `endMin += 24 * 60`
   (Arbeit ueber Mitternacht hinaus)
3. Bruttostunden = (`endMin` - `startMin`) / 60
4. Nettostunden = Bruttostunden - (`pauseMinutes` / 60)
5. **ArbZG-Pausenpruefung:**
   - >= 9h Brutto: mindestens 45 Minuten Pause
   - >= 6h Brutto: mindestens 30 Minuten Pause
   - < 6h Brutto: keine Mindestpause
6. Warnung wenn tatsaechliche Pause < gesetzliches Minimum

**Beispiel:**
```
calculateNetHours("08:00", "17:00", 30.0)
→ Brutto: 9.0h, Netto: 8.5h
→ Gesetzl. Minimum: 45 min (>= 9h)
→ Tatsaechlich: 30 min
→ WARNUNG: "Pause (0.50h) < gesetzl. Minimum (0.75h)!"
→ TimeResult(9.0, 8.5, "WARNUNG: ...")
```

### 4.2 Validierung

#### isValidWorkingDay()

Prueft ob ein Datum ein gueltiger Arbeitstag ist.

```kotlin
fun isValidWorkingDay(dateString: String): ValidationResult
```

**Pruefungen in Reihenfolge:**
1. Datum parsen (Format `YYYY-MM-DD`)
2. Feiertags-Check: `BavarianHolidays.isHoliday()`
3. Sonntags-Check: `DayOfWeek.SUNDAY`
4. Samstags-Check: `DayOfWeek.SATURDAY` (Warnung, nicht vollstaendig blockiert)

**Fehlermeldungen:**
- Feiertag: "Arbeitszeitprotokollierung an einem Feiertag ist nicht zulaessig."
- Sonntag: "Arbeitszeitprotokollierung an Sonntagen ist nicht zulaessig."
- Samstag: "Arbeitszeitprotokollierung an Samstagen ist nur bei genehmigter Mehrarbeit erlaubt."
- Ungueltiges Format: "Ungueltiges Datumsformat."

#### checkTimeOverlap()

Erkennt Zeitkollisionen zwischen einem neuen und bestehenden Eintraegen.

```kotlin
fun checkTimeOverlap(
    datum: String,
    startTime: String,
    endTime: String,
    existingEntries: List<WorkEntry>,
    excludeId: String? = null       // Eigene ID bei Bearbeitung ausschliessen
): String?
```

**Ueberlappungserkennung (vier Faelle):**
1. Neuer Start liegt innerhalb eines bestehenden Eintrags
2. Neues Ende liegt innerhalb eines bestehenden Eintrags
3. Neuer Eintrag umschliesst einen bestehenden vollstaendig
4. Bestehender Eintrag umschliesst den neuen vollstaendig

Beide Zeitbereiche werden fuer Nachtschichten normalisiert (`+24*60` wenn `end < start`).

**Rueckgabe:** Fehlermeldung-String bei Ueberlappung, `null` wenn keine Kollision.

### 4.3 Monatsberechnung

#### calculateSollStundenMonth()

Berechnet die Soll-Arbeitsstunden fuer einen Monat.

```kotlin
fun calculateSollStundenMonth(year: Int, month: Int, sollStundenProTag: Double): Double
```

**Algorithmus:**
1. Ersten und letzten Tag des Monats bestimmen
2. Ueber jeden Tag iterieren
3. Zaehle nur Tage die KEIN Samstag, Sonntag oder Feiertag sind
4. Arbeitstage x `sollStundenProTag` = Soll-Stunden gesamt

```kotlin
// Beispiel: Maerz 2026
// 31 Tage - 8 Wochenendtage - 0 Feiertage = 23 Arbeitstage
// 23 x 8.0h = 184.0h Soll-Stunden
```

#### getQuartalForMonth()

Bestimmt das Quartal fuer einen gegebenen Monat.

```kotlin
fun getQuartalForMonth(monthName: String, year: Int): String {
    val monthNum = MonthNames.getMonthNumber(monthName)
    val q = ((monthNum - 1) / 3) + 1
    return "Q$q/$year"
}
```

**Zuordnung:**
- Jan-Maerz -> Q1
- Apr-Jun -> Q2
- Jul-Sep -> Q3
- Okt-Dez -> Q4

#### getCurrentDate()

Ermittelt das aktuelle Systemdatum in der lokalen Zeitzone.

```kotlin
fun getCurrentDate(): LocalDate {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
```

---

## 5. Android UI (MainActivity.kt)

Die gesamte UI ist in einer einzigen Datei implementiert und besteht aus
Jetpack Compose Composable-Funktionen mit Material 3 Design.

### 5.1 Theme

#### RemoteWorkTrackerTheme

Unterstuetzt Light und Dark Mode mit angepasster Farbpalette.

```kotlin
@Composable
fun RemoteWorkTrackerTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkMode) {
        darkColorScheme(
            primary = Color(0xFF3B82F6),      // Blau
            background = Color(0xFF1F2937),    // Dunkelgrau
            surface = Color(0xFF141922),       // Sehr dunkles Grau
            onBackground = Color(0xFFE6E9EF), // Helles Grau
            onSurface = Color(0xFFE6E9EF),
            secondary = Color(0xFF10B981),     // Gruen
            error = Color(0xFFEF4444)          // Rot
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4F46E5),       // Indigo
            secondary = Color(0xFF10B981),     // Gruen
            error = Color(0xFFEF4444)          // Rot
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

**Farbpalette:**

| Verwendung        | Light Mode    | Dark Mode     |
|-------------------|---------------|---------------|
| Primary           | `#4F46E5`     | `#3B82F6`     |
| Secondary         | `#10B981`     | `#10B981`     |
| Error             | `#EF4444`     | `#EF4444`     |
| Background        | System        | `#1F2937`     |
| Surface           | System        | `#141922`     |
| On Background     | System        | `#E6E9EF`     |

**Weitere UI-Farben (in Composables):**
- Monatsueberblick-Card: `#E0E7FF` (Indigo-hell)
- Stat-Cards: `#F3F4F6` (Grau-hell)
- PDF-FAB: `#8B5CF6` (Violett)
- Backup-Button: `#3B82F6` (Blau)
- Restore-Button: `#F59E0B` (Amber)
- CSV-Button: `#10B981` (Gruen)
- Monatsabschluss-Button: `#F59E0B` (Amber)

### 5.2 Lokalisierung

Zweisprachige Unterstuetzung (Deutsch/Englisch) ueber eine einfache Map-basierte
Uebersetzungsinfrastruktur.

```kotlin
private val translations = mapOf(
    "de" to mapOf(
        "add_entry" to "Eintrag hinzufuegen",
        "edit_entry" to "Eintrag bearbeiten",
        "settings" to "Einstellungen",
        "remote_quote" to "Remote-Quote",
        "target_hours" to "Soll-Stunden/Tag",
        "save" to "Speichern",
        "cancel" to "Abbrechen",
        "delete" to "Loeschen",
        // ... 30+ weitere Schluessel
    ),
    "en" to mapOf(
        "add_entry" to "Add Entry",
        "edit_entry" to "Edit Entry",
        // ... entsprechende englische Uebersetzungen
    )
)

private fun t(key: String, lang: String): String =
    translations[lang]?.get(key) ?: key
```

**Uebersetzungsschluessel (vollstaendige Liste):**

| Schluessel          | Deutsch                          | Englisch                    |
|---------------------|----------------------------------|-----------------------------|
| `add_entry`         | Eintrag hinzufuegen              | Add Entry                   |
| `edit_entry`        | Eintrag bearbeiten               | Edit Entry                  |
| `settings`          | Einstellungen                    | Settings                    |
| `remote_quote`      | Remote-Quote                     | Remote Quota                |
| `target_hours`      | Soll-Stunden/Tag                 | Target Hours/Day            |
| `save`              | Speichern                        | Save                        |
| `cancel`            | Abbrechen                        | Cancel                      |
| `delete`            | Loeschen                         | Delete                      |
| `close_month`       | Monat abschliessen               | Close Month                 |
| `clear_all`         | Alles loeschen                   | Clear All                   |
| `entries`           | Eintraege                        | Entries                     |
| `no_entries`        | Noch keine Eintraege             | No entries yet              |
| `history`           | Historie                         | History                     |
| `soll`              | Soll                             | Target                      |
| `max_remote`        | Max Remote                       | Max Remote                  |
| `ist_remote`        | Ist Remote                       | Actual Remote               |
| `remaining`         | Verbleibend                      | Remaining                   |
| `pdf_import`        | PDF Import                       | PDF Import                  |
| `remote_hours`      | Remote-Stunden                   | Remote Hours                |
| `available_months`  | Verfuegbare Monate               | Available Months            |
| `entry_saved`       | Eintrag gespeichert              | Entry saved                 |
| `entry_deleted`     | Eintrag geloescht                | Entry deleted               |
| `month_closed`      | abgeschlossen                    | closed                      |
| `record_time`       | Arbeitszeit erfassen             | Record Work Time            |
| `csv_export`        | CSV Export                       | CSV Export                  |
| `pdf_report`        | PDF Bericht                      | PDF Report                  |
| `backup`            | Backup                           | Backup                      |
| `restore`           | Wiederherstellen                 | Restore                     |
| `export_import`     | Export / Import                  | Export / Import             |

Die Sprache wird ueber `state.language` gesteuert und im TopAppBar per Button umgeschaltet.

### 5.3 Hauptlayout

Die `TrackerApp`-Composable-Funktion definiert das Hauptlayout:

```kotlin
@Composable
fun TrackerApp(repository: TrackerRepository, stateHolder: MutableState<TrackerState>)
```

**Scaffold-Aufbau:**

```
┌──────────────────────────────────────┐
│  TopAppBar                           │
│  "Remote Work Tracker"  [EN] [☽] [⚙] │
├──────────────────────────────────────┤
│  LazyColumn (scrollbar)              │
│  ┌────────────────────────────────┐  │
│  │ Nachricht (falls vorhanden)   │  │
│  ├────────────────────────────────┤  │
│  │ MonthSelectorRow (optional)   │  │
│  ├────────────────────────────────┤  │
│  │ MonthOverviewCard             │  │
│  ├────────────────────────────────┤  │
│  │ RemoteProgressCard            │  │
│  ├────────────────────────────────┤  │
│  │ QuickStatsRow                 │  │
│  ├────────────────────────────────┤  │
│  │ Eintraege-Titel               │  │
│  │ WorkEntryCard (1..n)          │  │
│  ├────────────────────────────────┤  │
│  │ Export/Import Section         │  │
│  ├────────────────────────────────┤  │
│  │ Historie (falls vorhanden)    │  │
│  │ HistoryCard (1..n)            │  │
│  ├────────────────────────────────┤  │
│  │ Aktions-Buttons               │  │
│  │ [Monat abschliessen] [Alles]  │  │
│  └────────────────────────────────┘  │
│                                      │
│                          [PDF] [+]   │ ← FABs
└──────────────────────────────────────┘
```

**TopAppBar-Aktionen:**
1. **Sprach-Toggle:** Wechselt zwischen DE und EN
2. **Dunkelmodus-Toggle:** Wechselt zwischen Sonne/Mond-Symbol
3. **Einstellungen:** Oeffnet `SettingsBottomSheet`

**Floating Action Buttons:**
1. **PDF-Import (violett):** Oeffnet Dokumenten-Picker fuer PDF-Dateien
2. **Eintrag hinzufuegen (blau):** Oeffnet `AddEntryDialog`

### 5.4 Composable-Komponenten

#### MonthOverviewCard

Zeigt den aktuellen Monat, das Quartal und die Quote an.

```kotlin
@Composable
fun MonthOverviewCard(
    data: MonthData,
    month: String,
    year: Int,
    quartal: String,
    lang: String
)
```

**Darstellung:** Indigo-farbene Karte mit grossem Monatsnamen und Quartal/Quote-Info.

#### RemoteProgressCard

Fortschrittsanzeige der Remote-Stunden mit Farbkodierung.

```kotlin
@Composable
fun RemoteProgressCard(data: MonthData, lang: String)
```

**Farbkodierung des Fortschrittsbalkens:**
- **Gruen** (`#10B981`): >= 10h verbleibend
- **Orange/Amber** (`#F59E0B`): < 10h verbleibend
- **Rot** (`#EF4444`): Ueberschritten (verbleibend < 0)

Enthalt einen `LinearProgressIndicator` und eine Karte mit dem Restwert.

#### QuickStatsRow

Drei nebeneinander angeordnete Statistikkarten.

```kotlin
@Composable
fun QuickStatsRow(data: MonthData, lang: String)
```

Zeigt an:
1. **Soll:** Soll-Stunden gesamt (z.B. "184.0h")
2. **Max Remote:** Maximal erlaubte Remote-Stunden (z.B. "73.6h")
3. **Ist Remote:** Tatsaechliche Remote-Stunden (z.B. "32.0h")

#### WorkEntryCard

Darstellung eines einzelnen Arbeitseintrags mit Bearbeitungs-/Loeschfunktion.

```kotlin
@Composable
fun WorkEntryCard(
    entry: WorkEntry,
    lang: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit
)
```

**Aufbau:**
- Datum (fett)
- Zeitbereich (Start - Ende)
- Remote-Stunden und Gesamtstunden
- Pausenwarnung (rot, falls vorhanden)
- Bearbeitungs-Icon (blau) und Loesch-Button (rotes "X")

#### AddEntryDialog

Dialog fuer neue Eintraege und Bearbeitung bestehender Eintraege.

```kotlin
@Composable
fun AddEntryDialog(
    lang: String,
    isEditing: Boolean = false,
    initialDatum: String? = null,
    initialStart: String? = null,
    initialEnd: String? = null,
    initialPause: String? = null,
    initialRemote: String? = null,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Double, Double?) -> Unit
)
```

**Felder:**
- Datum (YYYY-MM-DD), Standard: heutiges Datum
- Startzeit (HH:MM), Standard: 08:00
- Endzeit (HH:MM), Standard: 16:30
- Pause (Minuten), Standard: 30
- Remote-Stunden (leer = alle Stunden sind remote)

Der Titel wechselt zwischen "Arbeitszeit erfassen" und "Eintrag bearbeiten" je nach Modus.

#### SettingsBottomSheet

Modal-Bottom-Sheet fuer Einstellungen.

```kotlin
@Composable
fun SettingsBottomSheet(
    state: TrackerState,
    lang: String,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit
)
```

**Inhalte:**
- **Remote-Quote Slider:** 40% bis 60% in 5%-Schritten (Steps: 3 -> 45%, 50%, 55%)
- **Soll-Stunden/Tag:** Textfeld (Zahleneingabe)
- **Speichern-Button:** Uebernimmt die Werte

#### HistoryCard

Darstellung eines archivierten Monats.

```kotlin
@Composable
fun HistoryCard(month: MonthData)
```

Zeigt: Monatsname, Jahr, Remote-Stunden vs. Maximum, tatsaechliche Quote.

#### MonthSelectorRow

Monatsauswahl bei mehreren verfuegbaren Monaten (FilterChips).

```kotlin
@Composable
fun MonthSelectorRow(
    months: List<Pair<Int, Int>>,
    currentYear: Int,
    currentMonthNum: Int,
    onMonthSelected: (Int, Int) -> Unit,
    lang: String
)
```

Wird nur angezeigt wenn `availableMonths.size > 1`. Zeigt abgekuerzte Monatsnamen
(z.B. "Jan 2026", "Feb 2026") als FilterChips an.

#### PdfImportDialog

Vorschau-Dialog fuer geparste PDF-Eintraege.

```kotlin
@Composable
fun PdfImportDialog(
    result: PdfParseResult,
    lang: String,
    onDismiss: () -> Unit,
    onImport: (List<ParsedWorkEntry>) -> Unit
)
```

**Funktionen:**
- Zeigt Anzahl gefundener Eintraege
- Scrollbare Liste mit Datum, Zeitbereich und Remote-Kennzeichnung
- Remote-Eintraege farblich hervorgehoben (indigo)
- Fehlermeldungen in Amber-Farbe
- "Alle importieren"-Button importiert alle Eintraege auf einmal

### 5.5 Event-Handling

#### Share Intents

Fuer CSV, PDF und Backup wird das Android Share-System verwendet:

```kotlin
// CSV-Export
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/csv"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(intent, t("csv_export", lang)))
```

Dateien werden im Cache-Verzeichnis erstellt und ueber `FileProvider` sicher geteilt.

#### ActivityResultLauncher

Zwei Launcher fuer Dateiauswahl:

1. **PDF-Import Launcher:** Oeffnet Dokumenten-Picker fuer `application/pdf`
2. **Restore Launcher:** Oeffnet Dokumenten-Picker fuer `application/json`

```kotlin
val pdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    uri?.let {
        val result = pdfParser.parsePdf(it)
        // ...
    }
}

val restoreLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    uri?.let {
        val jsonString = context.contentResolver.openInputStream(it)
            ?.bufferedReader()?.readText() ?: ""
        val restoredState = repository.deserializeState(jsonString)
        // ...
    }
}
```

#### State-Observation und Recomposition

Der State wird als `MutableState<TrackerState>` in der `MainActivity` gehalten:

```kotlin
val state = remember { mutableStateOf(repository.loadState()) }
```

Jede Zustandsaenderung (`state = newState`) loest automatisch eine Compose-Rekomposition
aller betroffenen Composables aus. Zusaetzliche `LaunchedEffect`-Bloecke:

- **Auto-Monatswechsel:** Bei Aenderung von `dailyRecords` wird automatisch zum
  neuesten Monat gewechselt (z.B. nach PDF-Import).
- **Nachrichten-Timeout:** Statusnachrichten verschwinden nach 3 Sekunden automatisch.

---

## 6. PDF-Import (PdfParser.kt)

### 6.1 Uebersicht

Der `PdfParser` extrahiert Arbeitszeiteintraege aus SAP-Zeitnachweis-PDFs.
Er unterstuetzt sowohl englische als auch deutsche SAP-Formate.

### 6.2 Datenklassen

```kotlin
data class ParsedWorkEntry(
    val datum: String,          // "YYYY-MM-DD"
    val startTime: String,      // "HH:MM"
    val endTime: String,        // "HH:MM"
    val pauseMinutes: Double,   // Standard: 30.0
    val remoteHours: Double?,   // null (wird spaeter berechnet)
    val isRemote: Boolean = false  // Remote-Work-Erkennung
)

data class PdfParseResult(
    val entries: List<ParsedWorkEntry>,  // Extrahierte Eintraege
    val errors: List<String>             // Fehlermeldungen
)
```

### 6.3 Parsing-Strategien

#### Strategie 1: SAP TimeStatement Format

Primaere Strategie fuer SAP-Zeitnachweise.

**Schritt 1 -- Datumsbereich erkennen:**
```
Englisch: "from DD.MM.YYYY to DD.MM.YYYY"
Deutsch:  "vom DD.MM.YYYY bis DD.MM.YYYY"
```

**Schritt 2 -- Tageseintraege erkennen:**
```
Regex: ^(\d{2})\s+(MO|TU|WE|TH|FR|SA|SU|DI|MI|DO|SO)
```

**Schritt 3 -- Zeiteintraege extrahieren:**
```
Regex: (\d{2}):(\d{2})\s+(\d{2}):(\d{2})\s+(\d+[,\.]\d+)
```

**Schritt 4 -- Remote-Work erkennen:**
```kotlin
private fun isRemoteWorkLine(line: String): Boolean {
    val remoteKeywords = listOf(
        "remote work", "remote", "telearbeit",
        "home office", "homeoffice"
    )
    return remoteKeywords.any { line.lowercase().contains(it) }
}
```

#### Strategie 2: Generisches Parsing

Fallback fuer nicht-SAP-PDFs. Unterstuetzt fuenf verschiedene Datumsformate:

| Muster   | Format                          | Beispiel                    |
|----------|---------------------------------|-----------------------------|
| Pattern 1| `DD.MM.YYYY HH:MM HH:MM`       | `15.03.2026 08:00 16:30`   |
| Pattern 2| `YYYY-MM-DD HH:MM HH:MM`       | `2026-03-15 08:00 16:30`   |
| Pattern 3| `DD.MM.YYYY HH:MM HH:MM mm`    | `15.03.2026 08:00 16:30 30`|
| Pattern 4| `Mo DD.MM. HH:MM HH:MM`        | `Mo 15.03. 08:00 16:30`    |
| Pattern 5| `DD.MM.YYYY...HH:MM-HH:MM`     | `15.03.2026 08:00-16:30`   |

### 6.4 PDF-Verarbeitung

```kotlin
fun parsePdf(uri: Uri): PdfParseResult {
    val inputStream = context.contentResolver.openInputStream(uri)
    inputStream?.use { stream ->
        val document = PDDocument.load(stream)
        document.use { doc ->
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)

            // Erst SAP-Format versuchen, dann generisches Parsing
            val sapEntries = parseSapTimeStatement(text)
            if (sapEntries.isNotEmpty()) {
                entries.addAll(sapEntries)
            } else {
                // Fallback: Zeilenweises generisches Parsing
                for (line in text.split("\n")) {
                    parseWorkLine(line, null, null)?.let { entries.add(it) }
                }
            }
        }
    }
}
```

Die Bibliothek `pdfbox-android` wird beim Erstellen des Parsers initialisiert:
```kotlin
init {
    PDFBoxResourceLoader.init(context)
}
```

---

## 7. PDF-Berichtgenerierung (ReportGenerator.kt)

### 7.1 Uebersicht

Generiert einen A4-PDF-Monatsbericht mit der Android `PdfDocument`-API (kein pdfbox).

```kotlin
object ReportGenerator {
    fun generateMonthlyPdf(
        context: Context,
        monthData: MonthData,
        entries: List<WorkEntry>
    ): File
}
```

### 7.2 Seitenformat

- **Format:** A4 (595 x 842 Punkte, ca. 210 x 297 mm)
- **Raender:** 40pt links, 40pt oben
- **Schriftgroessen:** Titel 20pt, Header 12pt, Text 11pt

### 7.3 Aufbau des Berichts

```
┌─────────────────────────────────────────────────────┐
│  Monatsbericht {Monat} {Jahr}       (20pt, Bold)    │
│                                                     │
│  Soll-Stunden: 184.0h                               │
│  Ist Arbeitszeit: 160.0h                             │
│  Ist Remote: 64.0h                                   │
│  Max Remote: 73.6h                                   │
│  Quote: 40.0%                                        │
│  ──────────────────────────────────────────────────  │
│  Datum      Start  Ende   Pause   Remote   Gesamt   │
│  ──────────────────────────────────────────────────  │
│  2026-03-03 08:00  16:30  30 min  8.0h     8.0h     │
│  2026-03-04 09:00  17:30  30 min  8.0h     8.0h     │
│  ...                                                 │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 7.4 Automatische Paginierung

Bei mehr als ca. 47 Eintraegen wird automatisch eine neue Seite erstellt:

```kotlin
if (yPos > 790f) {
    document.finishPage(page)
    val newPageInfo = PdfDocument.PageInfo.Builder(
        595, 842, document.pages.size + 1
    ).create()
    page = document.startPage(newPageInfo)
    canvas = page.canvas
    yPos = 50f
}
```

### 7.5 Ausgabe

Die PDF-Datei wird im Cache-Verzeichnis der App gespeichert:

```kotlin
val file = File(
    context.cacheDir,
    "Monatsbericht_${monthData.monat}_${monthData.jahr}.pdf"
)
file.outputStream().use { document.writeTo(it) }
```

Der Dateiname folgt dem Schema `Monatsbericht_Maerz_2026.pdf`. Die Datei wird
ueber `FileProvider` und einen Share-Intent an andere Apps weitergegeben.

---

## 8. Build und Deployment

### 8.1 Voraussetzungen

- **JDK:** 17 (Gradle und Android Gradle Plugin 8.2 benoetigen JDK 17; der Bytecode-Zielwert der App ist 1.8)
- **Android SDK:** Compile SDK 34 (Android 14), `local.properties` mit `sdk.dir=...` oder `ANDROID_HOME`
- **Kotlin:** 1.9.21
- **Gradle:** Wrapper 8.5 ist enthalten (`./gradlew`, unter Windows `gradlew.bat`)
- **iOS:** zusaetzlich macOS mit Xcode 15 oder neuer (siehe 8.6)

### 8.2 Lokaler Build

**Debug-APK erstellen:**
```bash
cd RemoteWorkTrackerKMP
./gradlew :androidApp:assembleDebug
```

Die APK wird erstellt unter:
```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

**Direkte Installation auf verbundenem Geraet:**
```bash
./gradlew :androidApp:installDebug
```

**Shared-Modul einzeln bauen:**
```bash
./gradlew :shared:build
```

**Projekt aufraumen:**
```bash
./gradlew clean
```

### 8.3 Release Build

**APK-Signierung:**

In `androidApp/build.gradle.kts` ist derzeit `isMinifyEnabled = false` gesetzt.
Fuer einen Release-Build wird Folgendes empfohlen:

```kotlin
// androidApp/build.gradle.kts
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
```

**ProGuard/R8 Konfiguration:**

Fuer kotlinx.serialization muessen folgende Regeln beachtet werden:
```proguard
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.remotework.tracker.** {
    *** Companion;
}
-keep class com.remotework.tracker.**$$serializer { *; }
```

### 8.4 Abhaengigkeiten

**Root-Plugins (build.gradle.kts):**

| Plugin                       | Version |
|------------------------------|---------|
| com.android.application      | 8.2.0   |
| com.android.library          | 8.2.0   |
| kotlin("android")            | 1.9.21  |
| kotlin("multiplatform")      | 1.9.21  |
| kotlin("plugin.serialization")| 1.9.21 |

**Shared-Modul (commonMain):**

| Abhaengigkeit                             | Version |
|-------------------------------------------|---------|
| kotlinx-serialization-json                | 1.6.2   |
| kotlinx-datetime                          | 0.5.0   |

**Android-App:**

| Abhaengigkeit                             | Version    |
|-------------------------------------------|------------|
| Compose BOM                               | 2023.10.01 |
| androidx.compose.ui:ui                    | BOM        |
| androidx.compose.material3:material3      | BOM        |
| androidx.activity:activity-compose        | 1.8.2      |
| androidx.lifecycle:lifecycle-runtime-compose| 2.7.0     |
| androidx.lifecycle:lifecycle-viewmodel-compose| 2.7.0   |
| androidx.core:core-ktx                    | 1.12.0     |
| androidx.appcompat:appcompat              | 1.6.1      |
| com.google.android.material:material      | 1.11.0     |
| com.tom-roush:pdfbox-android              | 2.0.27.0   |

### 8.5 CI/CD

Das Projekt enthaelt zwei Cloud-Builds:

- **GitHub Actions** (`.github/workflows/ios-build.yml`, Workflow "iOS Build"): baut bei jedem Push auf
  `main`, bei Pull Requests und manuell (Actions -> iOS Build -> Run workflow) die iOS-Simulator-App
  auf einem macOS-Runner und die Android-Debug-APK auf Ubuntu. Ergebnisse als Artefakte
  `ios-simulator-app` und `android-debug-apk`.
- **Codemagic** (`codemagic.yaml`): `ios-workflow` (iOS ohne Signierung, Simulator/IPA) und
  `android-workflow` (Release- und Debug-APK; benoetigt den Keystore `remote_work_keystore` und die
  Gruppe `google_play` in Codemagic, die Platzhalter-E-Mail-Adresse muss ersetzt werden).

### 8.6 Build pro Plattform (Uebersicht)

| Ziel | Gebaut auf | Befehl | Ergebnis |
|---|---|---|---|
| Android Debug-APK | Linux, macOS, Windows (JDK 17, Android SDK 34) | `./gradlew :androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| Android Release-APK / AAB | wie oben, eigener Signierschluessel | `./gradlew :androidApp:assembleRelease` bzw. `:androidApp:bundleRelease` | `androidApp/build/outputs/apk/release/` (unsigniert, bis eine Signierung in `androidApp/build.gradle.kts` eingetragen ist) |
| iOS Simulator-App | nur macOS (Xcode 15+) | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`, dann `xcodebuild` | `iosApp/build/simulator/.../iosApp.app` |
| iOS ohne Mac | GitHub Actions oder Codemagic | Workflow "iOS Build" bzw. `ios-workflow` | Artefakt `ios-simulator-app` / IPA |
| iOS Geraet / App Store | macOS oder Codemagic, Apple-Developer-Konto | Signierung in Xcode oder Codemagic | signierte IPA |

iOS laesst sich auf Linux und Windows nicht bauen; dort ueberspringt Gradle die iOS-Ziele, Android-Build
und Unit-Tests funktionieren trotzdem.

**iOS auf dem Mac, Schritt fuer Schritt:**
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64     # Intel-Mac: :shared:linkDebugFrameworkIosX64
cd iosApp
xcodebuild build -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -derivedDataPath build/simulator \
  CODE_SIGN_IDENTITY="-" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```
Alternativ `iosApp/iosApp.xcodeproj` in Xcode oeffnen und Run druecken.

**Tests:**
```bash
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest      # JVM, ohne Emulator
./gradlew :androidApp:connectedDebugAndroidTest                         # braucht Emulator/Geraet
```
Stand: 119 Unit-Tests im Modul `shared`, 33 in `androidApp`. Die iOS-Oberflaeche hat keine eigenen
Tests, ihre Logik ist das getestete Modul `shared`.

### 8.7 Projektmodule (settings.gradle.kts)

```kotlin
rootProject.name = "RemoteWorkTrackerKMP"
include(":androidApp")
include(":shared")
```

Repositories: Google, Gradle Plugin Portal, Maven Central.

---

## 9. Datenpersistenz

### 9.1 Speichermechanismus

Die App verwendet Android `SharedPreferences` fuer die lokale Datenspeicherung.
Der gesamte Anwendungszustand wird als ein einziger JSON-String gespeichert.

**SharedPreferences-Name:** `"remote_work_tracker"`
**Schluessel:** `"tracker_state"`

```kotlin
actual class LocalStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("remote_work_tracker", Context.MODE_PRIVATE)
    // ...
}
```

### 9.2 Datenformat

Der gesamte `TrackerState` wird als Pretty-Print-JSON serialisiert:

```json
{
    "aktuellerMonat": "Maerz",
    "aktuellesJahr": 2026,
    "currentMonthData": {
        "monat": "Maerz",
        "jahr": 2026,
        "individuelleQuote": 40.0,
        "sollStundenGesamt": 184.0,
        "maxRemoteStunden": 73.6,
        "istRemoteStundenLaufend": 32.0,
        "istArbeitszeitLaufend": 64.0,
        "tatsaechlicheRemoteQuote": 17.39
    },
    "currentQuartal": "Q1/2026",
    "dailyRecords": {
        "2026-03-03": [
            {
                "id": "2026-03-03|08:00|16:30|1709456400000",
                "datum": "2026-03-03",
                "startTime": "08:00",
                "endTime": "16:30",
                "pauseMinutes": 30.0,
                "remoteStundenHeute": 8.0,
                "totalStundenHeute": 8.0,
                "pauseWarning": null
            }
        ]
    },
    "monatlicheHistorie": [],
    "individuelleQuoteProzent": 40.0,
    "sollStundenProTag": 8.0,
    "language": "de",
    "darkMode": false
}
```

### 9.3 Fehlerbehandlung

- **Korrupte Daten:** Bei Deserialisierungsfehlern wird automatisch ein neuer
  Standardzustand erzeugt (`createDefaultState()`). Daten gehen in diesem Fall verloren.
- **Unbekannte Felder:** Durch `ignoreUnknownKeys = true` werden neue Felder in
  zukuenftigen Versionen ignoriert (Vorwaertskompatibilitaet).
- **Backup/Restore:** Der Benutzer kann jederzeit ein JSON-Backup erstellen und
  wiederherstellen, um Datenverlust zu vermeiden.

### 9.4 Backup und Restore

**Backup erstellen:**
1. `serializeState(state)` erzeugt JSON-String
2. Datei wird im Cache-Verzeichnis gespeichert: `Backup_{Monat}_{Jahr}.json`
3. Share-Intent mit MIME-Type `application/json`

**Backup wiederherstellen:**
1. `ActivityResultContracts.OpenDocument()` oeffnet Dateiauswahl fuer JSON
2. Dateiinhalt wird gelesen
3. `deserializeState(jsonString)` parst den JSON-String
4. Bei Erfolg: State wird ueberschrieben und gespeichert
5. Bei Fehler: Fehlermeldung wird angezeigt

---

## 10. ArbZG-Konformitaet

Die App implementiert wesentliche Regeln des deutschen Arbeitszeitgesetzes (ArbZG)
und der bayerischen Feiertagsregelung.

### 10.1 Gesetzliche Pausenregelung (§ 4 ArbZG)

Die Pausenpruefung erfolgt automatisch bei jeder Arbeitszeiterfassung:

```kotlin
val statutoryPause = when {
    grossHours >= 9 -> 0.75    // 45 Minuten bei >= 9h Arbeitszeit
    grossHours >= 6 -> 0.5     // 30 Minuten bei >= 6h Arbeitszeit
    else -> 0.0                // Keine Mindestpause bei < 6h
}
```

| Bruttoarbeitszeit   | Mindestpause (ArbZG)  | App-Pruefung |
|---------------------|-----------------------|--------------|
| < 6 Stunden         | keine Pflicht         | keine        |
| 6 bis < 9 Stunden   | 30 Minuten            | Warnung      |
| >= 9 Stunden        | 45 Minuten            | Warnung      |

Bei Unterschreitung wird ein `pauseWarning` im `WorkEntry` gesetzt und in der UI
rot angezeigt. Die Erfassung wird jedoch **nicht blockiert** -- es handelt sich um
eine Warnung, keine Sperre.

### 10.2 Feiertagskalender Bayern 2025-2030

Bayerische gesetzliche Feiertage (13 pro Jahr):

| Feiertag                     | 2025       | 2026       |
|------------------------------|------------|------------|
| Neujahr                      | 01.01.     | 01.01.     |
| Heilige Drei Koenige         | 06.01.     | 06.01.     |
| Karfreitag                   | 18.04.     | 03.04.     |
| Ostermontag                  | 21.04.     | 06.04.     |
| Tag der Arbeit               | 01.05.     | 01.05.     |
| Christi Himmelfahrt           | 29.05.     | 14.05.     |
| Pfingstmontag                | 09.06.     | 25.05.     |
| Fronleichnam                 | 19.06.     | 04.06.     |
| Mariae Himmelfahrt            | 15.08.     | 15.08.     |
| Tag der Deutschen Einheit     | 03.10.     | 03.10.     |
| Allerheiligen                 | 01.11.     | 01.11.     |
| 1. Weihnachtsfeiertag        | 25.12.     | 25.12.     |
| 2. Weihnachtsfeiertag        | 26.12.     | 26.12.     |

Arbeitszeiterfassung an Feiertagen wird vollstaendig blockiert.

### 10.3 Wochenend-Validierung

- **Sonntag:** Erfassung wird blockiert mit Fehlermeldung
- **Samstag:** Erfassung wird blockiert mit Hinweis auf genehmigte Mehrarbeit
- **Feiertag:** Erfassung wird blockiert

### 10.4 Quotenmanagement

Die Remote-Arbeits-Quote wird auf mehreren Ebenen verwaltet:

- **Individuelle Quote:** Einstellbar von 40% bis 60% (Standard: 40%)
- **Monatsebene:** `maxRemoteStunden = sollStundenGesamt * (quote / 100)`
- **Quartalsebene:** Automatische Anpassung am Quartalsende
  - Ueber Quote -> Reduktion um 10% (Minimum 40%)
  - Unter Quote -> Erhoehung um 10% (Maximum 60%)

---

## 11. Sicherheit und Datenschutz

### 11.1 Datenspeicherung

- **Ausschliesslich lokale Speicherung:** Alle Daten verbleiben auf dem Geraet
- **Keine Cloud-Synchronisierung:** Kein Backend-Server, keine API-Aufrufe
- **Keine Netzwerkkommunikation:** Die App benoetigt keine Internetberechtigung
- **SharedPreferences:** Daten im privaten App-Verzeichnis (`MODE_PRIVATE`)

### 11.2 Benutzerauthentifizierung

Die App implementiert keine eigene Benutzerauthentifizierung. Der Zugang wird
ausschliesslich ueber die Geraetesicherheit (Bildschirmsperre, Biometrie) gesteuert.

### 11.3 Dateifreigabe

Fuer den sicheren Austausch von Dateien (CSV, PDF, Backup-JSON) wird der Android
`FileProvider` verwendet:

```kotlin
val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    file
)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"  // oder text/csv, application/json
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

Der `FileProvider` stellt sicher, dass:
- Dateien nur ueber Content-URIs geteilt werden (kein direkter Dateizugriff)
- Leseberechtigungen temporaer und gezielt vergeben werden
- Keine `file://`-URIs verwendet werden (Android 7.0+ Kompatibilitaet)

### 11.4 PDF-Verarbeitung

Importierte PDFs werden nur im Arbeitsspeicher verarbeitet (`InputStream`).
Es werden keine Kopien der PDF-Dateien gespeichert. Nur die extrahierten
Arbeitszeitdaten (Datum, Zeiten, Pause) werden in den App-Zustand uebernommen.

### 11.5 Exportierte Dateien

CSV-, PDF- und Backup-Dateien werden im Cache-Verzeichnis der App gespeichert
(`context.cacheDir`). Diese Dateien:
- Werden vom Betriebssystem bei Speichermangel automatisch geloescht
- Sind nur fuer die App selbst und den Share-Intent-Empfaenger zugaenglich
- Enthalten keine sensitiven Daten ueber die erfassten Arbeitszeiten hinaus

---

Zuletzt aktualisiert: Maerz 2026
