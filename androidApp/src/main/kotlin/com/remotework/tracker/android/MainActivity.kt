package com.remotework.tracker.android

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.remotework.tracker.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Feature 5: Translations
internal val translations = mapOf(
    "de" to mapOf(
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
        "final_report" to "Abschlussbericht",
        "close" to "Schließen",
        "report_empty" to "Noch keine abgeschlossenen Monate vorhanden.",
        "report_overall" to "Gesamt",
        "report_quarter_incomplete" to "unvollständig",
        "report_months" to "Monate",
        "report_months_over" to "Monate über Limit",
        "report_limit" to "Limit",
        "report_quota" to "Quote",
        "rating_good" to "Gut: Limit eingehalten",
        "rating_tight" to "Knapp eingehalten (über 90 % des Limits)",
        "rating_exceeded" to "Überschritten",
        "report_rule" to "Bewertung je Quartal: bis 90 % des Remote-Limits gut, bis 100 % knapp, darüber überschritten.",
        "soll" to "Soll",
        "max_remote" to "Max Remote",
        "flex_balance" to "GLZ-Saldo (Nachweis)",
        "ist_remote" to "Ist Remote",
        "remaining" to "Verbleibend",
        "confirm_delete" to "Wirklich löschen?",
        "confirm_close" to "Monat wirklich abschließen?",
        "pdf_import" to "PDF importieren",
        "remote_hours" to "Remote-Stunden",
        "available_months" to "Verfügbare Monate",
        "entry_saved" to "Eintrag gespeichert",
        "entry_deleted" to "Eintrag gelöscht",
        "all_deleted" to "Alles gelöscht",
        "clear_confirm" to "Wirklich alles löschen? Historie, Einträge und Einstellungen werden zurückgesetzt.",
        "month_closed" to "abgeschlossen",
        "month_empty" to "enthält keine Einträge und wurde nicht in die Historie aufgenommen",
        "date_label" to "Datum (YYYY-MM-DD)",
        "start_label" to "Start (HH:MM)",
        "end_label" to "Ende (HH:MM)",
        "pause_label" to "Pause (Minuten)",
        "remote_label" to "Remote Stunden (leer = alles)",
        "import_all" to "Alle importieren",
        "entries_found" to "Einträge gefunden:",
        "notes" to "Hinweise:",
        "entries_imported" to "Einträge importiert",
        "remote_count" to "Remote",
        "errors" to "Fehler",
        "no_entries_found" to "Keine Einträge gefunden",
        "record_time" to "Arbeitszeit erfassen",
        "quote_label" to "Quote",
        "csv_export" to "CSV exportieren",
        "pdf_report" to "PDF exportieren",
        "backup" to "Backup erstellen",
        "restore" to "Wiederherstellen",
        "backup_success" to "Backup erstellt",
        "restore_success" to "Daten wiederhergestellt",
        "restore_error" to "Fehler beim Wiederherstellen",
        "export_import" to "Export / Import",
        "net_hours" to "Netto",
        "invalid_number" to "Ungültige Zahl",
        "export_error" to "Export fehlgeschlagen",
        "restore_confirm" to "Alle aktuellen Daten durch das Backup ersetzen?",
        "confirm_yes" to "Ja",
        "language" to "Sprache wechseln",
        "dark_mode" to "Dunkelmodus",
        "empty_hint" to "Erfasse deinen ersten Arbeitstag, um die Remote-Quote zu verfolgen.",
        "first_entry" to "Ersten Eintrag erfassen",
        "danger_zone" to "Gefahrenzone",
        "invalid_date" to "Ungültiges Datum (JJJJ-MM-TT)",
        "invalid_time" to "Ungültige Zeitangabe!",
        "progress_desc" to "%s von %s Stunden",
        "used_percent" to "%s %% genutzt",
        "file_too_big" to "Datei zu groß oder nicht lesbar",
        "read_error" to "Lesefehler",
        "edit_entry_for" to "Eintrag %s bearbeiten",
        "delete_entry_for" to "Eintrag %s löschen",
        "report_month_one" to "Monat",
        "report_month_over_one" to "Monat über Limit",
        "report_quarter_one" to "Quartal",
        "report_quarters" to "Quartale",
        "report_remote" to "Remote",
        "pause_warning" to "WARNUNG: Pause (%sh) < gesetzl. Minimum (%sh)!",
        "switch_on" to "an",
        "switch_off" to "aus"
    ),
    "en" to mapOf(
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
        "final_report" to "Final Report",
        "close" to "Close",
        "report_empty" to "No closed months yet.",
        "report_overall" to "Total",
        "report_quarter_incomplete" to "incomplete",
        "report_months" to "months",
        "report_months_over" to "months over limit",
        "report_limit" to "Limit",
        "report_quota" to "Quota",
        "rating_good" to "Good: within the limit",
        "rating_tight" to "Tight (above 90 % of the limit)",
        "rating_exceeded" to "Exceeded",
        "report_rule" to "Rating per quarter: up to 90 % of the remote limit good, up to 100 % tight, above that exceeded.",
        "soll" to "Target",
        "max_remote" to "Max Remote",
        "flex_balance" to "Flextime balance (statement)",
        "ist_remote" to "Actual Remote",
        "remaining" to "Remaining",
        "confirm_delete" to "Really delete?",
        "confirm_close" to "Really close this month?",
        "pdf_import" to "Import PDF",
        "remote_hours" to "Remote Hours",
        "available_months" to "Available Months",
        "entry_saved" to "Entry saved",
        "entry_deleted" to "Entry deleted",
        "all_deleted" to "All deleted",
        "clear_confirm" to "Really delete everything? History, entries and settings will be reset.",
        "month_closed" to "closed",
        "month_empty" to "has no entries and was not added to the history",
        "date_label" to "Date (YYYY-MM-DD)",
        "start_label" to "Start (HH:MM)",
        "end_label" to "End (HH:MM)",
        "pause_label" to "Pause (Minutes)",
        "remote_label" to "Remote Hours (empty = all)",
        "import_all" to "Import All",
        "entries_found" to "Entries found:",
        "notes" to "Notes:",
        "entries_imported" to "Entries imported",
        "remote_count" to "Remote",
        "errors" to "Errors",
        "no_entries_found" to "No entries found",
        "record_time" to "Record Work Time",
        "quote_label" to "Quota",
        "csv_export" to "Export CSV",
        "pdf_report" to "Export PDF",
        "backup" to "Create backup",
        "restore" to "Restore",
        "backup_success" to "Backup created",
        "restore_success" to "Data restored",
        "restore_error" to "Error restoring data",
        "export_import" to "Export / Import",
        "net_hours" to "Net",
        "invalid_number" to "Invalid number",
        "export_error" to "Export failed",
        "restore_confirm" to "Replace all current data with the backup?",
        "confirm_yes" to "Yes",
        "language" to "Switch language",
        "dark_mode" to "Dark mode",
        "empty_hint" to "Record your first work day to track your remote quota.",
        "first_entry" to "Record first entry",
        "danger_zone" to "Danger zone",
        "invalid_date" to "Invalid date (YYYY-MM-DD)",
        "invalid_time" to "Invalid time!",
        "progress_desc" to "%s of %s hours",
        "used_percent" to "%s %% used",
        "file_too_big" to "File too large or not readable",
        "read_error" to "Read error",
        "edit_entry_for" to "Edit entry %s",
        "delete_entry_for" to "Delete entry %s",
        "report_month_one" to "month",
        "report_month_over_one" to "month over limit",
        "report_quarter_one" to "quarter",
        "report_quarters" to "quarters",
        "report_remote" to "Remote",
        "pause_warning" to "WARNING: break (%sh) shorter than statutory minimum (%sh)!",
        "switch_on" to "on",
        "switch_off" to "off"
    )
)

