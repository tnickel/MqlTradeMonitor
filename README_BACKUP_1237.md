<div align="center">
  <h1>📈 MQL Trade Monitor</h1>
  <p>
    <strong>Eine professionelle Komplettlösung zur Überwachung, Aggregation und Analyse von MetaTrader 5 Trading-Konten in Echtzeit.</strong>
  </p>
</div>

<br />

## 1. Übersicht

Das Projekt **MQL Trade Monitor** ist eine umfassende und skalierbare Lösung für Trader und Entwickler zur zentralen Überwachung und tiefgreifenden Analyse von MetaTrader 5-Konten. Es bietet ein modernes, dynamisches Web-Dashboard, um Account-Metriken, detaillierte Portfoliodaten, offene Positionen und historische Trade-Daten in Echtzeit zu visualisieren. 

Das System ist flexibel erweiterbar und besticht durch eine saubere Trennung von Datenerfassung (Client) und Datenverarbeitung (Backend). Es besteht aus zwei Hauptkomponenten:

1. **MQL5 Expert Advisor (Client)**: Ein leichtgewichtiger, robuster EA, der direkt im MetaTrader 5 Terminal läuft und kritische Account-Daten sowie offene/geschlossene Trades sicher und performant in Echtzeit an den Server exportiert.
2. **Java Spring Boot Server (Backend)**: Eine hochperformante Backend-Architektur, die eingehende Datenströme verarbeitet, validiert, in einer integrierten H2-Datenbank speichert und über ein interaktives Web-Dashboard zur Verfügung stellt.

---

## 2. Architektur & Komponenten

Die Architektur ist nach modernen Best Practices aufgebaut und garantiert Stabilität, Datensicherheit sowie eine hohe Nutzerfreundlichkeit.

### 2.1 MQL5 Expert Advisor (`TradeMonitorClient.mq5`)

Der Client agiert als ausfallsicherer Sensor innerhalb der Handelsplattform.
*   **Funktion**: Lückenlose Überwachung des Account-Zustands und der Trade-Aktivitäten. Datenübertragung per verifiziertem HTTP POST an die Server-API.
*   **Kern-Features**:
    *   **Echtzeit-Überwachung**: Kontinuierliches Streaming von Balance, Equity, Margin und aktuellen offenen Trades.
    *   **Intelligenter Tick-Mode**: Schaltet automatisch auf eine hochauflösende, tick-basierte Überwachung um, sobald ein definiertes Drawdown-Limit (z. B. 50 % des maximalen Drawdowns) unterschritten wird, um kritische Phasen mit maximaler Präzision zu tracken.
    *   **Verbindungs-Management**: Implementierte Ausfallsicherheit durch automatische Reconnects, regelmäßige Heartbeats (Lebenszeichen) sowie eine "Not-Aus"-Erkennung bei Systemfehlern.
    *   **Historien-Export**: Vollständige und strukturierte Übertragung geschlossener Trades für tiefgehende Performance-Analysen.
*   **Konfiguration**: Der EA bietet weitreichende Einstellungsmöglichkeiten, z. B. Server-URLs, maßgeschneiderte Update-Intervalle sowie Whitelisting/Blacklisting über Magic Numbers.

### 2.2 Java Spring Boot Server

Das Herzstück der Datenverarbeitung und -aufbereitung.
*   **Technologie-Stack**: Java 21 LTS, Spring Boot 3, Spring Data JPA, H2 Database (eingebettet), Thymeleaf.
*   **Datenbankschicht**:
    *   **H2 (File-basiert)**: Robuste, persistente und wartungsarme Speicherung von Accounts, Trades und Historien-Datenpaketen.
    *   **Relationale Tabellen**: Strukturiertes Schema (`accounts`, `open_trades`, `closed_trades`, `dashboard_sections`) für schnelle Abfragen.
