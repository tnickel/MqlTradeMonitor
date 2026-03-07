# Benutzerdokumentation: MQL Trade Monitor

## 1. Einführung

Der **MQL Trade Monitor** ist Ihr zentrales Dashboard zur Überwachung aller Ihrer MetaTrader 5 Konten. Diese Dokumentation führt Sie durch alle Funktionen der Web-Oberfläche.

---

## 2. Erste Schritte

### 2.1 Server starten

1. Starten Sie die Anwendung: `java -jar server/target/trade-monitor-server-0.12.0.jar`
2. Öffnen Sie `http://localhost:8080` im Browser.
3. Melden Sie sich mit Ihren Zugangsdaten an (Standard: admin/password).

### 2.2 MetaTrader 5 verbinden

1. Kopieren Sie `TradeMonitorClient.mq5` in das Verzeichnis `MQL5/Experts/`.
2. Kompilieren Sie den EA im MetaEditor (F7).
3. Unter *Extras > Optionen > Experten* die URL `http://localhost:8080` erlauben.
4. Ziehen Sie den EA auf einen Chart und aktivieren Sie "Auto-Trading".
5. Der Account erscheint nach wenigen Sekunden im Dashboard.

---

## 3. Dashboard (`/`)

Das Dashboard ist die Startseite und bietet einen schnellen Überblick über alle verbundenen Accounts.

### 3.1 Account-Kacheln

Jeder verbundene Account wird als Kachel dargestellt:

- **Status-Indikator**: Farbiger Punkt zeigt den Online-Status an:
  - Grün: Account ist online und aktuell
  - Gelb: Letzte Aktualisierung liegt etwas zurück
  - Orange: Letzte Aktualisierung liegt länger zurück
  - Rot: Account ist offline
- **Metriken**: Balance, Equity, Profit (heute/gesamt) und Anzahl offener Trades.
- **Alarm-Anzeige**: Bei ausgelöstem Open-Profit-Alarm wird die Kachel rot markiert und ein globales Alarm-Banner erscheint oben auf der Seite.
- **Details**: Klick auf die Kachel öffnet die Einzelansicht des Accounts.

### 3.2 Report-Kacheln

Direkt im Dashboard werden tägliche, wöchentliche und monatliche Profit-Charts als eigene Kacheln angezeigt. Diese geben einen schnellen visuellen Überblick über die Performance aller Accounts.

### 3.3 Layout anpassen

Sie können das Dashboard vollständig nach Ihren Wünschen gestalten:

1. Klicken Sie oben rechts auf **"Layout bearbeiten"**.
2. **Sektionen verwalten**:
   - **Neue Sektion**: Klicken Sie auf `+ Neue Sektion`, um einen neuen Bereich zu erstellen.
   - **Umbenennen**: Klicken Sie auf den Titel einer Sektion, um ihn zu ändern.
   - **Löschen**: Klicken Sie auf das Mülleimer-Symbol. Enthaltene Accounts werden in eine andere Sektion verschoben.
3. **Drag & Drop**: Ziehen Sie Account-Kacheln in die gewünschte Sektion oder Reihenfolge.
4. **Speichern**: Klicken Sie auf **"Speichern"**, um die Änderungen dauerhaft zu übernehmen.

---

## 4. Offene Trades (`/open-trades`)

Diese Ansicht zeigt **alle offenen Positionen** aller Accounts in einer Tabelle.

- **Sortierung**: Echtgeld-Konten (REAL) werden priorisiert oben angezeigt.
- **Summen**: Oben sehen Sie die Gesamtsumme von Equity und Profit über alle Accounts.
- **Spalten**: Account-Name, Ticket, Symbol, Typ (Buy/Sell), Volumen, Profit, Swap, Magic Number, Kommentar.
- **Farbkodierung**: Positiver Profit wird grün, negativer rot dargestellt.

---

## 5. Account-Details (`/account/{id}`)

Klicken Sie auf einen Account im Dashboard, um zur Detailansicht zu gelangen.

### 5.1 Übersichts-Chart

