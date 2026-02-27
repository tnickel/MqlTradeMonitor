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



### 6.2 MetaTrader verbinden
1.  Öffnen Sie MetaTrader 5.
2.  Stellen Sie sicher, dass WebRequests für `http://localhost:8080` erlaubt sind (Extras -> Optionen -> Experten).
3.  Ziehen Sie den `TradeMonitorClient` EA auf einen Chart.
4.  Der Account erscheint nach wenigen Sekunden automatisch im Dashboard.
<img width="1844" height="1641" alt="Tagesreport" src="https://github.com/user-attachments/assets/203d6278-bebe-47d1-8aac-7e719ca9cd62" />
<img width="1895" height="1387" alt="SyncCheck" src="https://github.com/user-attachments/assets/934efe34-34d1-4a71-884d-396cbd5e68db" />
<img width="2068" height="1181" alt="OverviewInAdminconsole" src="https://github.com/user-attachments/assets/1009b7e1-2f8c-4f6f-aeb2-926b7c880494" />
<img width="2611" height="1757" alt="Overview" src="https://github.com/user-attachments/assets/1400e350-a1e9-4e18-976a-ca2ca1cbf161" />
<img width="932" height="456" alt="Metatrader5 integration" src="https://github.com/user-attachments/assets/14867e8c-f429-4c37-9cd5-122a362e6191" />
<img width="782" height="612" alt="login" src="https://github.com/user-attachments/assets/0b6940bd-0185-41e9-9863-0ee88e952bd4" />
<img width="2056" height="602" alt="HomeyIntegration" src="https://github.com/user-attachments/assets/0d0e38d3-99f7-49f4-a28d-3473aeacbaac" />
<img width="1361" height="1452" alt="DrawdownMonitor" src="https://github.com/user-attachments/assets/e2e9c170-2b88-49a7-afbf-cb30a6f49019" />
<img width="2178" height="1440" alt="AdminArea" src="https://github.com/user-attachments/assets/b8743339-a6d3-4483-a222-21ac0587d59a" />
<img widt<img width="1844" height="1641" alt="Tagesreport" src="https://github.com/user-attachments/assets/442f66e4-7188-4d0c-8e33-e43296a609c5" />
<img width="1895" height="1387" alt="SyncCheck" src="https://github.com/user-attachments/assets/8aeeed64-249d-420e-ad09-3479fd3c5903" />
<img width="2068" height="1181" alt="OverviewInAdminconsole" src="https://github.com/user-attachments/assets/b7704f9c-afb6-42af-8b74-7cf9c674e637" />
<img width="2611" height="1757" alt="Overview" src="https://github.com/user-attachments/assets/e95dd7fa-457c-4c07-908d-988acb68690a" />
<img width="932" height="456" alt="Metatrader5 integration" src="https://github.com/user-attachments/assets/a18e2d12-262b-46b3-97bc-467129c5f477" />
<img width="782" height="612" alt="login" src="https://github.com/user-attachments/assets/ec23118d-aa92-401f-bf1c-cd0d54bb0e41" />
<img width="2056" height="602" alt="HomeyIntegration" src="https://github.com/user-attachments/assets/a0b91fe1-b8ef-444d-aa10-6be93d0a66af" />
<img width="1361" height="1452" alt="DrawdownMonitor" src="https://github.com/user-attachments/assets/c9f3524b-3faf-4618-b08e-c335bf7a8125" />
<img width="2178" height="1440" alt="AdminArea" src="https://github.com/user-attachments/assets/ed7445fd-769b-4239-8808-928a850e180b" />
<img width="3715" height="1550" alt="AccountDetailEquityDetail" src="https://github.com/user-attachments/assets/b026c5f6-9070-4679-aee9-73bb3e345294" />
<img width="3694" height="1664" alt="AccountDetail3" src="https://github.com/user-attachments/assets/18194ae8-8fd9-451f-9284-30466231e0eb" />
<img width="3823" height="1692" alt="AccountDetail2" src="https://github.com/user-attachments/assets/0acebcc5-c9c6-45ab-a969-4f9ad95b18a7" />
<img width="3827" height="1688" alt="AccountDetail1" src="https://github.com/user-attachments/assets/2006583e-f380-4859-b851-68e45adb8987" />
h="3715" height="1550" alt="AccountDetailEquityDetail" src="https://github.com/user-attachments/assets/2afea686-2f8b-4d70-b473-d93d22d20072" />
<img width="3694" height="1664" alt="AccountDetail3" src="https://github.com/user-attachments/assets/aaf48926-f7a3-4356-96eb-2939b264546b" />
<img width="3823" height="1692" alt="AccountDetail2" src="https://github.com/user-attachments/assets/cd8512b4-88ce-4d56-a3d3-0b26c15d93d2" />
<img width="3827" height="1688" alt="AccountDetail1" src="https://github.com/user-attachments/assets/ae839f1d-463f-4315-996a-f6034d3ba9a0" />