*   **API-Endpoints (Auszug)**:
    *   `/api/register`: Sichere Account-Registrierung und Initialisierung.
    *   `/api/trades-init` & `/api/trades`: Deltabasierte und vollständige Trade-Synchronisation.
    *   `/api/heartbeat`: Monitoring der Client-Erreichbarkeit (Watchdog).
    *   `/api/section/*` & `/api/account/layout`: Flexibles Management des User-Interfaces und Speicherung des Layout-Zustands.

### 2.3 Interaktives Web Dashboard

Das Frontend ist eine Server-Side-Rendered Webanwendung (Thymeleaf), ergänzt durch reaktive JavaScript-Module zur dynamischen Datendarstellung.

*   **Zentrales Dashboard (`/`)**:
    *   **Dynamisches Kachel-Layout**: Individuell anpassbare Sektionen (Erstellen, Umbenennen, Löschen).
    *   **Drag & Drop**: Nahtloses Verschieben von Account-Kacheln via Drag-and-Drop.
    *   **Integrierte Report-Kacheln**: Visuelle Aufbereitung als tages-, wochen- und monatsbasierte Profit-Charts direkt im Startbildschirm.
    *   **Echtzeit-Indikatoren**: Sofortige visuelle Rückmeldung zum Online-/Offline-Status der angebundenen Terminals.
*   **Offene Trades (`/open-trades`)**:
    *   Konsolidierte Listenansicht aller offenen Positionen skalierend über *alle* Accounts hinweg.
    *   Aggregierte Portfolio-Summen (Gesamt-Equity, Gesamt-Profit/Loss).
*   **Admin-Kontrollzentrum (`/admin`)**:
    *   Tiefgehende Datenbank-Statistiken pro Account.
    *   **Magic Number Mapping**: Benutzerfreundliches Umbenennen von kryptischen Magic Numbers in lesbare Strategie-Namen.
    *   **Sync-Ausnahmen**: Gezieltes Ausnehmen bestimmter EAs vom strengen Sync-Check (Status: `EXEMPTED`), um False-Positives zu vermeiden.
    *   Zentrale Konfiguration (z. B. Max Age für die Vorhaltung historischer Daten).
*   **Detaillierte Account-Analyse (`/account/{id}`)**:
    *   Tiefer Drilldown auf Einzel-Account-Ebene.
    *   **Übersichts-Chart**: Innovativer, überlagerter Chart für Balance (linke Y-Achse) und Equity (rechte Y-Achse), um Divergenzen sofort zu erkennen.
    *   **Aufgeschlüsselte Performance**: Dedizierte Profit-Kurven pro Magic Number inkl. offener (schwebender) Equity.
    *   Lückenlose Historien-Tabelle.
*   **Mobile Optimierung (`/mobile/drawdown`)**:
    *   Responsive Ansicht für unterwegs mit Ranking der kritischsten Konten (Sortierung nach Drawdown).

---

## 3. Installation & Start

Die Inbetriebnahme des Systems ist dank bewährter Standard-Tools einfach und effizient gestaltet.

### 3.1 Server-Backend
1.  **Systemvoraussetzungen**: Java JDK 21 oder neuer, Maven (`mvn`).
2.  **Kompilieren (Build)**: 
    ```bash
    mvn clean package
    ```
3.  **Applikationsstart**: 
    ```bash
    java -jar target/trade-monitor-server-1.0.0.jar
    ```
4.  **Zugriff**: Im Browser unter `http://localhost:8080` aufrufen.

### 3.2 MetaTrader 5 Client
1.  Die Datei `TradeMonitorClient.mq5` in das Terminal-Verzeichnis `MQL5/Experts/` kopieren.
2.  Den Quelltext im MetaEditor fehlerfrei kompilieren (F7).
3.  Im MetaTrader 5 Terminal unter *Extras -> Optionen -> Experten* die URL `http://localhost:8080` zu den erlaubten WebRequest-URLs hinzufügen.
4.  Den EA per Drag & Drop auf einen beliebigen Chart ziehen, Parameter prüfen und "Auto-Trading" aktivieren.
5.  Der Account registriert sich automatisch und erscheint binnen weniger Sekunden auf dem Web-Dashboard.

