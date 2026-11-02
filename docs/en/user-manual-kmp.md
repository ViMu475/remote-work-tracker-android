# User Manual -- Remote Work Tracker (KMP Android)

**Version 1.0.0**

Android app for tracking remote work hours against company quotas. Enforces
German labor law (ArbZG) break rules and monitors monthly remote work
percentages with automatic quarterly adjustments.

---

## Table of Contents

1. [Getting Started](#1-getting-started) -- Requirements, Installation, First Launch
2. [Daily Workflow](#2-daily-workflow) -- Recording, Editing, Deleting Entries
3. [Understanding the Dashboard](#3-understanding-the-dashboard) -- Overview, Progress, Stats
4. [Settings](#4-settings) -- Quota, Target Hours
5. [Dark Mode and Language](#5-dark-mode-and-language)
6. [Export and Backup](#6-export-and-backup) -- CSV, PDF, JSON Backup, Restore
7. [Month Management](#7-month-management) -- Close Month, Quarterly Adjustment, History
8. [PDF Import](#8-pdf-import) -- SAP TimeStatement Import
9. [Validation and Labor Law](#9-validation-and-labor-law) -- ArbZG, Holidays, Overlaps
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Getting Started

### 1.1 System Requirements

| Requirement      | Minimum                        |
|------------------|--------------------------------|
| Android version  | 7.0 (Nougat, API level 24)    |
| Storage          | approximately 50 MB            |
| Architecture     | ARM64 or x86_64                |

No internet connection required. All data is stored locally via Android
SharedPreferences.

### 1.2 Installation

**Option A -- Install from APK (sideloading)**

1. Transfer the APK file to your Android device.
2. Open it with a file manager. Enable "Install from unknown sources" if
   prompted (**Settings > Security**).
3. Follow the on-screen prompts.

**Option B -- Build from source**

1. Open the project in Android Studio with a connected device or emulator.
2. Run `./gradlew :androidApp:installDebug` or use the Run button.

### 1.3 First Launch

When you start the app for the first time, you will see an empty dashboard
with the current month and year displayed. The app initializes with these
default settings:

- **Remote Quota:** 40%
- **Target Hours per Day:** 8.0 hours
- **Language:** German (DE)
- **Theme:** Light Mode

Monthly target hours are calculated automatically from working days
(Mon-Fri, excluding Bavarian public holidays).

---

## 2. Daily Workflow

### 2.1 Recording Work Time

To add a new work entry:

1. Tap the blue **plus button (+)** in the bottom-right corner of the screen.
2. A dialog opens with the following fields:

| Field            | Format     | Description                                      |
|------------------|------------|--------------------------------------------------|
| Date             | YYYY-MM-DD | Defaults to today's date                         |
| Start            | HH:MM      | Work start time (default: 08:00)                 |
| End              | HH:MM      | Work end time (default: 16:30)                   |
| Pause (Minutes)  | Number     | Break duration in minutes (default: 30)          |
| Remote Hours     | Number     | Hours worked remotely; leave empty for all hours |

3. Tap **Save** to store the entry.

If the Remote Hours field is left empty, the entire net working time
(`(End - Start) - Pause`) is counted as remote. If you worked partly in the
office, enter only the remote portion. Dashboard metrics update automatically
after saving.

### 2.2 Editing an Entry

To modify an existing entry:

1. Find the entry card in the entries list.
2. Tap the **pencil icon** on the right side of the card.
3. The add-entry dialog opens with all fields pre-filled with the existing
   values.
4. Make your changes and tap **Save**.

The same validation rules apply as when creating a new entry.

### 2.3 Deleting an Entry

1. Tap the **X button** on the right side of the entry card.
2. The entry is removed immediately and all metrics are recalculated.

There is no undo. Re-enter the data manually or restore from a backup.

---

## 3. Understanding the Dashboard

### 3.1 Month Overview

The top card displays the **Month and Year** (e.g., "Maerz 2026") and the
**Quarter and Quota** (e.g., "Q1/2026 | Quote: 40.0%").

### 3.2 Remote Hours Progress Bar

A horizontal progress bar shows used remote hours relative to the maximum
allowed, with actual (left) and maximum (right) hour labels. A colored
"Remaining" badge shows hours still available. Colors indicate status:

| Color   | Condition                                |
|---------|------------------------------------------|
| Green   | More than 10 hours remaining             |
| Orange  | Fewer than 10 hours remaining            |
| Red     | Quota exceeded (actual > maximum)        |

### 3.3 Quick Stats

Three compact stat cards appear in a row below the progress bar:

| Card         | Description                                          |
|--------------|------------------------------------------------------|
| Target       | Total target work hours for the month (Soll-Stunden) |
| Max Remote   | Maximum allowed remote hours (Target x Quota%)       |
| Actual Remote| Total remote hours logged so far                     |

These values update in real time as you add, edit, or delete entries.

### 3.4 Month Selection

When entries exist for multiple months, selection chips appear above the
month overview. Tap a chip to switch the view. The currently selected month
is highlighted. The selector is hidden when only one month has entries.

---

## 4. Settings

### 4.1 Opening Settings

Tap the **gear icon** in the top app bar. A bottom sheet slides up.

### 4.2 Remote Quota

- Slider ranging from **40% to 60%** in 5% steps (40, 45, 50, 55, 60).
- Determines what percentage of monthly target hours can be worked remotely.
- Example: 40% quota with 160 target hours = 64 max remote hours.

### 4.3 Target Hours per Day

- Text input field, valid range: **1.0 to 10.0 hours** (default: 8.0).
- Multiplied by working days in the month to calculate monthly target hours.
- Part-time employees should adjust to their contractual daily hours.

### 4.4 Saving Settings

Tap **Save** to validate, recalculate all metrics, and close the sheet.
Invalid values (quota outside 40-60%, hours outside 1-10) trigger an error
message without applying changes.

---

## 5. Dark Mode and Language

### 5.1 Dark Mode

- Tap the **moon/sun symbol** in the top app bar to toggle between light
  and dark themes. The setting persists across app restarts.

### 5.2 Language Switch

- Tap the **DE/EN** button in the top app bar to switch between German and
  English. All UI labels, messages, and dialogs update. The setting persists
  across app restarts.
- Note: Month names in the data model are always stored in German internally.

---

## 6. Export and Backup

The Export/Import section sits below the entries list with four buttons.

### 6.1 CSV Export

- **Green CSV button** generates a CSV with all entries for the selected
  month. Columns: `Datum, Start, Ende, Pause (min), Remote (h), Gesamt (h)`.
- Filename: `Export_[Month]_[Year].csv`
- Opens the Android share sheet (email, Drive, etc.).

### 6.2 PDF Report

- **Purple PDF button** generates an A4 monthly report containing a summary
  (target hours, actual hours, remote hours, quota) and an entry table
  sorted by date. Automatic page breaks for long months.
- Filename: `Monatsbericht_[Month]_[Year].pdf`
- Opens the Android share sheet.

### 6.3 JSON Backup

- **Blue Backup button** exports the complete app state (all entries, all
  months, history, settings) as a JSON file.
- Filename: `Backup_[Month]_[Year].json`
- Create backups regularly, especially before closing months or clearing data.

### 6.4 Restore

- **Orange Restore button** opens the file picker for JSON files.
- Select a previously exported backup to replace all current data.
- Warning: Restoring completely overwrites the current app state.

---

## 7. Month Management

### 7.1 Closing a Month

The orange **Close Month** button at the bottom of the dashboard archives
the current month. It is only enabled when entries exist. Closing a month:

1. Archives the month's summary to the history section.
2. Removes the month's entries from active records.
3. Switches to the next available month or the current calendar month.
4. Triggers a quarterly adjustment check if applicable (see section 7.2).

### 7.2 Quarterly Adjustment

The app automatically evaluates remote work compliance at the end of each
quarter. Quarter-end months are:

| Quarter | End Month   |
|---------|-------------|
| Q1      | March       |
| Q2      | June        |
| Q3      | September   |
| Q4      | December    |

When the third month of a quarter is closed and all three months are in
history, the app adjusts the quota:

- **Exceeded:** actual remote > max remote across quarter -- quota reduced
  by 10 percentage points (minimum 40%).
- **Within budget:** actual remote <= max remote -- quota increased by 10
  percentage points (maximum 60%).

The new quota takes effect immediately for the next month.

### 7.3 History

Closed months appear in the **History** section, **sorted by year and month (most recent first)**.
Each card shows month/year, remote hours (actual / max), and actual quota
percentage.

**Red card:** If a month has more remote hours than its limit (actual above max), its card has a
**red background** (in light and dark mode). Months within the limit are unchanged.

### 7.3.1 Final Report

The **Final Report** button next to the "History" heading opens a report over all closed months,
**ordered by quarter** (oldest quarter first):

- Per quarter: remote hours, limit (sum of the monthly limits), usage in percent, actual quota, the
  number of months over the limit, and the months of the quarter (red rows = over the limit).
- **Rating** per quarter: up to 90 % of the limit **Good** (green), up to 100 % **Tight** (yellow),
  above that **Exceeded** (red).
- A quarter with fewer than 3 closed months is marked **incomplete**.
- The **overall rating** over all quarters is shown at the top. The report only looks at closed
  months, not at the current one.

### 7.4 Clear All

The red **Clear All** button deletes all data and resets to factory defaults
(40% quota, 8.0h/day, current month). This action is irreversible -- create
a backup first.

---

## 8. PDF Import

### 8.1 Importing a PDF

1. Tap the purple **PDF** FAB (upper button in bottom-right corner).
2. Select an SAP TimeStatement PDF from the file picker.
3. The app parses the document and opens a preview dialog.

### 8.2 Import Preview

The preview shows the number of detected entries as cards:
- **Blue background:** remote work entries (labeled "Remote").
- **Gray background:** office work entries.

Tap **Import All** to import. Entries violating validation rules (overlaps,
holidays) are skipped and counted as errors. A summary message shows the
result: "X Entries imported (Y Remote), Z Errors".

Imported entries show **no break warning**: a statement lists no breaks, so the pause is stored as 0.
Manually entered entries are still checked against the statutory break.

### 8.3 Supported Formats

The PDF parser supports the following document formats:

| Format                   | Language | Date Pattern                    |
|--------------------------|----------|---------------------------------|
| SAP TimeStatement        | English  | "from DD.MM.YYYY to DD.MM.YYYY" |
| SAP Zeitnachweis          | German   | "vom DD.MM.YYYY bis DD.MM.YYYY" |

The parser recognizes:

- **Weekday abbreviations:** MO, TU, WE, TH, FR, SA, SU (English) and
  MO, DI, MI, DO, FR, SA, SO (German).
- **Remote work keywords:** "Remote Work", "Remote", "Telearbeit",
  "Home Office", "Homeoffice".
- **Office work keywords:** "At Work", "Anwesend", "Im Buero", "Buero".
- **Time format:** HH:MM HH:MM with hours as a decimal (e.g., 8,50).

A default pause of 30 minutes is applied to each imported entry.

---

## 9. Validation and Labor Law

### 9.1 Break Rules (ArbZG)

German labor law mandates minimum breaks based on gross working hours:

| Gross Working Hours | Minimum Break Required |
|---------------------|------------------------|
| Less than 6 hours   | No minimum             |
| 6 to 9 hours        | 30 minutes             |
| More than 9 hours   | 45 minutes             |

Insufficient breaks do not block entry creation but produce a red warning
on the entry card (e.g., "WARNUNG: Pause (0.25h) < gesetzl. Minimum
(0.50h)!").

### 9.2 Weekends and Holidays

- **Sundays:** blocked ("Recording work time on Sundays is not permitted").
- **Bavarian public holidays:** blocked ("Recording work time on a public
  holiday is not permitted").
- **Saturdays:** blocked ("only permitted with approved overtime").

Recognized Bavarian holidays (2025-2030): New Year's Day, Epiphany, Good
Friday, Easter Monday, Labor Day, Ascension Day, Whit Monday, Corpus
Christi, Assumption Day, German Unity Day, All Saints' Day, Christmas Day,
St. Stephen's Day.

### 9.3 Time Overlap Detection

The app rejects entries whose time range overlaps with existing entries on
the same date. An error message identifies the conflicting range. When
editing, the entry being edited is excluded from the overlap check.

Additional validations:
- Remote hours cannot exceed net working hours.
- Start time must be before end time (overnight work is supported).

---

## 10. Troubleshooting

**App does not start or crashes on launch**
- Verify that your device runs Android 7.0 (API 24) or later. Open
  **Settings > About Phone** to check your Android version.
- Try clearing the app's cache: **Settings > Apps > Remote Work Tracker >
  Storage > Clear Cache**.

**Data seems lost after an app update**
- Data is stored in SharedPreferences and normally survives updates.
- Use the Restore function (section 6.4) with a previously saved backup.

**PDF import finds no entries**
- The PDF parser only supports SAP TimeStatement (EN) and SAP Zeitnachweis
  (DE) formats.
- Other PDF layouts, scanned documents, or image-based PDFs are not
  supported.
- Ensure the PDF is a digital document (text-selectable), not a scanned
  image.

**Cannot add an entry for a specific date**
- Check whether the date falls on a Sunday or a Bavarian public holiday
  (see section 9.2). These are blocked.
- Saturday entries are also blocked unless approved overtime applies.
- Verify the date format is correct (YYYY-MM-DD).

**Break warning appears on an entry**
- The red warning text indicates that the entered pause is below the
  statutory minimum required by German labor law.
- Edit the entry and increase the pause duration to at least 30 minutes
  (for 6-9h work) or 45 minutes (for 9+ hours work).

**Quota changed unexpectedly**
- The quarterly auto-adjustment may have changed your quota (see section
  7.2). Check the history section for recently closed quarter-end months.

**Progress bar shows red**
- Your actual remote hours have exceeded the maximum allowed for the month.
- Work more days from the office or adjust your quota in Settings if your
  company agreement allows a higher percentage.

**Export or share does not work**
- Ensure you have at least one app that can handle the file type (email
  client, file manager, cloud storage app).

**How to transfer data to a new phone**
1. Old phone: tap Backup and save the JSON file (email, Drive, etc.).
2. New phone: install the app, tap Restore, and select the backup file.

---

*Last updated: March 2026*
