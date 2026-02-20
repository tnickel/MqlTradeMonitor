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
    *   **Report-Kacheln**: Tägliche, wöchentliche und monatliche Profit-Charts direkt auf dem Dashboard.
    *   **Echtzeit-Status**: Visuelle Indikatoren für Online/Offline Status.
*   **Offene Trades (`/open-trades`)**:
    *   Dedizierte Ansicht aller offenen Positionen über alle Accounts hinweg.
    *   Aggregierte Summen (Total Equity, Total P/L).
*   **Admin-Bereich (`/admin`)**:
    *   Datenbank-Statistiken pro Account.
    *   **Magic Number Mapping**: Zuweisung von lesbaren Namen zu Magic Numbers.
    *   **Sync-Ausnahmen**: Ausnehmen bestimmter EAs vom Sync-Check (Status EXEMPTED).
    *   Globale Konfiguration (z.B. Max Age für Historie).
*   **Account-Details (`/account/{id}`)**:
    *   Detaillierte Analyse eines einzelnen Accounts.
    *   **Übersichts-Chart**: Überlagerter Chart für Balance (linke Y-Achse) und Equity (rechte Y-Achse, gelb).
    *   Performance-Charts pro Magic Number.
    *   Historien-Tabelle.
*   **Mobile Ansicht (`/mobile/drawdown`)**:
    *   Ranking aller Accounts nach höchstem Drawdown.

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