internal fun t(key: String, lang: String): String = translations[lang]?.get(key) ?: key

/** Content column width on wide screens (landscape, tablets). */
private val MaxContentWidth = 640.dp

class MainActivity : ComponentActivity() {

    private lateinit var repository: TrackerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val storage = LocalStorage(applicationContext)
        repository = TrackerRepository(storage)

        setContent {
            val state = remember { mutableStateOf(repository.loadState()) }
            val dark = state.value.darkMode
            // The app bar is the brand colour (dark indigo in light mode, light indigo in dark mode):
            // status bar icons must contrast with it; the navigation bar is transparent over the page.
            DisposableEffect(dark) {
                val transparent = AndroidColor.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                )
                onDispose { }
            }
            RemoteWorkTrackerTheme(darkMode = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TrackerApp(repository, state)
                }
            }
        }
    }
}

@Composable
private fun largeFont(): Boolean = LocalDensity.current.fontScale > 1.15f

private val ButtonMinHeight = 48.dp

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.semantics { heading() }
    )
}

/** Toggle with Switch role and a spoken state (real DarkMode icons are not in the bundled icon set). */
@Composable
private fun TopBarToggle(checked: Boolean, label: String, lang: String, onChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) t("switch_on", lang) else t("switch_off", lang)
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerApp(repository: TrackerRepository, stateHolder: MutableState<TrackerState>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var state by stateHolder
    val lang = state.language
    // dialog and edit state survives rotation / process death
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showFinalReport by rememberSaveable { mutableStateOf(false) }
    var showCloseConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var editingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editDatum by rememberSaveable { mutableStateOf("") }
    var editStart by rememberSaveable { mutableStateOf("") }
    var editEnd by rememberSaveable { mutableStateOf("") }
    var editPause by rememberSaveable { mutableStateOf("") }
    var editRemote by rememberSaveable { mutableStateOf("") }
    // not saveable (large objects), recreated on demand
    var pendingRestore by remember { mutableStateOf<TrackerState?>(null) }
    var showPdfResultDialog by remember { mutableStateOf(false) }
    var pdfParseResult by remember { mutableStateOf<PdfParseResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val pdfParser = remember { PdfParser(context.applicationContext) }

    // Get available months from entries
    val availableMonths = remember(state.dailyRecords) {
        repository.getMonthsFromEntries(state)
    }
    val currentMonthNum = MonthNames.getMonthNumber(state.aktuellerMonat)
    val currentMonthEntries = remember(state.dailyRecords, state.aktuellesJahr, currentMonthNum) {
        repository.getEntriesForMonth(state, state.aktuellesJahr, currentMonthNum).sortedByDescending { it.datum }
    }

    fun switchToMonthOf(datum: String, from: TrackerState): TrackerState {
        val year = datum.substring(0, 4).toIntOrNull() ?: return from
        val month = datum.substring(5, 7).toIntOrNull() ?: return from
        return repository.switchToMonth(from, year, month)
    }

    fun openNewEntryDialog() {
        editingEntryId = null
        showAddDialog = true
    }

    // File work happens off the main thread; failures are reported instead of crashing
    fun shareFile(buildFile: () -> java.io.File, mime: String, chooserTitle: String, successMessage: String? = null) {
        scope.launch {
            busy = true
            try {
                val file = withContext(Dispatchers.IO) { buildFile() }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, chooserTitle))
                if (successMessage != null) message = successMessage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = t("export_error", lang)
            } finally {
                busy = false
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) { pdfParser.parsePdf(uri) }
                busy = false
                pdfParseResult = result
                if (result.entries.isNotEmpty()) {
                    showPdfResultDialog = true
                } else {
                    message = result.errors.firstOrNull() ?: t("no_entries_found", lang)
                }
            }
        }
    }

    // Restore launcher: read + validate off the main thread, then ask before replacing the data
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) {
                    try {
                        val json = context.contentResolver.openInputStream(uri)?.use { readTextLimited(it, 5_000_000) }
                        if (json == null) OperationResult(error = t("file_too_big", lang))
                        else repository.restoreState(json)
                    } catch (e: Exception) {
                        OperationResult(error = e.message ?: t("read_error", lang))
                    }
                }
                busy = false
                val restored = result.state
                if (restored != null) {
                    pendingRestore = restored
                } else {
                    message = "${t("restore_error", lang)}${result.error?.let { ": $it" } ?: ""}"
                }
            }
        }
    }

    LaunchedEffect(message) {
        val m = message
        if (m != null) {
            snackbarHostState.showSnackbar(m)
            message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Remote Work Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Language toggle
                    IconButton(
                        modifier = Modifier.semantics {
                            contentDescription = t("language", lang)
                            stateDescription = if (state.language == "de") "Deutsch" else "English"
                        },
                        onClick = {
                            val newLang = if (state.language == "de") "en" else "de"
                            state = state.copy(language = newLang)
                            repository.saveState(state)
                        }
                    ) {
                        Text(
                            if (state.language == "de") "EN" else "DE",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Dark mode toggle
                    TopBarToggle(
                        checked = state.darkMode,
                        label = t("dark_mode", lang),
                        lang = lang,
                        onChange = {
                            state = state.copy(darkMode = it)
                            repository.saveState(state)
                        }
                    ) {
                        Text(
                            if (state.darkMode) "☀" else "☽",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    // Settings
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = t("settings", lang))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openNewEntryDialog() },
                modifier = Modifier.semantics { contentDescription = t("add_entry", lang) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(t("add_entry", lang)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (busy) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            // Month Selector (if multiple months available)
            if (availableMonths.size > 1) {
                item {
                    MonthSelectorRow(
                        months = availableMonths,
                        currentYear = state.aktuellesJahr,
                        currentMonthNum = MonthNames.getMonthNumber(state.aktuellerMonat),
                        onMonthSelected = { year, monthNum ->
                            state = repository.switchToMonth(state, year, monthNum)
                            repository.saveState(state)
                        },
                        lang = lang
                    )
                }
            }

            // Month Overview
            item {
                MonthOverviewCard(state.currentMonthData, state.aktuellerMonat, state.aktuellesJahr, state.currentQuartal, lang)
            }

            // Remote Hours Progress
            item {
                RemoteProgressCard(state.currentMonthData, lang)
            }

            // Quick Stats
            item {
                QuickStatsRow(state.currentMonthData, lang)
                FlexBalanceLine(state.gleitzeitSaldo, lang)
            }

            // Current Month Entries
            item {
                SectionTitle(
                    "${t("entries", lang)} (${state.aktuellerMonat} ${state.aktuellesJahr})",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (currentMonthEntries.isEmpty()) {
                item {
                    EmptyState(lang, onAdd = { openNewEntryDialog() })
                }
            } else {
                items(currentMonthEntries, key = { it.id }) { entry ->
                    WorkEntryCard(
                        entry = entry,
                        lang = lang,
                        onDelete = { pendingDeleteId = entry.id },
                        onEdit = {
                            editingEntryId = entry.id
                            editDatum = entry.datum
                            editStart = entry.startTime
                            editEnd = entry.endTime
                            editPause = minutesText(entry.pauseMinutes)
                            editRemote = if (entry.remoteStundenHeute == entry.totalStundenHeute) "" else entry.remoteStundenHeute.toString()
                            showAddDialog = true
                        }
                    )
                }
            }

            // Primary action of the month
            item {
                Button(
                    onClick = { showCloseConfirm = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight),
                    enabled = currentMonthEntries.isNotEmpty()
                ) {
                    Text(t("close_month", lang))
                }
            }

            // History
            if (state.monatlicheHistorie.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle(t("history", lang), modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { showFinalReport = true },
                            modifier = Modifier.heightIn(min = ButtonMinHeight)
                        ) {
                            Text(t("final_report", lang), maxLines = 1)
                        }
                    }
                }
                items(FinalReport.sortHistory(state.monatlicheHistorie, newestFirst = true)) { month ->
                    HistoryCard(month, lang)
                }
            }

            // Export / Import Section (secondary, at the bottom)
            item {
                ExportImportCard(
                    lang = lang,
                    onCsv = {
                        val snapshot = state
                        shareFile(
                            buildFile = {
                                val monthNum = MonthNames.getMonthNumber(snapshot.aktuellerMonat)
                                val csv = repository.generateCsv(snapshot, snapshot.aktuellesJahr, monthNum)
                                newExportFile(context, "Export_${snapshot.aktuellerMonat}_${snapshot.aktuellesJahr}.csv")
                                    .also { it.writeText(csv) }
                            },
                            mime = "text/csv",
                            chooserTitle = t("csv_export", lang)
                        )
                    },
                    onPdfReport = {
                        val snapshot = state
                        shareFile(
                            buildFile = {
                                val monthNum = MonthNames.getMonthNumber(snapshot.aktuellerMonat)
                                val entries = repository.getEntriesForMonth(snapshot, snapshot.aktuellesJahr, monthNum)
                                ReportGenerator.generateMonthlyPdf(context, snapshot.currentMonthData, entries)
                            },
                            mime = "application/pdf",
                            chooserTitle = t("pdf_report", lang)
                        )
                    },
                    onBackup = {
                        val snapshot = state
                        shareFile(
                            buildFile = {
                                newExportFile(context, "Backup_${snapshot.aktuellerMonat}_${snapshot.aktuellesJahr}.json")
                                    .also { it.writeText(repository.serializeState(snapshot)) }
                            },
                            mime = "application/json",
                            chooserTitle = t("backup", lang),
                            successMessage = t("backup_success", lang)
                        )
                    },
                    onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
                    onPdfImport = { pdfLauncher.launch(arrayOf("application/pdf")) }
                )
            }

            // Danger zone
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = appCardColors(container = MaterialTheme.colorScheme.background),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            t("danger_zone", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { heading() }
                        )
                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
                        ) {
                            Text(t("clear_all", lang))
                        }
                    }
                }
            }

            // room for the extended FAB
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(t("delete", lang)) },
            text = { Text(t("confirm_delete", lang)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteId = null
                        state = repository.deleteEntry(state, id)
                        repository.saveState(state)
                        message = t("entry_deleted", lang)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text(t("delete", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }, modifier = Modifier.heightIn(min = ButtonMinHeight)) { Text(t("cancel", lang)) }
            }
        )
    }

    if (showFinalReport) {
        FinalReportDialog(FinalReport.build(state.monatlicheHistorie), lang) { showFinalReport = false }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text(t("close_month", lang)) },
            text = { Text("${t("confirm_close", lang)} (${state.aktuellerMonat} ${state.aktuellesJahr})") },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseConfirm = false
                        val closedName = state.aktuellerMonat
                        if (!repository.monthHasEntries(state, state.aktuellesJahr, currentMonthNum)) {
                            message = "$closedName ${t("month_empty", lang)}"
                        } else {
                            val (newState, quarterMsg) = repository.closeSpecificMonth(state, state.aktuellesJahr, currentMonthNum)
                            state = newState
                            repository.saveState(state)
                            message = "$closedName ${t("month_closed", lang)}" + (quarterMsg?.let { "\n$it" } ?: "")
                        }
                    },
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text(t("confirm_yes", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }, modifier = Modifier.heightIn(min = ButtonMinHeight)) { Text(t("cancel", lang)) }
            }
        )
    }

    pendingRestore?.let { restored ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(t("restore", lang)) },
            text = { Text(t("restore_confirm", lang)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestore = null
                        state = restored
                        repository.saveState(state)
                        message = t("restore_success", lang)
                    },
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text(t("confirm_yes", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }, modifier = Modifier.heightIn(min = ButtonMinHeight)) { Text(t("cancel", lang)) }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(t("clear_all", lang)) },
            text = { Text(t("clear_confirm", lang)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        state = repository.clearAllHistory(state)
                        repository.saveState(state)
                        message = t("all_deleted", lang)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.heightIn(min = ButtonMinHeight)
                ) { Text(t("delete", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }, modifier = Modifier.heightIn(min = ButtonMinHeight)) { Text(t("cancel", lang)) }
            }
        )
    }

    // Add/Edit Entry Dialog
    if (showAddDialog) {
        AddEntryDialog(
            lang = lang,
            isEditing = editingEntryId != null,
            initialDatum = if (editingEntryId != null) editDatum else null,
            initialStart = if (editingEntryId != null) editStart else null,
            initialEnd = if (editingEntryId != null) editEnd else null,
            initialPause = if (editingEntryId != null) editPause else null,
            initialRemote = if (editingEntryId != null) editRemote else null,
            onDismiss = {
                showAddDialog = false
                editingEntryId = null
            },
            onAdd = { datum, start, end, pause, remote ->
                val id = editingEntryId
                val result = if (id != null) {
                    repository.updateEntry(state, id, datum, start, end, pause, remote)
                } else {
                    repository.addWorkEntry(state, datum, start, end, pause, remote)
                }
                val newState = result.state
                if (newState != null) {
                    state = if (id == null) switchToMonthOf(datum, newState) else newState
                    repository.saveState(state)
                    message = t("entry_saved", lang)
                    showAddDialog = false
                    editingEntryId = null
                    null
                } else {
                    result.error ?: t("errors", lang)
                }
            }
        )
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        SettingsBottomSheet(
            state = state,
            lang = lang,
            onDismiss = { showSettingsSheet = false },
            onSave = { newQuote, newSollStunden ->
                val result = repository.updateSettings(state, newQuote, newSollStunden)
                val newState = result.state
                if (newState != null) {
                    state = newState
                    repository.saveState(state)
                    showSettingsSheet = false
                    null
                } else {
                    result.error ?: t("errors", lang)
                }
            }
        )
    }

    val parseResult = pdfParseResult
    if (showPdfResultDialog && parseResult != null) {
        PdfImportDialog(
            result = parseResult,
            lang = lang,
            onDismiss = {
                showPdfResultDialog = false
                pdfParseResult = null
            },
            onImport = { entries ->
                var importedCount = 0
                var remoteCount = 0
                var latestImported: String? = null
                val errors = mutableListOf<String>()
                for (entry in entries) {
                    val remoteHours = if (entry.isRemote) null else 0.0
                    val result = repository.importWorkEntry(
                        state,
                        entry.datum,
                        entry.startTime,
                        entry.endTime,
                        entry.pauseMinutes,
                        remoteHours
                    )
                    val newState = result.state
                    if (newState != null) {
                        state = newState
                        importedCount++
                        if (entry.isRemote) remoteCount++
                        if (entry.datum > (latestImported ?: "")) latestImported = entry.datum
                    } else {
                        errors.add("${entry.datum}: ${result.error ?: ""}")
                    }
                }
                // Show the month of the statement that was just loaded, even if every entry in it
                // was already there (otherwise the screen would keep showing the old month).
                (latestImported ?: entries.maxOfOrNull { it.datum })?.let { state = switchToMonthOf(it, state) }
                state = repository.withFlexBalance(state, parseResult.flexBalance)
                repository.saveState(state)
                message = "$importedCount ${t("entries_imported", lang)} ($remoteCount ${t("remote_count", lang)})" +
                        if (errors.isNotEmpty()) {
                            ", ${errors.size} ${t("errors", lang)}\n" + errors.take(3).joinToString("\n") +
                                if (errors.size > 3) "\n…" else ""
                        } else ""
                showPdfResultDialog = false
                pdfParseResult = null
            }
        )
    }
}

