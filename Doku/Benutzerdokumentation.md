# MQL Trade Monitor &mdash; Benutzerhandbuch

## 1. Einfuehrung

Der **MQL Trade Monitor** ist eine professionelle Monitoring-Plattform fuer MetaTrader 5 Trading-Konten. Dieses Handbuch fuehrt Sie durch alle Funktionen der Web-Oberflaeche.

**Was die Plattform bietet:**
- Zentrale Echtzeit-Uebersicht ueber alle Ihre MetaTrader-Konten
- Automatische Alarmierung bei kritischem Drawdown (E-Mail, Smart-Home-Sirene, Dashboard)
- Detaillierte Performance-Analyse pro Konto und Strategie (Magic Number)
- Automatischer Abgleich zwischen Real- und Demo-Konten (Sync-Check)
- Copy-Trading-Qualitaetsanalyse (Slippage, Ausfuehrungsverzoegerung)
- Tages-, Wochen- und Monatsberichte
- Vollstaendig konfigurierbar ueber das Admin-Panel

---

## 2. Erste Schritte

### 2.1 Server starten

```bash
java -jar server/target/trade-monitor-server-0.12.0.jar
```

Oeffnen Sie `http://localhost:8080` im Browser und melden Sie sich an.

### 2.2 MetaTrader 5 verbinden

1. Kopieren Sie `TradeMonitorClient.mq5` in das Verzeichnis `MQL5/Experts/`.
2. Kompilieren Sie den EA im MetaEditor (F7).
3. Unter *Extras > Optionen > Experten* die URL `http://localhost:8080` erlauben.
4. Ziehen Sie den EA auf einen Chart und aktivieren Sie "Auto-Trading".

Der Account erscheint nach wenigen Sekunden automatisch im Dashboard. Es ist keine manuelle Konfiguration noetig &mdash; der EA registriert sich selbststaendig.

---

## 3. Dashboard

Das Dashboard (`/`) ist die Startseite und bietet einen sofortigen Ueberblick ueber alle verbundenen Accounts.

### 3.1 Account-Kacheln

Jeder Account wird als Kachel mit folgenden Informationen dargestellt:

- **Live-Status-Indikator** (farbiger Punkt):
  - **Gruen:** Account ist online und aktuell
  - **Gelb:** Letzte Aktualisierung liegt etwas zurueck
  - **Orange:** Letzte Aktualisierung liegt laenger zurueck
  - **Rot:** Account ist offline
  - Die Schwellwerte und Farben sind im Admin-Panel konfigurierbar.
- **Metriken:** Balance, Equity, Tagesprofit, Gesamtprofit, Anzahl offener Trades
- **Alarm-Anzeige:** Bei ausgeloestem Open-Profit-Alarm wird die Kachel rot markiert

### 3.2 Report-Kacheln

Direkt im Dashboard werden kompakte Tages-, Wochen- und Monats-Profit-Charts als eigene Kacheln angezeigt. Diese geben einen schnellen visuellen Ueberblick ueber die Performance aller Accounts.

### 3.3 Layout anpassen

Das Dashboard-Layout ist vollstaendig personalisierbar:

1. Klicken Sie oben rechts auf **"Layout bearbeiten"**.
2. **Sektionen:**
   - *Neue Sektion:* Klicken Sie auf `+ Neue Sektion`.
   - *Umbenennen:* Klicken Sie auf den Sektions-Titel.
   - *Loeschen:* Klicken Sie auf das Muelleimer-Symbol (Accounts werden verschoben).
3. **Drag & Drop:** Ziehen Sie Account-Kacheln in die gewuenschte Sektion oder Reihenfolge.
4. Klicken Sie auf **"Speichern"** &mdash; das Layout wird serverseitig persistiert.

