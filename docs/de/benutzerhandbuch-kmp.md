# Benutzerhandbuch — Remote Work Tracker (KMP Android)

**Version 1.0.0**

Dieses Handbuch beschreibt die Bedienung der Android-App "Remote Work Tracker",
einer Kotlin-Multiplatform-Anwendung zur Erfassung und Verwaltung von
Remote-Arbeitsstunden. Die App berechnet monatliche Soll- und Ist-Werte,
prueft die Einhaltung der individuellen Remote-Quote (40-60%) und setzt
die gesetzlichen Pausenregelungen nach dem Arbeitszeitgesetz (ArbZG) durch.
Quartalsweise wird die Quote automatisch angepasst.

---

## Inhaltsverzeichnis

1. [Erste Schritte](#1-erste-schritte)
   - 1.1 Systemanforderungen
   - 1.2 Installation
   - 1.3 Erster Start
2. [Taeglicher Arbeitsablauf](#2-taeglicher-arbeitsablauf)
   - 2.1 Arbeitszeit erfassen
   - 2.2 Eintrag bearbeiten
   - 2.3 Eintrag loeschen
3. [Dashboard verstehen](#3-dashboard-verstehen)
   - 3.1 Monatsuebersicht
   - 3.2 Remote-Stunden Fortschrittsbalken
   - 3.3 Schnellstatistik
   - 3.4 Monatsauswahl
4. [Einstellungen](#4-einstellungen)
   - 4.1 Einstellungen oeffnen
   - 4.2 Remote-Quote aendern
   - 4.3 Soll-Stunden pro Tag
   - 4.4 Speichern
5. [Dunkelmodus und Sprache](#5-dunkelmodus-und-sprache)
   - 5.1 Dunkelmodus
   - 5.2 Sprachumschaltung
6. [Export und Backup](#6-export-und-backup)
   - 6.1 CSV-Export
   - 6.2 PDF-Bericht
   - 6.3 JSON-Backup
   - 6.4 Wiederherstellen
7. [Monatsverwaltung](#7-monatsverwaltung)
   - 7.1 Monat abschliessen
   - 7.2 Quartalsanpassung
   - 7.3 Historie (rote Karten, Abschlussbericht)
   - 7.4 Alles loeschen
8. [PDF-Import](#8-pdf-import)
   - 8.1 PDF importieren
   - 8.2 Import-Vorschau
   - 8.3 Unterstuetzte Formate
9. [Validierung und Arbeitsrecht](#9-validierung-und-arbeitsrecht)
   - 9.1 Pausenregelung (ArbZG)
   - 9.2 Wochenende und Feiertage
   - 9.3 Zeitueberschneidung
   - 9.4 Remote-Stunden-Pruefung
10. [Fehlerbehebung](#10-fehlerbehebung)
    - 10.1 Haeufige Probleme
    - 10.2 Datenwiederherstellung
    - 10.3 Bekannte Einschraenkungen

---

## 1. Erste Schritte

### 1.1 Systemanforderungen

| Anforderung         | Minimum                              |
|---------------------|--------------------------------------|
| Android-Version     | 7.0 (Nougat, API 24) oder hoeher     |
| Speicherplatz       | ca. 50 MB                            |
| Arbeitsspeicher     | 2 GB RAM empfohlen                   |
| Bildschirm          | Beliebige Groesse (responsives Layout)|

Die App benoetigt keine Internetverbindung. Alle Daten werden lokal auf
dem Geraet in den SharedPreferences gespeichert.

### 1.2 Installation

**Variante A: APK direkt installieren (Sideloading)**

1. Die APK-Datei (`androidApp-debug.apk` oder `androidApp-release.apk`)
   auf das Android-Geraet uebertragen (USB, E-Mail, Cloud-Speicher).
2. In den Android-Einstellungen unter "Sicherheit" die Option
   "Unbekannte Quellen" bzw. "Apps aus unbekannten Quellen installieren"
   fuer den verwendeten Dateimanager aktivieren.
3. Die APK-Datei auf dem Geraet oeffnen und "Installieren" tippen.
4. Nach der Installation "Oeffnen" tippen oder die App ueber den
   App-Drawer starten.

**Variante B: Ueber Android Studio bauen**

Voraussetzungen: Android Studio, Android SDK, JDK 17+.

```bash
cd RemoteWorkTrackerKMP
./gradlew :androidApp:installDebug
```

Die App wird automatisch auf dem angeschlossenen Geraet oder Emulator
installiert und kann ueber den App-Drawer gestartet werden.

### 1.3 Erster Start

Beim ersten Start legt die App automatisch einen leeren Zustand an:

- **Remote-Quote**: 40%, **Soll-Stunden/Tag**: 8,0h, **Sprache**: Deutsch, **Dunkelmodus**: Aus
- **Soll-Stunden Gesamt**: Berechnet aus den Arbeitstagen im Monat
  (ohne Wochenenden und bayerische Feiertage)

Das Dashboard zeigt die Monatsuebersicht mit 0,00h Remote-Stunden.

---

## 2. Taeglicher Arbeitsablauf

### 2.1 Arbeitszeit erfassen

1. Den blauen **Plus-Button (+)** unten rechts auf dem Bildschirm tippen.
   Der Dialog "Arbeitszeit erfassen" oeffnet sich.

2. Folgende Felder ausfuellen:

   | Feld                    | Format     | Beispiel   | Pflicht |
   |-------------------------|-----------|------------|---------|
   | Datum                   | YYYY-MM-DD | 2026-03-15 | Ja      |
   | Start                   | HH:MM      | 08:00      | Ja      |
   | Ende                    | HH:MM      | 16:30      | Ja      |
   | Pause (Minuten)         | Zahl       | 30         | Ja      |
   | Remote Stunden          | Dezimalzahl| 6.5        | Nein    |

   **Hinweis zum Feld "Remote Stunden"**: Wenn das Feld leer gelassen
   wird, werden alle Netto-Arbeitsstunden als Remote-Stunden gezaehlt.
   Wenn nur ein Teil der Arbeitszeit remote war, den entsprechenden Wert
   eingeben. Fuer reine Buero-Tage "0" eingeben.

3. "Speichern" tippen. Bei erfolgreicher Validierung erscheint die
   Meldung "Eintrag gespeichert" und der Eintrag wird in der Liste
   angezeigt.

**Standardwerte**: Datum = heute, Start = 08:00, Ende = 16:30,
Pause = 30 Min, Remote = leer (gesamte Arbeitszeit).

### 2.2 Eintrag bearbeiten

1. Das blaue **Stift-Icon** auf dem Eintrag tippen.
2. Dialog oeffnet sich mit vorausgefuellten Werten.
3. Felder aendern und "Speichern" tippen. Alle Validierungen werden
   erneut geprueft (Zeitueberschneidung schliesst den eigenen Eintrag aus).

### 2.3 Eintrag loeschen

1. Das rote **X** auf dem gewuenschten Eintrag tippen.
2. Der Eintrag wird sofort entfernt und die Monatssummen werden
   automatisch neu berechnet.
3. Die Meldung "Eintrag geloescht" erscheint kurz am oberen
   Bildschirmrand.

**Achtung**: Das Loeschen kann nicht rueckgaengig gemacht werden.
Es empfiehlt sich, regelmaessig Backups anzulegen (siehe Abschnitt 6.3).

---

## 3. Dashboard verstehen

### 3.1 Monatsuebersicht

Die obere Karte (lila Hintergrund) zeigt Monat/Jahr (z.B. "Maerz 2026")
sowie Quartal und Quote (z.B. "Q1/2026 | Quote: 40.0%").

### 3.2 Remote-Stunden Fortschrittsbalken

Zeigt den aktuellen Stand der Remote-Stunden als Balken mit
Ist-Wert (links) und Max-Wert (rechts). Darunter die Verbleibend-Karte.

**Farbcodierung:**

| Farbe  | Bedeutung                                    |
|--------|----------------------------------------------|
| Gruen  | Mehr als 10h verbleibend                     |
| Orange | Weniger als 10h verbleibend                  |
| Rot    | Quote ueberschritten                         |

### 3.3 Schnellstatistik

Drei Karten nebeneinander:

| Karte          | Beschreibung                                       |
|----------------|----------------------------------------------------|
| **Soll**       | Soll-Arbeitsstunden im Monat (Arbeitstage x Soll/Tag) |
| **Max Remote** | Maximal erlaubte Remote-Stunden (Soll x Quote%)   |
| **Ist Remote** | Tatsaechlich erfasste Remote-Stunden               |

**Beispiel**: 21 Arbeitstage x 8h = 168h Soll; 40% Quote = 67,2h Max Remote.

### 3.4 Monatsauswahl

Bei Eintraegen in mehreren Monaten erscheinen Auswahl-Chips (z.B.
"Jan 2026", "Feb 2026"). Der aktive Chip ist lila hervorgehoben.
Tippen wechselt die Dashboard-Ansicht.

---

## 4. Einstellungen

### 4.1 Einstellungen oeffnen

Das **Zahnrad-Icon** in der oberen rechten Ecke der Kopfleiste tippen.
Es oeffnet sich ein Einstellungs-Bereich als untere Einblendung
(Bottom Sheet), der ueber die aktuellen Werte gleitet.

### 4.2 Remote-Quote aendern

- **Schieberegler** fuer die Remote-Quote
- Bereich: 40% bis 60%
- Schritte: 5% (verfuegbare Werte: 40%, 45%, 50%, 55%, 60%)
- Der aktuelle Wert wird oberhalb des Schiebereglers als Prozentzahl
  angezeigt (z.B. "Remote-Quote: 45%")

Die Quote bestimmt, welcher Anteil der monatlichen Soll-Arbeitszeit
remote geleistet werden darf. Bei 40% Quote und 168h Soll-Stunden
ergeben sich 67,2h maximal erlaubte Remote-Stunden.

### 4.3 Soll-Stunden pro Tag

- **Textfeld** fuer die taegliche Soll-Arbeitszeit
- Gueltige Werte: 1,0 bis 10,0 Stunden
- Standard: 8,0 Stunden (Vollzeit bei 40-Stunden-Woche)
- Fuer Teilzeitkraefte den entsprechenden Wert eintragen (z.B. 6,0 fuer
  eine 30-Stunden-Woche bei 5 Arbeitstagen)

### 4.4 Speichern

Den "Speichern"-Button tippen. Die Aenderungen werden sofort wirksam:

- Die Soll-Stunden des aktuellen Monats werden neu berechnet
- Die maximal erlaubten Remote-Stunden werden angepasst
- Der Fortschrittsbalken und alle Statistiken aktualisieren sich
- Die Einstellungen bleiben auch nach einem App-Neustart erhalten

---

## 5. Dunkelmodus und Sprache

### 5.1 Dunkelmodus

Das **Mond/Sonnen-Icon** in der Kopfleiste tippen:

- **Halbmond** (im hellen Modus): Wechselt zum dunklen Farbschema
- **Sonne** (im dunklen Modus): Wechselt zurueck zum hellen Farbschema
- Die Einstellung wird gespeichert und beim naechsten Start wiederhergestellt

### 5.2 Sprachumschaltung

Der **DE/EN-Button** in der Kopfleiste wechselt die Sprache:

- Zeigt "EN" im deutschen Modus, "DE" im englischen Modus
- Betrifft alle Beschriftungen, Buttons, Dialoge und Meldungen
- Die Einstellung wird gespeichert

**Hinweis**: Validierungs-Fehlermeldungen (Feiertage, Sonntage,
Pausenregelungen) erscheinen immer auf Deutsch.

---

## 6. Export und Backup

Der Export/Import-Bereich befindet sich unterhalb der Eintragsliste und
enthaelt vier nebeneinander liegende Buttons.

### 6.1 CSV-Export

**Gruener "CSV"-Button** -- Erstellt eine CSV-Datei mit allen Eintraegen
des aktuellen Monats. Dateiname: `Export_[Monat]_[Jahr].csv`.
Oeffnet den Android-Teilen-Dialog (E-Mail, Drive, etc.).

CSV-Spalten: `Datum,Start,Ende,Pause (min),Remote (h),Gesamt (h)`

### 6.2 PDF-Bericht

**Lila "PDF"-Button** -- Generiert einen A4-PDF-Monatsbericht mit
Zusammenfassung (Soll, Ist, Remote, Quote) und Eintragtabelle.
Dateiname: `Monatsbericht_[Monat]_[Jahr].pdf`. Mehrseitig bei vielen
Eintraegen. Oeffnet den Android-Teilen-Dialog.

### 6.3 JSON-Backup

**Blauer "Backup"-Button** -- Exportiert den kompletten App-Zustand
(alle Eintraege, Historie, Einstellungen) als JSON-Datei.
Dateiname: `Backup_[Monat]_[Jahr].json`. Oeffnet den Teilen-Dialog.

**Empfehlung**: Regelmaessig Backups erstellen, besonders vor
Monat-Abschluss oder Datenloeschung.

### 6.4 Wiederherstellen

**Orangener "Wiederherstellen"-Button** -- Oeffnet Dateiauswahl fuer
JSON-Backup. Stellt den kompletten App-Zustand wieder her und
ueberschreibt dabei alle aktuellen Daten.

**Achtung**: Vorher den aktuellen Zustand sichern!

---

## 7. Monatsverwaltung

### 7.1 Monat abschliessen

Orangener "Monat abschliessen"-Button am Seitenende (nur aktiv mit
Eintraegen). Der Monat wird in die Historie archiviert, Eintraege
werden entfernt, und die Ansicht wechselt zum naechsten verfuegbaren
Monat. Bei Quartalsende wird die Quartalsanpassung ausgeloest (7.2).

### 7.2 Quartalsanpassung

Nach Abschluss eines Quartals-Endmonats (Maerz, Juni, September,
Dezember) und wenn alle drei Quartalsmonate in der Historie liegen,
wird die Quote automatisch angepasst:

| Bedingung                | Aktion                                 |
|--------------------------|----------------------------------------|
| Ist-Remote > Max-Remote  | Quote -10% (min. 40%)                  |
| Ist-Remote <= Max-Remote | Quote +10% (max. 60%)                  |

Ein Hinweis-Dialog informiert ueber die neue Quote.

### 7.3 Historie

Abgeschlossene Monate erscheinen im Historie-Bereich, **nach Jahr und Monat sortiert (neueste zuerst)**.
Jede Karte zeigt: Monat/Jahr, Remote Ist/Max, tatsaechliche Quote.

**Rote Karte:** Hat ein Monat mehr Remote-Stunden als sein Limit (Remote Ist groesser als Max), ist die
Karte **rot hinterlegt** (im hellen und im dunklen Modus). Monate im Limit bleiben unveraendert.

### 7.3.1 Abschlussbericht

Der Knopf **Abschlussbericht** neben der Ueberschrift "Historie" oeffnet einen Bericht ueber alle
abgeschlossenen Monate, **geordnet nach Quartalen** (aeltestes Quartal oben):

- Je Quartal: Remote-Stunden, Limit (Summe der Monats-Limits), Auslastung in Prozent, tatsaechliche
  Quote, Anzahl der Monate ueber dem Limit und die Monate des Quartals (rote Zeilen = ueber dem Limit).
- **Bewertung** je Quartal: bis 90 % des Limits **Gut** (gruen), bis 100 % **Knapp eingehalten** (gelb),
  darueber **Ueberschritten** (rot).
- Ein Quartal mit weniger als 3 abgeschlossenen Monaten ist als **unvollstaendig** gekennzeichnet.
- Oben steht die **Gesamtbewertung** ueber alle Quartale. Der Bericht betrachtet nur abgeschlossene
  Monate, nicht den laufenden.

### 7.4 Alles loeschen

Roter "Alles loeschen"-Button setzt die App komplett zurueck: alle
Eintraege, Historie, Quote (40%), Soll-Stunden (8,0h).

**Achtung**: Nicht rueckgaengig machbar. Vorher Backup erstellen!

---

## 8. PDF-Import

### 8.1 PDF importieren

1. Den lila **"PDF"-Button (FAB)** oben im Bereich der schwebenden
   Aktions-Buttons tippen (oberhalb des blauen Plus-Buttons)
2. Der Android-Dateiauswahl-Dialog oeffnet sich und zeigt nur
   PDF-Dateien an
3. Die gewuenschte SAP-Zeitnachweis-PDF auswaehlen
4. Die App analysiert die PDF und extrahiert alle erkannten
   Arbeitszeit-Eintraege

### 8.2 Import-Vorschau

Nach erfolgreicher Analyse oeffnet sich ein Vorschau-Dialog:

- **Anzahl gefundener Eintraege** wird oben angezeigt
- **Eintragsliste** zeigt alle erkannten Eintraege:
  - Datum, Startzeit und Endzeit pro Eintrag
  - **Blaue Karten**: Remote-Eintraege (erkannt durch Schluesselwoerter)
  - **Graue Karten**: Buero-/Praesenz-Eintraege
- **Hinweise/Fehler**: Falls vorhanden, werden diese unterhalb der
  Liste in orange angezeigt
- "Alle importieren"-Button uebernimmt saemtliche Eintraege
- "Abbrechen"-Button schliesst den Dialog ohne Import

**Nach dem Import:**
- Erfolgsmeldung zeigt die Anzahl importierter Eintraege und
  Remote-Eintraege (z.B. "12 Eintraege importiert (8 Remote)")
- Fehlerhafte Eintraege (z.B. Feiertage, Ueberschneidungen) werden
  uebersprungen und deren Anzahl angezeigt
- Die Monatsansicht wechselt automatisch zum Monat der importierten
  Eintraege
- Importierte Eintraege zeigen **keine Pausenwarnung**: Ein Zeitnachweis enthaelt
  keine Pausen, die Pause wird mit 0 gespeichert. Manuell erfasste Eintraege
  werden weiter auf die gesetzliche Pause geprueft.

### 8.3 Unterstuetzte Formate

Die App erkennt folgende PDF-Formate:

**SAP TimeStatement (Englisch)**
- Kopfzeile: "from DD.MM.YYYY to DD.MM.YYYY"
- Wochentage: MO, TU, WE, TH, FR, SA, SU
- Remote-Erkennung: "Remote Work", "Remote"

**SAP Zeitnachweis (Deutsch)**
- Kopfzeile: "vom DD.MM.YYYY bis DD.MM.YYYY"
- Wochentage: MO, DI, MI, DO, FR, SA, SO
- Remote-Erkennung: "Telearbeit", "Home Office", "Homeoffice"

**Allgemeine Formate (Fallback):**
DD.MM.YYYY oder YYYY-MM-DD mit HH:MM-Zeitangaben. Fuer importierte
Eintraege werden automatisch 30 Minuten Pause angenommen.

---

## 9. Validierung und Arbeitsrecht

Die App implementiert mehrere Validierungsregeln, die bei jedem neuen
Eintrag und jeder Bearbeitung automatisch geprueft werden.

### 9.1 Pausenregelung (ArbZG)

Gemaess dem deutschen Arbeitszeitgesetz (ArbZG, Paragraf 4) gelten folgende
Mindest-Pausenzeiten:

| Brutto-Arbeitszeit     | Mindestpause   |
|------------------------|----------------|
| Unter 6 Stunden        | Keine Pflicht  |
| 6 bis unter 9 Stunden  | 30 Minuten     |
| 9 Stunden und mehr     | 45 Minuten     |

**Verhalten der App:**
- Die Brutto-Arbeitszeit wird aus Start- und Endzeit berechnet
- Ist die eingetragene Pause geringer als das gesetzliche Minimum,
  wird eine **Warnung** angezeigt (roter Text auf der Eintragskarte)
- Der Eintrag wird trotzdem gespeichert (Warnung, keine Blockierung)
- Die Warnung bleibt auf der Eintragskarte sichtbar

**Beispiel-Warnung:**
"WARNUNG: Pause (0.25h) < gesetzl. Minimum (0.50h)!"

### 9.2 Wochenende und Feiertage

Die App unterscheidet drei Faelle:

| Tag            | Verhalten                                           |
|----------------|-----------------------------------------------------|
| **Sonntag**    | Eintrag wird **blockiert** mit Fehlermeldung         |
| **Feiertag**   | Eintrag wird **blockiert** mit Fehlermeldung         |
| **Samstag**    | Eintrag wird **blockiert** mit Hinweis auf Sonderarbeit |

**Hinterlegte Feiertage (Bayern, 2025-2030):**
Neujahr, Heilige Drei Koenige, Karfreitag, Ostermontag, Tag der Arbeit,
Christi Himmelfahrt, Pfingstmontag, Fronleichnam, Maria Himmelfahrt,
Tag der Deutschen Einheit, Allerheiligen, 1. und 2. Weihnachtstag.

**Hinweis**: Fuer Jahre ab 2027 ist ein App-Update erforderlich.

### 9.3 Zeitueberschneidung

Das System prueft bei jedem neuen Eintrag, ob sich die eingegebene
Zeitspanne mit einem bestehenden Eintrag am gleichen Tag ueberschneidet.

- Ueberlappende Eintraege werden **abgelehnt**
- Die Fehlermeldung zeigt die kollidierende Zeitspanne:
  "Zeitueberlappung erkannt! HH:MM-HH:MM ueberschneidet sich."
- Beim Bearbeiten eines Eintrags wird der bearbeitete Eintrag selbst
  von der Pruefung ausgeschlossen

**Beispiel**: Ein bestehender Eintrag von 08:00-12:00 blockiert einen
neuen Eintrag von 11:00-16:00, da sich die Zeiten zwischen 11:00 und
12:00 ueberschneiden.

### 9.4 Remote-Stunden-Pruefung

Die eingegebenen Remote-Stunden duerfen die Netto-Arbeitszeit nicht
uebersteigen:

- Netto = Brutto-Arbeitszeit minus Pause
- Remote-Stunden > Netto: Eintrag wird abgelehnt
- Fehlermeldung: "Remote-Stunden (X h) > Netto (Y h)"

---

## 10. Fehlerbehebung

### 10.1 Haeufige Probleme

| Problem                           | Loesung                                           |
|-----------------------------------|---------------------------------------------------|
| App startet nicht                 | Android 7.0+ erforderlich; Cache leeren           |
| Eintrag nicht moeglich            | Pruefen: Wochenende/Feiertag? Zeitueberschneidung? Format YYYY-MM-DD / HH:MM? |
| Fortschrittsbalken rot            | Max Remote aufgebraucht; Quote in Einstellungen pruefen |
| PDF-Import ohne Ergebnis          | Nur SAP-PDFs mit maschinenlesbarem Text unterstuetzt |
| Teilen-Dialog fehlt               | Mindestens eine Teilen-App muss installiert sein   |

### 10.2 Datenwiederherstellung

- JSON-Backup vorhanden: "Wiederherstellen"-Button nutzen (Abschnitt 6.4)
- Kein Backup: Daten koennen nicht wiederhergestellt werden
- **Empfehlung**: Vor Monat-Abschluss und vor "Alles loeschen" immer
  ein Backup erstellen; regelmaessig Backup per E-Mail senden

### 10.3 Bekannte Einschraenkungen

- Feiertage nur fuer Bayern 2025-2030 hinterlegt
- Einzelbenutzer-App ohne Cloud-Sync oder Passwortschutz
- Kein automatisches Backup (nur manuell)
- Nachtarbeit (ueber Mitternacht) wird korrekt berechnet
- Offline-only, kein Abgleich mit Unternehmenssystemen

---

## Glossar

| Begriff                | Beschreibung                                              |
|------------------------|-----------------------------------------------------------|
| **ArbZG**              | Arbeitszeitgesetz - deutsches Bundesgesetz zur Regelung der Arbeitszeit |
| **Brutto-Arbeitszeit** | Zeitspanne von Arbeitsbeginn bis Arbeitsende (ohne Pausenabzug) |
| **Netto-Arbeitszeit**  | Brutto-Arbeitszeit abzueglich Pausenzeit                  |
| **Remote-Quote**       | Prozentualer Anteil der Arbeitszeit, der remote geleistet werden darf |
| **Soll-Stunden**       | Zu leistende Arbeitszeit pro Monat (Arbeitstage x Stunden/Tag) |
| **Max-Remote**         | Maximal erlaubte Remote-Stunden (Soll x Quote%)           |
| **Ist-Remote**         | Tatsaechlich erfasste Remote-Stunden im aktuellen Monat   |
| **Quartal**            | Dreimonatszeitraum (Q1-Q4) fuer die automatische Quotenanpassung |
| **SharedPreferences**  | Android-Speichermechanismus fuer App-Daten auf dem Geraet  |
| **Sideloading**        | Installation einer App aus einer APK-Datei statt ueber einen App Store |

---

Zuletzt aktualisiert: Maerz 2026