Ein überlagerten Chart zeigt:
- **Balance** (linke Y-Achse): Rekonstruiert aus geschlossenen Trades.
- **Equity** (rechte Y-Achse, gelb): Aus gespeicherten Equity-Snapshots.

So erkennen Sie sofort Divergenzen zwischen Balance und Equity.

### 5.2 Offene Positionen

Liste der aktuell offenen Trades nur für diesen Account, inklusive Sync-Status:
- **MATCHED**: Trade ist auf Real- und Demo-Konto synchronisiert.
- **WARNING**: Trade konnte nicht auf dem Gegenkonto gefunden werden.
- **EXEMPTED**: Trade ist vom Sync-Check ausgenommen (orange markiert).

### 5.3 Performance nach Strategie (Magic Number)

Hier sehen Sie, welche Strategien (EAs) am profitabelsten sind:
- **Magic Number Mapping**: Kryptische Nummern können mit lesbaren Namen versehen werden.
- **Profit-Kurven**: Interaktive Charts für jede aktive Magic Number.
- **Metriken**: Offener/geschlossener Profit, Swap, Commission, gehandelte Symbole.
- **Drawdown**: Maximaler Drawdown in EUR und Prozent pro Strategie.

### 5.4 Historie

Liste der geschlossenen Trades mit konfigurierbarem Zeitraum-Filter:
- Heute, 1 Woche, 1 Monat, Dieser Monat, 6 Monate, Dieses Jahr, 1 Jahr, Alles.
- Der gewählte Filter wird pro Account im Browser gespeichert.

---

## 6. Berichte (`/report/{period}`)

Aggregierte Profit-Reports über alle Accounts:
- **Tagesbericht**: `/report/daily`
- **Wochenbericht**: `/report/weekly`
- **Monatsbericht**: `/report/monthly`

Jeder Bericht enthält Charts mit der Performance-Entwicklung und eine tabellarische Aufschlüsselung.

---

## 7. Trade-Vergleich (`/trade-comparison`)

Analyse des Copy-Tradings zwischen Real- und Demo-Accounts:
- **Matching**: Trades werden automatisch zwischen REAL und DEMO zugeordnet.
- **Delay**: Ausführungsverzögerung in Sekunden (Open/Close).
- **Slippage**: Preisabweichung zwischen Real- und Demo-Ausführung.
- **Status**: MATCHED oder NOT FOUND.

---

## 8. Mobile Drawdown-Ansicht (`/mobile/drawdown`)

Optimiert für unterwegs. Zeigt ein Ranking aller aktiven Strategien (Magic Numbers) nach Drawdown-Schwere sortiert:
- Account-Name und Typ (REAL/DEMO)
- Magic Number mit zugewiesenem Namen
- Aktueller Drawdown in EUR und Prozent

---

## 9. Open-Profit-Alarm

Der Open-Profit-Alarm überwacht automatisch Ihre Konten und warnt bei kritischem Drawdown.

### 9.1 Konfiguration (pro Account)

Im Dashboard können Sie für jeden Account einen Alarm konfigurieren:
- **Absoluter Schwellwert**: Alarm bei Open Profit unter einem Betrag (z.B. -5000 EUR).
- **Prozentualer Schwellwert**: Alarm bei Drawdown über einem Prozentsatz der Balance (z.B. 10%).

### 9.2 Alarm-Aktionen

Bei Auslösung:
1. Die Account-Kachel im Dashboard wird rot markiert.
2. Ein globales Alarm-Banner erscheint oben auf der Seite.
3. Eine E-Mail-Benachrichtigung wird versendet (sofern konfiguriert).
4. Die Homey-Sirene wird ausgelöst (sofern konfiguriert).

Der Alarm setzt sich automatisch zurück, sobald die Bedingung nicht mehr zutrifft.

---

## 10. Benutzerprofil (`/profile`)

Hier können Sie Ihr Passwort ändern:
1. Navigieren Sie zu `/profile`.
2. Geben Sie Ihr neues Passwort ein und bestätigen Sie es.
3. Klicken Sie auf "Passwort ändern".