### 3.4 Globales Alarm-Banner
Sollte die Trade-Synchronisation fehlschlagen (z. B. ein Trade wurde auf dem Real-Account geöffnet, aber kein entsprechender MetaTrader-Client gefunden), erscheint oben ein auffälliges rotes Banner: "WARNUNG: Trade Sync Fehler".

### 3.5 System Status & Wartungsmodus
Das Dashboard verfügt über eine permanente System-Status-Kachel, die Auskunft über den Gesundheitszustand des Servers liefert. Befindet sich der Server durch kürzliche Wartungsarbeiten (z.B. Einspielen von Updates auf dem Contabo-Server) im Wartungsmodus, erscheint ein massives rotes Banner **"🔥 WARTUNGSMODUS AKTIV"** auf der Hauptseite. In dieser Zeit bleiben bestehende MetaTrader-Verbindungen unangetastet, jedoch können Daten im Dashboard leicht verzögert dargestellt werden. Die Kachel verfärbt sich passend dazu blau mit der Kennzeichnung "⚙️ Wartungsmodus".

---

## 4. Offene Trades

Die Ansicht `/open-trades` fasst **alle offenen Positionen** aller Accounts in einer Tabelle zusammen.

- **Sortierung:** Echtgeld-Konten (REAL) werden priorisiert oben angezeigt.
- **Gesamtsummen:** Aggregierte Equity und Profit ueber alle Accounts.
- **Spalten:** Account-Name, Ticket, Symbol, Typ (Buy/Sell), Volumen, Profit, Swap, Magic Number, Kommentar.
- **Farbkodierung:** Positiver Profit gruen, negativer rot.

---

## 5. Account-Details

Klicken Sie auf eine Account-Kachel im Dashboard, um zur Detailansicht zu gelangen.

### 5.1 Uebersichts-Chart

Ein ueberlagerten Chart mit zwei Y-Achsen:
- **Balance** (linke Achse, blau): Rekonstruiert aus geschlossenen Trades.
- **Equity** (rechte Achse, gelb): Aus periodischen Equity-Snapshots (1x pro Minute).

Divergenzen zwischen Balance und Equity sind sofort sichtbar.

### 5.2 Offene Positionen

Liste der aktuell offenen Trades mit **Sync-Status**:
- **MATCHED** (gruen): Trade existiert auf Real- und Demo-Konto.
- **WARNING** (rot): Trade nur auf einem Konto gefunden.
- **EXEMPTED** (orange): Magic Number ist vom Sync-Check ausgenommen.

### 5.3 Performance nach Strategie (Magic Number)

Detaillierte Aufschluesselung pro Trading-Strategie:
- **Interaktive Profit-Kurven** pro Magic Number
- **Metriken:** Offener/geschlossener Profit, Swap, Commission, gehandelte Symbole
- **Drawdown:** Maximaler Drawdown in EUR und Prozent
- **Magic Number Mapping:** Kryptische Nummern koennen im Admin-Bereich mit lesbaren Namen versehen werden

### 5.4 Trade-Historie

Chronologische Liste aller geschlossenen Trades mit konfigurierbarem Zeitraum-Filter:

| Filter | Zeitraum |
|---|---|
| Heute | Aktueller Tag |
| 1 Woche | Letzte 7 Tage |
| 1 Monat | Letzte 30 Tage |
| Dieser Monat | Aktueller Kalendermonat |
| 6 Monate | Letzte 180 Tage |
| Dieses Jahr | Aktuelles Kalenderjahr |
| 1 Jahr | Letzte 365 Tage |
| Alles | Gesamte Historie |

Der gewaehlte Filter wird pro Account im Browser gespeichert.

---

## 6. Berichte

Unter `/report/{period}` finden Sie aggregierte Performance-Reports:

- **Tagesbericht** (`/report/daily`): Profit pro Tag
- **Wochenbericht** (`/report/weekly`): Profit pro Woche
- **Monatsbericht** (`/report/monthly`): Profit pro Monat