@Composable
private fun EmptyState(lang: String, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(t("no_entries", lang), style = MaterialTheme.typography.titleMedium)
        Text(
            t("empty_hint", lang),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = ButtonMinHeight)) {
            Text(t("first_entry", lang))
        }
    }
}

@Composable
private fun ExportImportCard(
    lang: String,
    onCsv: () -> Unit,
    onPdfReport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onPdfImport: () -> Unit
) {
    val stacked = largeFont()
    @Composable
    fun Btn(label: String, onClick: () -> Unit, modifier: Modifier) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = ButtonMinHeight),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) { Text(label) }
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = appCardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                t("export_import", lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            if (stacked) {
                Btn(t("csv_export", lang), onCsv, Modifier.fillMaxWidth())
                Btn(t("pdf_report", lang), onPdfReport, Modifier.fillMaxWidth())
                Btn(t("backup", lang), onBackup, Modifier.fillMaxWidth())
                Btn(t("restore", lang), onRestore, Modifier.fillMaxWidth())
                Btn(t("pdf_import", lang), onPdfImport, Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(t("csv_export", lang), onCsv, Modifier.weight(1f).fillMaxHeight())
                    Btn(t("pdf_report", lang), onPdfReport, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(t("backup", lang), onBackup, Modifier.weight(1f).fillMaxHeight())
                    Btn(t("restore", lang), onRestore, Modifier.weight(1f).fillMaxHeight())
                }
                Btn(t("pdf_import", lang), onPdfImport, Modifier.fillMaxWidth())
            }
        }
    }
}

