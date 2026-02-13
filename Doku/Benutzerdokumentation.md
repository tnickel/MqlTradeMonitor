# Benutzerdokumentation: MQL Trade Monitor

## 1. Einführung
Der **MQL Trade Monitor** ist Ihr zentrales Dashboard zur Überwachung aller Ihrer MetaTrader 5 Konten. Diese Dokumentation führt Sie durch die Funktionen der Web-Oberfläche.

---

## 2. Dashboard (`/`)

Das Dashboard ist die Startseite und bietet einen schnellen Überblick über alle verbundenen Accounts.

### 2.1 Account-Kacheln
Jeder verbundene Account wird als Kachel dargestellt:
*   **Status-Badge**: Zeigt "ONLINE" (grün) oder "OFFLINE" (grau) an.
*   **Metriken**: Balance, Equity, Profit (heute/gesamt) und Anzahl offener Trades.
*   **Details**: Klick auf "Details anzeigen" öffnet die Einzelansicht des Accounts.

### 2.2 Layout anpassen (Neu!)
Sie können das Dashboard vollständig nach Ihren Wünschen gestalten.
1.  Klicken Sie oben rechts auf **"Layout bearbeiten"**.
2.  **Sektionen verwalten**:
    *   **Neue Sektion**: Klicken Sie ganz unten auf `+ Neue Sektion`, um einen neuen Bereich zu erstellen.
    *   **Umbenennen**: Klicken Sie auf den Titel einer Sektion (z.B. "HAUPTBEREICH"), um ihn zu ändern.
    *   **Löschen**: Klicken Sie auf das Mülleimer-Symbol neben dem Titel. *Achtung: Enthaltene Accounts werden in eine andere Sektion verschoben.*
3.  **Drag & Drop**:
    *   Ziehen Sie Account-Kacheln mit der Maus in die gewünschte Sektion oder Reihenfolge.
4.  **Speichern**: Klicken Sie oben rechts auf **"Speichern"**, um Ihre Änderungen dauerhaft zu übernehmen.

---

## 3. Offene Trades (`/open-trades`)

Diese Ansicht fasst **alle offenen Positionen** aller Accounts in einer Tabelle zusammen.
*   **Sortierung**: Echtgeld-Konten werden priorisiert oben angezeigt.
*   **Summen**: Oben sehen Sie die Gesamtsumme von Equity und Profit über alle Accounts hinweg.
*   **Spalten**: Account-Name, Ticket, Symbol, Typ (Buy/Sell), Volumen, Profit, Kommentar.
    *   *Positiver Profit wird grün, negativer rot dargestellt.*

---

## 4. Account-Details

Klicken Sie auf einen Account im Dashboard, um zur Detailansicht zu gelangen.

### 4.1 Offene Positionen
Liste der aktuell offenen Trades nur für diesen Account.

### 4.2 Performance nach Strategie (Magic Number)
Hier sehen Sie, welche Strategien (EAs) am profitabelsten sind.
*   **Mapping**: Magic Numbers können im Admin-Bereich benannt werden (z.B. "Gold Scalper" statt "12345").
*   **Charts**: Interaktive Gewinnkurven für jede aktive Magic Number.

### 4.3 Historie
Liste der geschlossenen Trades.
*   **Filter**: Oben rechts können Sie den Zeitraum wählen (Heute, Gestern, 7 Tage, 30 Tage, Alles).

---

## 5. Admin-Bereich (`/admin`)

Hier verwalten Sie globale Einstellungen und Metadaten.

### 5.1 Magic Number Mapping
Weisen Sie kryptischen Magic Numbers lesbare Namen zu.
*   Suchen Sie die Magic Number in der Liste.
*   Geben Sie einen Namen in das Feld "Custom Comment" ein.
*   Klicken Sie auf "Speichern".
*   *Dieser Name wird nun überall im Dashboard (Charts, Tabellen) angezeigt.*

### 5.2 Konfiguration
*   **Magic Number Max Age**: Legt fest, wie viele Tage zurück die Gewinnkurven berechnet werden (Standard: 30 Tage).

### 5.3 Datenbank-Stats
Technische Übersicht über die gespeicherten Trades pro Account (für Diagnosezwecke).

---

## 6. Installation & Erste Schritte

### 6.1 Server starten
Starten Sie die Anwendung auf Ihrem Server/PC. Das Dashboard ist standardmäßig unter `http://localhost:8080` erreichbar.

### 6.2 MetaTrader verbinden
1.  Öffnen Sie MetaTrader 5.
2.  Stellen Sie sicher, dass WebRequests für `http://localhost:8080` erlaubt sind (Extras -> Optionen -> Experten).
3.  Ziehen Sie den `TradeMonitorClient` EA auf einen Chart.
4.  Der Account erscheint nach wenigen Sekunden automatisch im Dashboard.