Jeder Bericht enthaelt interaktive Charts und tabellarische Aufschluesslung pro Account und Magic Number.

---

## 7. Trade-Vergleich (Copy-Trading-Analyse)

Die Ansicht `/trade-comparison` analysiert die Qualitaet des Copy-Tradings zwischen Real- und Demo-Accounts:

- **Automatisches Matching:** Geschlossene Trades werden zwischen REAL und DEMO zugeordnet.
- **Delay-Messung:** Ausfuehrungsverzoegerung in Sekunden (Open/Close).
- **Slippage-Berechnung:** Preisabweichung zwischen Real- und Demo-Ausfuehrung.
- **Status:** MATCHED oder NOT FOUND fuer jeden Trade.

---

## 8. Mobile Drawdown-Ansicht

Unter `/mobile/drawdown` finden Sie eine fuer Smartphones optimierte Ansicht:

- Ranking aller aktiven Strategien (Magic Numbers) nach Drawdown-Schwere
- Account-Name, Typ (REAL/DEMO), Magic Number mit zugewiesenem Namen
- Aktueller Drawdown in EUR und Prozent

Diese Seite ist ohne Login zugaenglich &mdash; ideal fuer schnelle Kontrolle unterwegs.

---

## 9. Open-Profit-Alarm

Das Alarm-System ueberwacht automatisch alle Konten und warnt bei kritischem Drawdown.

### 9.1 Konfiguration (pro Account)

Im Dashboard koennen Sie fuer jeden Account individuelle Alarmgrenzen setzen:

- **Absoluter Schwellwert:** Alarm wenn Open Profit unter Betrag faellt (z.B. -5000 EUR)
- **Prozentualer Schwellwert:** Alarm wenn Drawdown ueber Prozentsatz der Balance steigt (z.B. 10%)

### 9.2 Was passiert bei Alarm?

1. Die Account-Kachel im Dashboard wird rot markiert.
2. Ein globales Alarm-Banner erscheint oben auf der Seite.
3. Eine E-Mail-Benachrichtigung wird versendet (wenn SMTP konfiguriert).
4. Die Homey-Sirene wird ausgeloest (wenn Homey konfiguriert).

Der Alarm setzt sich automatisch zurueck, sobald die Bedingung nicht mehr zutrifft (Latch-Logik).

---

## 10. Benutzerprofil

Unter `/profile` koennen Sie Ihr Passwort aendern:

1. Neues Passwort eingeben und bestaetigen.
2. Klick auf "Passwort aendern".

---

## 11. Admin-Bereich

Der Admin-Bereich (`/admin`) ist nur fuer Benutzer mit der Rolle ADMIN zugaenglich.

### 11.1 Datenbank-Statistiken

Uebersicht pro Account: Anzahl offener/geschlossener Trades, Datumsbereiche, Gesamtprofit.

### 11.2 Magic Number Mapping

Weisen Sie Magic Numbers lesbare Strategie-Namen zu:
1. Magic Number in der Liste finden.
2. Namen im Feld "Custom Comment" eingeben.
3. "Speichern" klicken.

Der Name erscheint ueberall: in Charts, Tabellen, Berichten und der mobilen Ansicht.

### 11.3 Benutzerverwaltung

- **Benutzer erstellen:** Benutzername, Passwort, Rolle (ADMIN/USER).
- **Account-Berechtigungen:** Pro Benutzer festlegen, welche Accounts sichtbar sind.
- **Benutzer loeschen:** Entfernt den Account dauerhaft.

### 11.4 Live-Indicator

Konfigurieren Sie die Schwellwerte (in Minuten) und Farben fuer die Online-Status-Anzeige im Dashboard.

### 11.5 E-Mail-Konfiguration

SMTP-Einstellungen fuer E-Mail-Benachrichtigungen:
- Server (Host, Port), Zugangsdaten (Benutzer, Passwort)
- Absender- und Empfaengeradresse
- Taegliches Sendelimit (verhindert Spam bei Daueralarmen)
- **Test-E-Mail-Funktion** zum Pruefen der Konfiguration

