package com.remotework.tracker.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.remotework.tracker.*
import java.io.File
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
        "ist_remote" to "Ist Remote",
        "remaining" to "Verbleibend",
        "confirm_delete" to "Wirklich löschen?",
        "confirm_close" to "Monat wirklich abschließen?",
        "pdf_import" to "PDF Import",
        "remote_hours" to "Remote-Stunden",
        "available_months" to "Verfügbare Monate",
        "entry_saved" to "Eintrag gespeichert",
        "entry_deleted" to "Eintrag gelöscht",
        "all_deleted" to "Alles gelöscht",
        "clear_confirm" to "Wirklich alles löschen? Historie, Einträge und Einstellungen werden zurückgesetzt.",
        "month_closed" to "abgeschlossen",
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
        "csv_export" to "CSV Export",
        "pdf_report" to "PDF Bericht",
        "backup" to "Backup",
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
        "dark_mode" to "Dark Mode"
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
        "ist_remote" to "Actual Remote",
        "remaining" to "Remaining",
        "confirm_delete" to "Really delete?",
        "confirm_close" to "Really close this month?",
        "pdf_import" to "PDF Import",
        "remote_hours" to "Remote Hours",
        "available_months" to "Available Months",
        "entry_saved" to "Entry saved",
        "entry_deleted" to "Entry deleted",
        "all_deleted" to "All deleted",
        "clear_confirm" to "Really delete everything? History, entries and settings will be reset.",
        "month_closed" to "closed",
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
        "quote_label" to "Quote",
        "csv_export" to "CSV Export",
        "pdf_report" to "PDF Report",
        "backup" to "Backup",
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
        "dark_mode" to "Dark mode"
    )
)

internal fun t(key: String, lang: String): String = translations[lang]?.get(key) ?: key

class MainActivity : ComponentActivity() {

    private lateinit var repository: TrackerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = LocalStorage(applicationContext)
        repository = TrackerRepository(storage)