---

## 11. Admin-Bereich (`/admin`)

Der Admin-Bereich ist nur für Benutzer mit der Rolle ROLE_ADMIN zugänglich.

### 11.1 Datenbank-Statistiken

Übersicht über gespeicherte Trades pro Account: Anzahl offener/geschlossener Trades, Datumsbereiche, Gesamtprofit.

### 11.2 Magic Number Mapping

Weisen Sie Magic Numbers lesbare Namen zu:
1. Suchen Sie die Magic Number in der Liste.
2. Geben Sie einen Namen im Feld "Custom Comment" ein.
3. Klicken Sie auf "Speichern".
4. Der Name wird überall im Dashboard angezeigt.

### 11.3 Benutzerverwaltung

- **Benutzer erstellen**: Benutzername, Passwort, Rolle (ADMIN/USER), optionale Account-Berechtigungen.
- **Account-Berechtigungen**: Einschränkung der sichtbaren Accounts pro Benutzer.
- **Benutzer löschen**: Entfernt den Benutzer aus dem System.

### 11.4 Live-Indicator Konfiguration

Konfigurieren Sie die Schwellwerte und Farben für die Online-Status-Anzeige:
- Grüne, gelbe und orange Schwellwerte in Minuten.
- Benutzerdefinierte Farben für jeden Status.

### 11.5 E-Mail-Konfiguration

SMTP-Einstellungen für E-Mail-Benachrichtigungen:
- Host, Port, Benutzer, Passwort
- Absender- und Empfängeradresse
- Tägliches Sendelimit
- Test-E-Mail-Funktion

### 11.6 Homey Smart-Home Integration

Konfiguration der Homey-Webhook-Anbindung für Sirenen-Alarme:
- Homey ID und Event-Name
- Trigger-Quellen (Sync, API)
- Wiederholungen und Alarm-Delay

### 11.7 Sync-Ausnahmen

Magic Numbers die vom automatischen Sync-Check zwischen REAL/DEMO ausgenommen werden sollen (kommagetrennt). Diese Trades erhalten den Status EXEMPTED statt WARNING.

### 11.8 Log-Aufbewahrung

Konfigurierbare Aufbewahrungsdauer für:
- Login-Logs
- Verbindungs-/Request-Logs
- Client-Aktionslogs

### 11.9 Sicherheitseinstellungen

- Rate-Limiting: Anfragen pro Minute (global, per IP)
- Brute-Force-Schutz: Max. Fehlversuche und Sperrdauer
- Security-Headers: Aktivierung von X-Frame-Options, CSP, HSTS etc.
- Max Sessions: Maximale gleichzeitige Sitzungen
- H2-Konsole: Aktivierung/Deaktivierung der Datenbank-Konsole

### 11.10 Logs einsehen

- **Login-Logs** (`/admin/logs`): Authentifizierungsversuche mit Zeitstempel, Benutzer, IP und Ergebnis.
- **Request-Logs** (`/admin/requests`): HTTP-Anfragen neuer IP-Adressen.
- **Client-Logs** (`/admin/client-logs`): MetaTrader-Client-Aktionen (Register, Update, Heartbeat), optional filterbar nach Account.

---

## 12. Häufige Fragen & Problemlösungen

| Problem | Lösung |
|---|---|
| Datenbank beim Start gesperrt | Task Manager: Java-Prozess beenden, neu starten |
| EA kann nicht verbinden | MT5: Extras > Optionen > Experten > Server-URL erlauben |
| Equity-Kurve leer | Erst nach ca. 1 Minute hat der Server genug Snapshots gesammelt |
| Account erscheint nicht | Prüfen ob "Auto-Trading" im MT5 aktiviert ist |
| E-Mail-Alarm kommt nicht | SMTP-Einstellungen im Admin-Bereich prüfen, Test-E-Mail senden |
| Sync-Warnung trotz korrektem Setup | Magic Number unter Sync-Ausnahmen im Admin eintragen |