---

## 4. Datenspeicherung & Backup

Die Anwendung legt den Fokus auf einfache Handhabung (Zero-Config-Database):
*   Alle operativen Daten werden lokal im Ordner `./data` relativ zum Server-Startpunkt gespeichert.
*   Die Hauptdatei `trademonitor.mv.db` (H2 Datenbank) kapselt sämtliche Informationen.
*   **Backup**: Für Backups muss lediglich der Server gestoppt und die `.db` Datei weggesichert werden.
*   **Reset**: Um das System in den Werkszustand zu versetzen, Server stoppen und die Datenbankdatei löschen.

---

## 5. Software-Engineering & Entwicklung
<<<<<<< HEAD
=======

Das Projekt nutzt etablierte Architektur- und Design-Patterns, um Wartbarkeit und Skalierbarkeit für Entwickler zu gewährleisten:

*   **Backend-Struktur**: Strikt getrennt nach dem Controller-Service-Repository Pattern. Entkoppelte Geschäftslogik für hohe Testbarkeit.
*   **Frontend-Architektur**: Verwendung performanter Thymeleaf Templates (`src/main/resources/templates`) in Kombination mit modernem Vanilla-JavaScript.
*   **Styling**: Ein eigens entwickeltes, reaktives Design-System (`src/main/resources/static/css/style.css`), das tiefgreifend auf CSS-Variablen basiert, um ein nahtloses Theming (Dark Mode per Default) und eine erstklassige User Experience (UX) zu bieten.

---

## 6. Galerie / Screenshots

*(Einblicke in die Benutzeroberfläche und die umfassenden Überwachungsfunktionen)*

<details>
<summary><strong>Bildergalerie anzeigen</strong> (Klick zum Erweitern)</summary>
<br />

### Dashboard & Startseite
<img width="2611" alt="Overview" src="https://github.com/user-attachments/assets/1400e350-a1e9-4e18-976a-ca2ca1cbf161" />

### Account Details & tiefgehende Analyse
<img width="3827" alt="AccountDetail1" src="https://github.com/user-attachments/assets/2006583e-f380-4859-b851-68e45adb8987" />
<img width="3823" alt="AccountDetail2" src="https://github.com/user-attachments/assets/0acebcc5-c9c6-45ab-a969-4f9ad95b18a7" />
<img width="3694" alt="AccountDetail3" src="https://github.com/user-attachments/assets/18194ae8-8fd9-451f-9284-30466231e0eb" />
<img width="3715" alt="AccountDetailEquityDetail" src="https://github.com/user-attachments/assets/b026c5f6-9070-4679-aee9-73bb3e345294" />

### Mobile Drawdown Monitor
<img width="1361" alt="DrawdownMonitor" src="https://github.com/user-attachments/assets/e2e9c170-2b88-49a7-afbf-cb30a6f49019" />

### Admin Bereich & Konfiguration
<img width="2178" alt="AdminArea" src="https://github.com/user-attachments/assets/b8743339-a6d3-4483-a222-21ac0587d59a" />
<img width="2068" alt="OverviewInAdminconsole" src="https://github.com/user-attachments/assets/1009b7e1-2f8c-4f6f-aeb2-926b7c880494" />

### Sync-Check & Logging
<img width="1895" alt="SyncCheck" src="https://github.com/user-attachments/assets/934efe34-34d1-4a71-884d-396cbd5e68db" />

### Tagesreport Generierung
<img width="1844" alt="Tagesreport" src="https://github.com/user-attachments/assets/203d6278-bebe-47d1-8aac-7e719ca9cd62" />