// Feature 2: Settings Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    state: TrackerState,
    lang: String,
    onDismiss: () -> Unit,
    /** Returns an error message if the values were rejected (the sheet then stays open). */
    onSave: (Double, Double) -> String?
) {
    var quoteValue by rememberSaveable { mutableStateOf(state.individuelleQuoteProzent.toFloat()) }
    var sollStunden by rememberSaveable { mutableStateOf(minutesText(state.sollStundenProTag)) }
    var sollInvalid by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle(t("settings", lang))

            // Remote Quote Slider
            Text("${t("remote_quote", lang)}: ${quoteValue.toInt()}%", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = quoteValue,
                onValueChange = { quoteValue = it },
                valueRange = 40f..60f,
                steps = 3, // 45, 50, 55 between 40 and 60
                modifier = Modifier.fillMaxWidth()
            )

            // Soll-Stunden TextField (accepts "7,5" and "7.5")
            OutlinedTextField(
                value = sollStunden,
                onValueChange = { sollStunden = it; sollInvalid = false; error = null },
                label = { Text(t("target_hours", lang)) },
                isError = sollInvalid,
                supportingText = if (sollInvalid) ({ Text(t("invalid_number", lang)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val parsed = parseDecimalOrNull(sollStunden)
                    if (parsed == null) {
                        sollInvalid = true
                    } else {
                        error = onSave(quoteValue.toDouble(), parsed)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
            ) {
                Text(t("save", lang))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PdfImportDialog(
    result: PdfParseResult,
    lang: String,
    onDismiss: () -> Unit,
    onImport: (List<ParsedWorkEntry>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("pdf_import", lang)) },
        text = {
            Column {
                Text(
                    "${result.entries.size} ${t("entries_found", lang)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(result.entries) { entry ->
                        val container = if (entry.isRemote) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val content = if (entry.isRemote) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        Card(
                            colors = appCardColors(container, content),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp).semantics(mergeDescendants = true) {},
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${fmtDate(entry.datum, lang)}: ${entry.startTime} - ${entry.endTime}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (entry.isRemote) {
                                    Text(
                                        t("remote_count", lang),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                if (result.errors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        t("notes", lang),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.status.warning
                    )
                    result.errors.forEach { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(result.entries) },
                enabled = result.entries.isNotEmpty(),
                modifier = Modifier.heightIn(min = ButtonMinHeight)
            ) {
                Text(t("import_all", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = ButtonMinHeight)) {
                Text(t("cancel", lang))
            }
        }
    )
}

@Composable
fun MonthOverviewCard(data: MonthData, month: String, year: Int, quartal: String, lang: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = appCardColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) {}) {
            Text(
                "$month $year",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$quartal | ${t("quote_label", lang)}: ${fmtL(data.individuelleQuote, 1, lang)}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun RemoteProgressCard(data: MonthData, lang: String) {
    val progress = if (data.maxRemoteStunden > 0) {
        (data.istRemoteStundenLaufend / data.maxRemoteStunden).coerceIn(0.0, 1.0).toFloat()
    } else 0f

    val rawRemaining = data.maxRemoteStunden - data.istRemoteStundenLaufend
    val remaining = rawRemaining.coerceAtLeast(0.0)
    val status = MaterialTheme.status
    val scheme = MaterialTheme.colorScheme
    val (progressColor, onProgressColor) = when (remainingLevel(rawRemaining)) {
        RemainingLevel.OVER_QUOTA -> scheme.error to scheme.onError
        RemainingLevel.LOW -> status.warning to status.onWarning
        RemainingLevel.OK -> status.success to status.onSuccess
    }
    val used = fmtL(data.istRemoteStundenLaufend, 2, lang)
    val max = fmtL(data.maxRemoteStunden, 2, lang)
    val percent = if (data.maxRemoteStunden > 0) Math.round(data.istRemoteStundenLaufend / data.maxRemoteStunden * 100).toInt() else 0

    Card(modifier = Modifier.fillMaxWidth(), colors = appCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                t("remote_hours", lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .semantics { contentDescription = t("progress_desc", lang).format(used, max) },
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${used}h", style = MaterialTheme.typography.bodyMedium)
                Text(
                    t("used_percent", lang).format("$percent"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("${max}h", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = progressColor,
                contentColor = onProgressColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${t("remaining", lang)}: ${fmtL(remaining, 2, lang)}h",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun QuickStatsRow(data: MonthData, lang: String) {
    val items = listOf(
        t("soll", lang) to "${fmtL(data.sollStundenGesamt, 1, lang)}h",
        t("max_remote", lang) to "${fmtL(data.maxRemoteStunden, 1, lang)}h",
        t("ist_remote", lang) to "${fmtL(data.istRemoteStundenLaufend, 1, lang)}h"
    )
    if (largeFont()) {
        // large text: the three cards go below each other (label left, value right)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (label, value) -> StatCard(label, value, Modifier.fillMaxWidth(), horizontal = true) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { (label, value) -> StatCard(label, value, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/** Flextime balance of the last imported statement: current period and total, e.g. "+2.5h / Σ 12.0h". */
@Composable
fun FlexBalanceLine(balance: FlexBalance?, lang: String) {
    if (balance == null) return
    fun fmt(v: Double) = (if (v > 0) "+" else "") + fmtL(v, 2, lang) + "h"
    val parts = listOfNotNull(balance.current?.let { fmt(it) }, balance.total?.let { "Σ " + fmt(it) })
    if (parts.isEmpty()) return
    Text(
        "${t("flex_balance", lang)}: ${parts.joinToString("  ")}",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    Card(modifier = modifier, colors = appCardColors()) {
        if (horizontal) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                modifier = Modifier.padding(12.dp).fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WorkEntryCard(entry: WorkEntry, lang: String, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = appCardColors()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(fmtDate(entry.datum, lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${entry.startTime} - ${entry.endTime}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${t("remote_count", lang)}: ${fmtL(entry.remoteStundenHeute, 2, lang)}h | ${t("net_hours", lang)}: ${fmtL(entry.totalStundenHeute, 2, lang)}h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.pauseWarning?.takeIf { entry.source != SOURCE_PDF_IMPORT }?.let {
                    Text(
                        localizePauseWarning(it, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = t("edit_entry_for", lang).format(entry.datum),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = t("delete_entry_for", lang).format(entry.datum),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCard(month: MonthData, lang: String) {
    // Remote limit exceeded -> error container (same rule on every platform, see FinalReport)
    val over = FinalReport.isOverQuota(month)
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (over) appCardColors(scheme.errorContainer, scheme.onErrorContainer) else appCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) {}) {
            Text(
                "${month.monat} ${month.jahr}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${t("remote_count", lang)}: ${fmtL(month.istRemoteStundenLaufend, 2, lang)}h / ${fmtL(month.maxRemoteStunden, 2, lang)}h",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${t("quote_label", lang)}: ${fmtL(month.tatsaechlicheRemoteQuote, 1, lang)}%",
                style = MaterialTheme.typography.bodyMedium
            )
            if (over) {
                // not by colour alone
                Text(t("rating_exceeded", lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    /** Returns an error message if the entry was rejected (the dialog then stays open and shows it). */
    onAdd: (String, String, String, Double, Double?) -> String?
) {
    var datum by rememberSaveable { mutableStateOf(initialDatum ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var start by rememberSaveable { mutableStateOf(initialStart ?: "08:00") }
    var end by rememberSaveable { mutableStateOf(initialEnd ?: "16:30") }
    var pause by rememberSaveable { mutableStateOf(initialPause ?: "30") }
    var remote by rememberSaveable { mutableStateOf(initialRemote ?: "") }
    var datumInvalid by rememberSaveable { mutableStateOf(false) }
    var startInvalid by rememberSaveable { mutableStateOf(false) }
    var endInvalid by rememberSaveable { mutableStateOf(false) }
    var pauseInvalid by rememberSaveable { mutableStateOf(false) }
    var remoteInvalid by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    // a rejected entry shows its message at the top of the form: make sure it is in view
    LaunchedEffect(error) { if (error != null) scroll.animateScrollTo(0) }

    val next = KeyboardOptions(imeAction = ImeAction.Next)
    val nextDecimal = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) t("edit_entry", lang) else t("record_time", lang)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scroll).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = datum,
                    onValueChange = { datum = it; datumInvalid = false; error = null },
                    label = { Text(t("date_label", lang)) },
                    isError = datumInvalid,
                    supportingText = if (datumInvalid) ({ Text(t("invalid_date", lang)) }) else null,
                    keyboardOptions = next,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it; startInvalid = false; error = null },
                        label = { Text(t("start_label", lang), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        isError = startInvalid,
                        supportingText = if (startInvalid) ({ Text(t("invalid_time", lang)) }) else null,
                        keyboardOptions = next,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it; endInvalid = false; error = null },
                        label = { Text(t("end_label", lang), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        isError = endInvalid,
                        supportingText = if (endInvalid) ({ Text(t("invalid_time", lang)) }) else null,
                        keyboardOptions = next,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = pause,
                    onValueChange = { pause = it; pauseInvalid = false; error = null },
                    label = { Text(t("pause_label", lang)) },
                    isError = pauseInvalid,
                    supportingText = if (pauseInvalid) ({ Text(t("invalid_number", lang)) }) else null,
                    keyboardOptions = nextDecimal,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remote,
                    onValueChange = { remote = it; remoteInvalid = false; error = null },
                    label = { Text(t("remote_label", lang)) },
                    isError = remoteInvalid,
                    supportingText = if (remoteInvalid) ({ Text(t("invalid_number", lang)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // field-wise validation; blank pause = 30 min, blank remote = whole day
                    val pauseMinutes = if (pause.isBlank()) 30.0 else parseDecimalOrNull(pause)
                    val remoteHours = if (remote.isBlank()) null else parseDecimalOrNull(remote)
                    datumInvalid = !isValidIsoDate(datum)
                    startInvalid = !isValidTime(start)
                    endInvalid = !isValidTime(end)
                    pauseInvalid = pauseMinutes == null
                    remoteInvalid = remote.isNotBlank() && remoteHours == null
                    if (!datumInvalid && !startInvalid && !endInvalid && pauseMinutes != null && !remoteInvalid) {
                        error = onAdd(datum.trim(), start.trim(), end.trim(), pauseMinutes, remoteHours)
                    }
                },
                modifier = Modifier.heightIn(min = ButtonMinHeight)
            ) {
                Text(t("save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = ButtonMinHeight)) {
                Text(t("cancel", lang))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectorRow(
    months: List<Pair<Int, Int>>,
    currentYear: Int,
    currentMonthNum: Int,
    onMonthSelected: (Int, Int) -> Unit,
    lang: String
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = appCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                t("available_months", lang),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                months.forEach { (year, monthNum) ->
                    val isSelected = year == currentYear && monthNum == currentMonthNum
                    val monthName = MonthNames.getMonthName(monthNum)
                    val shortName = monthName.take(3)

                    FilterChip(
                        selected = isSelected,
                        onClick = { onMonthSelected(year, monthNum) },
                        modifier = Modifier.heightIn(min = ButtonMinHeight),
                        label = { Text("$shortName $year", style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ratingColors(rating: FinalReport.Rating?): Pair<Color, Color> {
    val s = MaterialTheme.status
    val scheme = MaterialTheme.colorScheme
    return when (rating) {
        FinalReport.Rating.GOOD -> s.successContainer to s.onSuccessContainer
        FinalReport.Rating.TIGHT -> s.warningContainer to s.onWarningContainer
        FinalReport.Rating.EXCEEDED -> scheme.errorContainer to scheme.onErrorContainer
        null -> scheme.surfaceVariant to scheme.onSurface
    }
}

internal fun ratingText(rating: FinalReport.Rating?, lang: String): String = when (rating) {
    FinalReport.Rating.GOOD -> t("rating_good", lang)
    FinalReport.Rating.TIGHT -> t("rating_tight", lang)
    FinalReport.Rating.EXCEEDED -> t("rating_exceeded", lang)
    null -> ""
}

/** One report line: label on the left, value on the right (tabular figures come from the theme). */
@Composable
private fun ReportRow(label: String, value: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null, textAlign = TextAlign.End)
    }
}

/** Quarterly final report: quarters in order, each with its months and a rating. */
@Composable
fun FinalReportDialog(report: FinalReport.Report, lang: String, onDismiss: () -> Unit) {
    fun h(v: Double) = fmtL(v, 2, lang) + "h"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("final_report", lang)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (report.quarters.isEmpty()) {
                    Text(t("report_empty", lang))
                } else {
                    val o = report.overall
                    val (oBg, oFg) = ratingColors(o.rating)
                    Card(colors = appCardColors(oBg, oFg), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${t("report_overall", lang)}: ${ratingText(o.rating, lang)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            ReportRow("${monthsText(o.months, lang)}, ${quartersText(o.quarters, lang)}", "")
                            ReportRow(t("report_remote", lang), "${h(o.remote)} / ${t("report_limit", lang)} ${h(o.maxRemote)}")
                            ReportRow(t("report_quota", lang), "${fmtL(o.actualQuote, 2, lang)}%")
                            Text(monthsOverText(o.monthsOver, lang), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    for (q in report.quarters) {
                        val (bg, fg) = ratingColors(q.rating)
                        Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Column(Modifier.fillMaxWidth().background(bg).padding(10.dp).semantics(mergeDescendants = true) {}) {
                                    Text(
                                        q.quartal + if (q.complete) "" else " (${t("report_quarter_incomplete", lang)}: ${q.monthCount}/3)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold, color = fg,
                                        modifier = Modifier.semantics { heading() }
                                    )
                                    Text(ratingText(q.rating, lang), style = MaterialTheme.typography.bodySmall, color = fg)
                                }
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ReportRow(
                                        t("report_remote", lang),
                                        "${h(q.remote)} / ${t("report_limit", lang)} ${h(q.maxRemote)} (${Math.round(q.usage * 100)} %)"
                                    )
                                    ReportRow(t("report_quota", lang), "${fmtL(q.actualQuote, 2, lang)}%")
                                    Text(monthsOverText(q.monthsOver, lang), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(4.dp))
                                    for (m in q.months) {
                                        val rowBg = if (m.over) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                                        val rowFg = if (m.over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                        CompositionLocalProvider(LocalContentColor provides rowFg) {
                                            ReportRow(
                                                "${m.monat} ${m.jahr}",
                                                "${h(m.remote)} / ${h(m.maxRemote)} | ${fmtL(m.actualQuote, 2, lang)}%",
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(rowBg)
                                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                                bold = m.over
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(t("report_rule", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = ButtonMinHeight)) { Text(t("close", lang)) } }
    )
}