        setContent {
            val state = remember { mutableStateOf(repository.loadState()) }
            RemoteWorkTrackerTheme(darkMode = state.value.darkMode) {
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
fun cardSurface(dark: Long = 0xFF374151, light: Long = 0xFFF3F4F6): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(dark) else Color(light)

// Feature 4: Dark Mode
@Composable
fun RemoteWorkTrackerTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkMode) {
        darkColorScheme(
            primary = Color(0xFF2563EB),
            background = Color(0xFF1F2937),
            surface = Color(0xFF141922),
            onBackground = Color(0xFFE6E9EF),
            onSurface = Color(0xFFE6E9EF),
            secondary = Color(0xFF047857),
            error = Color(0xFFDC2626),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4F46E5),
            secondary = Color(0xFF047857),
            error = Color(0xFFDC2626)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
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
                        if (json == null) OperationResult(error = "Datei zu groß oder nicht lesbar")
                        else repository.restoreState(json)
                    } catch (e: Exception) {
                        OperationResult(error = e.message ?: "Lesefehler")
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
                title = { Text("Remote Work Tracker", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    // Language toggle
                    IconButton(modifier = Modifier.semantics { contentDescription = t("language", lang) }, onClick = {
                        val newLang = if (state.language == "de") "en" else "de"
                        state = state.copy(language = newLang)
                        repository.saveState(state)
                    }) {
                        Text(
                            if (state.language == "de") "EN" else "DE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    // Dark mode toggle
                    IconButton(modifier = Modifier.semantics { contentDescription = t("dark_mode", lang) }, onClick = {
                        state = state.copy(darkMode = !state.darkMode)
                        repository.saveState(state)
                    }) {
                        Text(
                            if (state.darkMode) "\u2600" else "\u263D",
                            color = Color.White,
                            fontSize = 18.sp
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // PDF Import FAB
                FloatingActionButton(
                    modifier = Modifier.semantics { contentDescription = t("pdf_import", lang) },
                    onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    containerColor = Color(0xFF7C3AED)
                ) {
                    Text("PDF", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                // Add Entry FAB
                FloatingActionButton(
                    modifier = Modifier.semantics { contentDescription = t("add_entry", lang) },
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("+", fontSize = 24.sp, color = Color.White)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
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
            }

            // Current Month Entries
            item {
                Text(
                    "${t("entries", lang)} (${state.aktuellerMonat} ${state.aktuellesJahr})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (currentMonthEntries.isEmpty()) {
                item {
                    Text(t("no_entries", lang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(currentMonthEntries) { entry ->
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

            // Export / Import Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardSurface())
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            t("export_import", lang),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // CSV Export
                            Button(
                                onClick = {
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
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                            ) {
                                Text("CSV", fontSize = 11.sp)
                            }

                            // PDF Report
                            Button(
                                onClick = {
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
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                            ) {
                                Text("PDF", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Backup
                            Button(
                                onClick = {
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
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Text(t("backup", lang), fontSize = 12.sp, maxLines = 1)
                            }

                            // Restore
                            Button(
                                onClick = {
                                    restoreLauncher.launch(arrayOf("application/json"))
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309))
                            ) {
                                Text(t("restore", lang), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // History
            if (state.monatlicheHistorie.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t("history", lang),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { showFinalReport = true }) {
                            Text(t("final_report", lang), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                items(FinalReport.sortHistory(state.monatlicheHistorie, newestFirst = true)) { month ->
                    HistoryCard(month, lang)
                }
            }

            // Actions
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showCloseConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                        modifier = Modifier.weight(1f),
                        enabled = currentMonthEntries.isNotEmpty()
                    ) {
                        Text(t("close_month", lang), fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showClearConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(t("clear_all", lang), fontSize = 12.sp)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(160.dp)) }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(t("delete", lang)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text(t("cancel", lang)) } }
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
                Button(onClick = {
                    showCloseConfirm = false
                    val closedName = state.aktuellerMonat
                    val (newState, quarterMsg) = repository.closeSpecificMonth(state, state.aktuellesJahr, currentMonthNum)
                    state = newState
                    repository.saveState(state)
                    message = "$closedName ${t("month_closed", lang)}" + (quarterMsg?.let { "\n$it" } ?: "")
                }) { Text(t("confirm_yes", lang)) }
            },
            dismissButton = { TextButton(onClick = { showCloseConfirm = false }) { Text(t("cancel", lang)) } }
        )
    }

    pendingRestore?.let { restored ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(t("restore", lang)) },
            text = { Text(t("restore_confirm", lang)) },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    state = restored
                    repository.saveState(state)
                    message = t("restore_success", lang)
                }) { Text(t("confirm_yes", lang)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(t("cancel", lang)) } }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(t("delete", lang)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(t("cancel", lang)) }
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                t("settings", lang),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            // Remote Quote Slider
            Text("${t("remote_quote", lang)}: ${quoteValue.toInt()}%")
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(t("save", lang))
            }

            Spacer(modifier = Modifier.height(32.dp))
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
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(result.entries) { entry ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (entry.isRemote) cardSurface(0xFF312E81, 0xFFE0E7FF) else cardSurface()
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${entry.datum}: ${entry.startTime} - ${entry.endTime}",
                                    fontSize = 14.sp
                                )
                                if (entry.isRemote) {
                                    Text(
                                        t("remote_count", lang),
                                        fontSize = 12.sp,
                                        color = cardSurface(0xFFA5B4FC, 0xFF4F46E5),
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
                        "${t("notes", lang)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309)
                    )
                    result.errors.forEach { error ->
                        Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(result.entries) },
                enabled = result.entries.isNotEmpty()
            ) {
                Text(t("import_all", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("cancel", lang))
            }
        }
    )
}

@Composable
fun MonthOverviewCard(data: MonthData, month: String, year: Int, quartal: String, lang: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardSurface(0xFF312E81, 0xFFE0E7FF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "$month $year",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = cardSurface(0xFFA5B4FC, 0xFF4F46E5)
            )
            Text(
                "$quartal | ${t("quote_label", lang)}: ${String.format(Locale.US, "%.1f", data.individuelleQuote)}%",
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val progressColor = when (remainingLevel(rawRemaining)) {
        RemainingLevel.OVER_QUOTA -> Color(0xFFDC2626)
        RemainingLevel.LOW -> Color(0xFFB45309)
        RemainingLevel.OK -> Color(0xFF047857)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(t("remote_hours", lang), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                color = progressColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${String.format(Locale.US, "%.2f", data.istRemoteStundenLaufend)}h")
                Text("${String.format(Locale.US, "%.2f", data.maxRemoteStunden)}h")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = progressColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${t("remaining", lang)}: ${String.format(Locale.US, "%.2f", remaining)}h",
                    modifier = Modifier.padding(12.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun QuickStatsRow(data: MonthData, lang: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(t("soll", lang), "${String.format(Locale.US, "%.1f", data.sollStundenGesamt)}h", Modifier.weight(1f))
        StatCard(t("max_remote", lang), "${String.format(Locale.US, "%.1f", data.maxRemoteStunden)}h", Modifier.weight(1f))
        StatCard(t("ist_remote", lang), "${String.format(Locale.US, "%.1f", data.istRemoteStundenLaufend)}h", Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardSurface())
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WorkEntryCard(entry: WorkEntry, lang: String, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.datum, fontWeight = FontWeight.Bold)
                Text("${entry.startTime} - ${entry.endTime}")
                Text(
                    "${t("remote_count", lang)}: ${String.format(Locale.US, "%.2f", entry.remoteStundenHeute)}h | ${t("net_hours", lang)}: ${String.format(Locale.US, "%.2f", entry.totalStundenHeute)}h",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.pauseWarning?.takeIf { entry.source != SOURCE_PDF_IMPORT }?.let {
                    Text(it, fontSize = 12.sp, color = Color(0xFFDC2626))
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = t("edit_entry", lang), tint = Color(0xFF2563EB))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = t("delete", lang), tint = Color(0xFFDC2626))
                }
            }
        }
    }
}

@Composable
fun HistoryCard(month: MonthData, lang: String) {
    // Remote limit exceeded -> red background (same rule on every platform, see FinalReport)
    val over = FinalReport.isOverQuota(month)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (over) cardSurface(0xFF4A1D1D, 0xFFFEE2E2) else cardSurface(0xFF303A4B, 0xFFF9FAFB)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${month.monat} ${month.jahr}",
                fontWeight = FontWeight.Bold
            )
            Text(
                "${t("remote_count", lang)}: ${String.format(Locale.US, "%.2f", month.istRemoteStundenLaufend)}h / ${String.format(Locale.US, "%.2f", month.maxRemoteStunden)}h",
                fontSize = 14.sp
            )
            Text(
                "${t("quote_label", lang)}: ${String.format(Locale.US, "%.1f", month.tatsaechlicheRemoteQuote)}%",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    var pauseInvalid by rememberSaveable { mutableStateOf(false) }
    var remoteInvalid by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) t("edit_entry", lang) else t("record_time", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = datum,
                    onValueChange = { datum = it; error = null },
                    label = { Text(t("date_label", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = start,
                    onValueChange = { start = it; error = null },
                    label = { Text(t("start_label", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = end,
                    onValueChange = { end = it; error = null },
                    label = { Text(t("end_label", lang)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pause,
                    onValueChange = { pause = it; pauseInvalid = false; error = null },
                    label = { Text(t("pause_label", lang)) },
                    isError = pauseInvalid,
                    supportingText = if (pauseInvalid) ({ Text(t("invalid_number", lang)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remote,
                    onValueChange = { remote = it; remoteInvalid = false; error = null },
                    label = { Text(t("remote_label", lang)) },
                    isError = remoteInvalid,
                    supportingText = if (remoteInvalid) ({ Text(t("invalid_number", lang)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // blank pause = 30 min, blank remote = whole day; anything else must be a valid number
                val pauseMinutes = if (pause.isBlank()) 30.0 else parseDecimalOrNull(pause)
                val remoteHours = if (remote.isBlank()) null else parseDecimalOrNull(remote)
                pauseInvalid = pauseMinutes == null
                remoteInvalid = remote.isNotBlank() && remoteHours == null
                if (pauseMinutes != null && !remoteInvalid) {
                    error = onAdd(datum.trim(), start.trim(), end.trim(), pauseMinutes, remoteHours)
                }
            }) {
                Text(t("save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("cancel", lang))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectorRow(
    months: List<Pair<Int, Int>>,
    currentYear: Int,
    currentMonthNum: Int,
    onMonthSelected: (Int, Int) -> Unit,
    lang: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardSurface())
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                t("available_months", lang),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                months.forEach { (year, monthNum) ->
                    val isSelected = year == currentYear && monthNum == currentMonthNum
                    val monthName = MonthNames.getMonthName(monthNum)
                    val shortName = monthName.take(3)

                    FilterChip(
                        selected = isSelected,
                        onClick = { onMonthSelected(year, monthNum) },
                        label = {
                            Text(
                                "$shortName $year",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4F46E5),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}


@Composable
private fun ratingColors(rating: FinalReport.Rating?): Pair<Color, Color> = when (rating) {
    FinalReport.Rating.GOOD -> cardSurface(0xFF14391F, 0xFFDCFCE7) to cardSurface(0xFF86EFAC, 0xFF166534)
    FinalReport.Rating.TIGHT -> cardSurface(0xFF3D3410, 0xFFFEF9C3) to cardSurface(0xFFFDE68A, 0xFF854D0E)
    FinalReport.Rating.EXCEEDED -> cardSurface(0xFF4A1D1D, 0xFFFEE2E2) to cardSurface(0xFFFCA5A5, 0xFF991B1B)
    null -> cardSurface() to MaterialTheme.colorScheme.onSurface
}

internal fun ratingText(rating: FinalReport.Rating?, lang: String): String = when (rating) {
    FinalReport.Rating.GOOD -> t("rating_good", lang)
    FinalReport.Rating.TIGHT -> t("rating_tight", lang)
    FinalReport.Rating.EXCEEDED -> t("rating_exceeded", lang)
    null -> ""
}

private fun h(v: Double) = String.format(Locale.US, "%.2f", v)

/** Quarterly final report: quarters in order, each with its months and a rating. */
@Composable
fun FinalReportDialog(report: FinalReport.Report, lang: String, onDismiss: () -> Unit) {
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
                    Card(colors = CardDefaults.cardColors(containerColor = oBg), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${t("report_overall", lang)}: ${ratingText(o.rating, lang)}", fontWeight = FontWeight.Bold, color = oFg)
                            Text(
                                "${o.months} ${t("report_months", lang)}, ${o.quarters} Q | Remote ${h(o.remote)}h / ${t("report_limit", lang)} ${h(o.maxRemote)}h | " +
                                    "${t("report_quota", lang)} ${h(o.actualQuote)}% | ${o.monthsOver} ${t("report_months_over", lang)}",
                                fontSize = 12.sp, color = oFg
                            )
                        }
                    }
                    for (q in report.quarters) {
                        val (bg, fg) = ratingColors(q.rating)
                        Card(colors = CardDefaults.cardColors(containerColor = cardSurface()), modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Column(Modifier.fillMaxWidth().background(bg).padding(10.dp)) {
                                    Text(
                                        q.quartal + if (q.complete) "" else " (${t("report_quarter_incomplete", lang)}: ${q.monthCount}/3)",
                                        fontWeight = FontWeight.Bold, color = fg
                                    )
                                    Text(ratingText(q.rating, lang), fontSize = 12.sp, color = fg)
                                }
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        "Remote ${h(q.remote)}h / ${t("report_limit", lang)} ${h(q.maxRemote)}h (${Math.round(q.usage * 100)} %) | " +
                                            "${t("report_quota", lang)} ${h(q.actualQuote)}% | ${q.monthsOver} ${t("report_months_over", lang)}",
                                        fontSize = 12.sp
                                    )
                                    for (m in q.months) {
                                        val red = cardSurface(0xFF4A1D1D, 0xFFFEE2E2)
                                        Row(
                                            Modifier.fillMaxWidth().padding(top = 4.dp)
                                                .background(if (m.over) red else Color.Transparent)
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${m.monat} ${m.jahr}", fontSize = 12.sp)
                                            Text("${h(m.remote)}h / ${h(m.maxRemote)}h | ${h(m.actualQuote)}%", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(t("report_rule", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("close", lang)) } }
    )
}