### 11.6 Homey Smart-Home Integration

Konfiguration der Webhook-Anbindung fuer Sirenen-Alarme:
- Homey-ID und Event-Name
- Trigger-Quellen (Sync-Warnung, API)
- Wiederholungen und Alarm-Delay
- **Test-Sirene-Funktion** zum Pruefen der Anbindung

### 11.7 Sync-Ausnahmen

Magic Numbers, die vom automatischen Sync-Check ausgenommen werden sollen (kommagetrennt). Diese Trades erhalten den Status EXEMPTED statt WARNING.

### 11.8 Log-Aufbewahrung

Konfigurierbare Aufbewahrungsdauer fuer verschiedene Log-Typen (Login, Verbindung, Client).

### 11.9 Sicherheitseinstellungen

Alle Security-Parameter sind live konfigurierbar:

| Parameter | Beschreibung |
|---|---|
| Rate-Limiting | Anfragen pro Minute, pro IP |
| Brute-Force-Schutz | Max. Fehlversuche und Sperrdauer in Minuten |
| Security-Headers | CSP, HSTS, X-Frame-Options etc. |
| Max Sessions | Gleichzeitige Sitzungen pro Benutzer |
| H2-Konsole | Datenbank-Konsole aktivieren/deaktivieren |

### 11.10 Logs einsehen
Direkt im Admin-Bereich lassen sich Logs sichten (u. a. `server.log`, Fehlermeldungen von EAs und HTTP-Requests). Diese können gefiltert und nach Datum durchsucht werden, was das Debugging erheblich vereinfacht. Eine Heatmap-Visualisierung hilft zudem, Aktivitäts-Muster der MetaTrader Clients auf einen Blick zu erkennen.

### 11.11 Server Health & Netzwerk-Überwachung
Unter **"Server Health"** bietet TradeMonitor eine tiefgehende Gesundheitsüberwachung der Server-Infrastruktur. 
- **Ressourcen:** Überwachung von RAM-Auslastung, Speicherplatz (Disk), und CPU-Last.
- **Größenstatistiken:** Anzeige detaillierter Größen (H2-Datenbank `trademonitor.mv.db`, WAR-Files).
- **Netzwerk-Verfügbarkeit / Timeline:** Eine optisch ansprechende interaktive Verlaufsanzeige dokumentiert lückenlos, wann der Server "Online", "Offline" oder im "Wartungsmodus" war.
  - Der zeitliche Horizont ("24h", "1W", "1M", "6M") lässt sich nativ in der Oberfläche umschalten.
  - Ein Klick auf *"📋 Detailliertere Infos"* öffnet ein visuell aufbereitetes Popup inklusive stufenlosem Zoom für die Timeline-Log-Ereignisse (inkl. Hover-Tooltips) und chronologischer Tabelle.

---

## 12. Haeufige Fragen & Problemloesungen

| Problem | Loesung |
|---|---|
| Datenbank beim Start gesperrt | Task Manager: Java-Prozess beenden, neu starten |
| EA kann nicht verbinden | MT5: Extras > Optionen > Experten > Server-URL erlauben |
| Equity-Kurve leer | Nach ca. 1 Minute hat der Server genug Snapshots gesammelt |
| Account erscheint nicht | "Auto-Trading" im MT5 aktiviert? EA auf Chart? |
| E-Mail-Alarm kommt nicht | SMTP-Einstellungen im Admin pruefen, Test-E-Mail senden |
| Sync-Warnung obwohl korrekt | Magic Number unter Sync-Ausnahmen im Admin eintragen |
| Dashboard-Layout zurueckgesetzt | Wurde das Layout nach Aenderung gespeichert? |
| Zu viele Alarm-E-Mails | Taegliches Sendelimit im Admin-Panel reduzieren |
