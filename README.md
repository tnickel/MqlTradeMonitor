# Projekt-Dokumentation: MQL Trade Monitor

## 1. Übersicht
Das Projekt **MQL Trade Monitor** ist eine umfassende Lösung zur Überwachung und Analyse von Trading-Konten (MetaTrader 5). Es bietet ein modernes Web-Dashboard zur zentralen Anzeige von Account-Metriken, offenen Positionen und historischen Daten.

Das System besteht aus zwei Hauptkomponenten:
1.  **MQL5 Expert Advisor (Client)**: Läuft im MetaTrader Terminal, exportiert Account-Daten und Trades in Echtzeit an den Server.
2.  **Java Spring Boot Server**: Empfängt die Daten, speichert sie in einer H2-Datenbank und stellt eine interaktive Web-Oberfläche bereit.

---

## 2. Architektur & Komponenten

### 2.1 MQL5 Expert Advisor (`TradeMonitorClient.mq5`)
*   **Funktion**: Überwacht Account-Zustand und Trades. Sendet Daten per HTTP POST an den Server.
*   **Features**:
    *   **Echtzeit-Überwachung**: Sendet Balance, Equity, Margin, und offene Trades.
    *   **Tick-Mode**: Schaltet automatisch auf tick-basierte Überwachung um, wenn ein Drawdown-Limit (50% des Max-Drawdowns) erreicht wird.
    *   **Verbindungsmanagement**: Automatische Reconnects, Heartbeats, und "Not-Aus"-Erkennung.
    *   **Historien-Export**: Überträgt geschlossene Trades zur Analyse.
*   **Konfiguration**:
    *   Server-URL, Update-Intervalle, Magic Number Filter (Whitelist/Blacklist).

### 2.2 Java Spring Boot Server
*   **Technologie**: Java 21, Spring Boot 3, Spring Data JPA, H2 Database, Thymeleaf.
*   **Datenbank**:
    *   **H2 (File-basiert)**: Persistente Speicherung von Accounts, Trades und Historie.
    *   **Tabellen**: `accounts`, `open_trades`, `closed_trades`, `dashboard_sections`.
*   **API Endpoints**:
    *   `/api/register`: Account-Registrierung.
    *   `/api/trades-init` & `/api/trades`: Trade-Synchronisation.
    *   `/api/heartbeat`: Lebenszeichen der Clients.
    *   `/api/section/*`: Management der Dashboard-Sektionen.
    *   `/api/account/layout`: Speichern der Kachel-Anordnung.

### 2.3 Web Dashboard
Das Frontend ist eine server-seitig gerenderte Webanwendung mit dynamischen JavaScript-Funktionen.
*   **Dashboard (`/`)**:
    *   **Dynamisches Layout**: Anpassbare Sektionen (Erstellen, Umbenennen, Löschen).
    *   **Drag & Drop**: Verschieben von Account-Kacheln zwischen Sektionen.
    *   **Echtzeit-Status**: Visuelle Indikatoren für Online/Offline Status.
*   **Offene Trades (`/open-trades`)**:
    *   Dedizierte Ansicht aller offenen Positionen über alle Accounts hinweg.
    *   Aggregierte Summen (Total Equity, Total P/L).
*   **Admin-Bereich (`/admin`)**:
    *   Datenbank-Statistiken pro Account.
    *   **Magic Number Mapping**: Zuweisung von lesbaren Namen zu Magic Numbers.
    *   Globale Konfiguration (z.B. Max Age für Historie).
*   **Account-Details (`/account/{id}`)**:
    *   Detaillierte Analyse eines einzelnen Accounts.
    *   Performance-Charts pro Magic Number.
    *   Historien-Tabelle.

---

## 3. Installation & Start

### 3.1 Server
1.  **Voraussetzung**: Java JDK 21+ und Maven.
2.  **Build**: `mvn clean package`
3.  **Start**: `java -jar target/trade-monitor-server-1.0.0.jar`
4.  URL: `http://localhost:8080`

### 3.2 MetaTrader Client
1.  `TradeMonitorClient.mq5` in `MQL5/Experts/` kopieren und kompilieren.
2.  In MT5 `http://localhost:8080` als erlaubte URL hinzufügen.
3.  EA auf Chart ziehen und konfigurieren.

---

## 4. Datenbank & Speicher
Die Daten werden lokal im Ordner `./data` gespeichert:
*   `trademonitor.mv.db`: H2 Datenbank-Datei.
*   Diese Datei kann für Backups einfach kopiert werden.
*   Zum Reset der Daten: Server stoppen und Datei löschen.

---

## 5. Entwicklung
*   **Backend**: Controller-Service-Repository Pattern.
*   **Frontend**: Thymeleaf Templates (`src/main/resources/templates`).
*   **Styling**: Modernes CSS mit CSS Variables für einfaches Theming (`src/main/resources/static/css/style.css`).





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