### Integration & MetaTrader
<img width="932" alt="Metatrader5 integration" src="https://github.com/user-attachments/assets/14867e8c-f429-4c37-9cd5-122a362e6191" />
<img width="2056" alt="HomeyIntegration" src="https://github.com/user-attachments/assets/0d0e38d3-99f7-49f4-a28d-3473aeacbaac" />
<img width="782" alt="login" src="https://github.com/user-attachments/assets/0b6940bd-0185-41e9-9863-0ee88e952bd4" />

</details>
>>>>>>> b283b2166dd36752d8dd80ffc796918542598a4d

Das Projekt nutzt etablierte Architektur- und Design-Patterns, um Wartbarkeit und Skalierbarkeit für Entwickler zu gewährleisten:

<<<<<<< HEAD
*   **Backend-Struktur**: Strikt getrennt nach dem Controller-Service-Repository Pattern. Entkoppelte Geschäftslogik für hohe Testbarkeit.
*   **Frontend-Architektur**: Verwendung performanter Thymeleaf Templates (`src/main/resources/templates`) in Kombination mit modernem Vanilla-JavaScript.
*   **Styling**: Ein eigens entwickeltes, reaktives Design-System (`src/main/resources/static/css/style.css`), das tiefgreifend auf CSS-Variablen basiert, um ein nahtloses Theming (Dark Mode per Default) und eine erstklassige User Experience (UX) zu bieten.

---

## 6. Galerie / Screenshots

*(Einblicke in die Benutzeroberfläche und die umfassenden Überwachungsfunktionen)*

<details>
<summary><strong>Bildergalerie anzeigen</strong> (Klick zum Erweitern)</summary>
<br />

### Dashboard & Startseite
<img width="2611" alt="Overview" src="https://github.com/user-attachments/assets/1400e350-a1e9-4e18-976a-ca2ca1cbf161" />

### Account Details & tiefgehende Analyse
<img width="3827" alt="AccountDetail1" src="https://github.com/user-attachments/assets/2006583e-f380-4859-b851-68e45adb8987" />
<img width="3823" alt="AccountDetail2" src="https://github.com/user-attachments/assets/0acebcc5-c9c6-45ab-a969-4f9ad95b18a7" />
<img width="3694" alt="AccountDetail3" src="https://github.com/user-attachments/assets/18194ae8-8fd9-451f-9284-30466231e0eb" />
<img width="3715" alt="AccountDetailEquityDetail" src="https://github.com/user-attachments/assets/b026c5f6-9070-4679-aee9-73bb3e345294" />

### Mobile Drawdown Monitor
<img width="1361" alt="DrawdownMonitor" src="https://github.com/user-attachments/assets/e2e9c170-2b88-49a7-afbf-cb30a6f49019" />

### Admin Bereich & Konfiguration
<img width="2178" alt="AdminArea" src="https://github.com/user-attachments/assets/b8743339-a6d3-4483-a222-21ac0587d59a" />
<img width="2068" alt="OverviewInAdminconsole" src="https://github.com/user-attachments/assets/1009b7e1-2f8c-4f6f-aeb2-926b7c880494" />

### Sync-Check & Logging
<img width="1895" alt="SyncCheck" src="https://github.com/user-attachments/assets/934efe34-34d1-4a71-884d-396cbd5e68db" />

### Tagesreport Generierung
<img width="1844" alt="Tagesreport" src="https://github.com/user-attachments/assets/203d6278-bebe-47d1-8aac-7e719ca9cd62" />

### Integration & MetaTrader
<img width="932" alt="Metatrader5 integration" src="https://github.com/user-attachments/assets/14867e8c-f429-4c37-9cd5-122a362e6191" />
<img width="2056" alt="HomeyIntegration" src="https://github.com/user-attachments/assets/0d0e38d3-99f7-49f4-a28d-3473aeacbaac" />
<img width="782" alt="login" src="https://github.com/user-attachments/assets/0b6940bd-0185-41e9-9863-0ee88e952bd4" />

</details>

=======
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
>>>>>>> b283b2166dd36752d8dd80ffc796918542598a4d
