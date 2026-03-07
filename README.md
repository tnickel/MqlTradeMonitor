<div align="center">
  <h1>MQL Trade Monitor</h1>
  <p>
    <strong>Professionelle Echtzeit-Monitoring-Plattform fuer MetaTrader 5 Trading-Konten</strong>
  </p>
  <p>
    <em>Full-Stack Eigenentwicklung &mdash; von der Architektur ueber das Backend bis zum interaktiven Frontend.</em>
  </p>

  <br />

  <table>
    <tr>
      <td><strong>Backend</strong></td>
      <td>Java 17 &bull; Spring Boot 3.2 &bull; Spring Security &bull; Spring Data JPA &bull; H2 Database</td>
    </tr>
    <tr>
      <td><strong>Frontend</strong></td>
      <td>Thymeleaf SSR &bull; Chart.js &bull; Vanilla JS &bull; CSS Custom Properties (Dark Mode)</td>
    </tr>
    <tr>
      <td><strong>Client</strong></td>
      <td>MQL5 Expert Advisor &bull; HTTP REST &bull; Stateful Reconnect-Logik</td>
    </tr>
    <tr>
      <td><strong>Security</strong></td>
      <td>BCrypt &bull; RBAC &bull; Brute-Force-Schutz &bull; Rate-Limiting &bull; CSP &bull; HSTS</td>
    </tr>
    <tr>
      <td><strong>Architektur</strong></td>
      <td>Controller-Service-Repository &bull; In-Memory Cache &bull; Scheduled Tasks &bull; Event-Driven Alerts</td>
    </tr>
  </table>
</div>

<br />

---

## Ueber dieses Projekt

Dieses Projekt ist eine **vollstaendig eigenstaendig konzipierte und entwickelte** Monitoring-Plattform, die den gesamten Software-Lebenszyklus abdeckt: Von der initialen Anforderungsanalyse ueber die Systemarchitektur, die Backend- und Frontend-Implementierung bis hin zum Deployment und laufenden Betrieb.

Die Plattform ueberwacht in Echtzeit mehrere MetaTrader 5 Trading-Konten, aggregiert Performance-Daten, erkennt automatisch Anomalien und alarmiert bei kritischen Zustaenden ueber multiple Kanaele (Web-Dashboard, E-Mail, Smart-Home-Integration).

**Das System laeuft produktiv und ueberwacht taeglich reale Trading-Konten.**

---

## Highlights & technische Staerken

### Durchdachte Systemarchitektur
- **Zwei-Komponenten-Design**: Klare Trennung zwischen Datenerfassung (MQL5 Expert Advisor) und Datenverarbeitung (Spring Boot Backend) ueber eine definierte REST-API.
- **In-Memory Cache mit DB-Backing**: Accounts werden fuer minimale Dashboard-Latenz im Speicher gehalten und asynchron in der H2-Datenbank persistiert.
- **Striktes Controller-Service-Repository Pattern**: Saubere Schichtenarchitektur mit entkoppelter Geschaeftslogik fuer hohe Wartbarkeit und Testbarkeit.

### Intelligentes Echtzeit-Monitoring
- **Multi-Account-Ueberwachung**: Beliebig viele MetaTrader-Terminals senden parallel Daten an einen zentralen Server.
- **Trade-Synchronisation (REAL vs. DEMO)**: Automatischer Abgleich offener Trades zwischen Echtgeld- und Demo-Konten mit zweistufigem Matching-Algorithmus (Strict Match + Fallback Match).
- **Open-Profit-Alarm-System**: Konfigurierbares Schwellwert-Monitoring (absolut und prozentual) mit Latch-Logik zur Vermeidung von Alarm-Fluten.
- **Slippage- & Delay-Analyse**: Automatisierte Auswertung von Ausfuehrungsdifferenzen beim Copy-Trading.

### Umfassendes Security-Konzept
- **Spring Security** mit BCrypt-Passwort-Hashing und rollenbasierter Zugriffskontrolle (RBAC).
- **IP-basierter Brute-Force-Schutz** mit konfigurierbaren Schwellwerten und automatischer Sperrung.
- **Rate-Limiting** (Sliding Window, per IP) zum Schutz vor Ueberlastung.
- **Security-Headers**: Content-Security-Policy, HSTS, X-Frame-Options, XSS-Protection.
- **Audit-Logging**: Lueckenlose Protokollierung von Logins, Requests und Client-Aktionen.
- **Alle Sicherheitsparameter live konfigurierbar** ueber das Admin-Panel &mdash; kein Neustart noetig.

### Professionelles Web-Dashboard
- **Dynamisches Kachel-Layout** mit Drag & Drop, Sektionsverwaltung und persistentem Layout.
- **Interaktive Charts**: Ueberlagerte Balance/Equity-Kurven, Magic-Number-Profit-Kurven, Tages-/Wochen-/Monatsreports.
- **Responsive Design**: Dedizierte Mobile-Ansicht fuer Drawdown-Monitoring unterwegs.
- **Dark Mode** als Standard mit durchgaengigem CSS-Custom-Properties-Theming.

### Robuster MQL5 Client
- **Ausfallsichere Kommunikation**: Automatische Reconnects mit Exponential-Backoff, Heartbeat-Watchdog, State-Persistenz ueber GlobalVariables.
- **Intelligenter Tick-Mode**: Automatische Umschaltung auf hochaufloesende Ueberwachung bei kritischem Drawdown.
- **Inkrementelle Synchronisation**: Deltabasierte Updates minimieren Datenverkehr und Serv-Last.

### Smart-Home & Benachrichtigungen
- **E-Mail-Alerting**: SMTP-Integration mit konfigurierbarem Rate-Limit (taegliches Maximum).
- **Homey-Webhook-Anbindung**: Sirenen-Alarm ueber Smart-Home bei kritischen Trading-Situationen.
- **Multi-Channel**: Alarme werden parallel ueber Dashboard-Banner, E-Mail und Smart-Home ausgeloest.

---

## Architektur-Uebersicht

```
  MetaTrader 5 Terminal(s)             Spring Boot Server
 +-----------------------+          +---------------------------+
 | MQL5 Expert Advisor   |  REST    |  Controller Layer         |
 | - Trade Monitoring    | -------> |  - ApiController          |
 | - Heartbeat           |  HTTP    |  - DashboardController    |
 | - History Export       |  POST   |  - AdminController        |
 | - Auto-Reconnect      |          |  - SecurityController     |
 +-----------------------+          +---------------------------+
                                    |  Service Layer            |
                                    |  - AccountManager (Cache) |
                                    |  - TradeSyncService       |
                                    |  - OpenProfitAlarmService |
                                    |  - TradeComparisonService |
                                    |  - EmailService           |
                                    |  - HomeyService           |
                                    +---------------------------+
                                    |  Persistence Layer        |
                                    |  - Spring Data JPA        |
                                    |  - H2 File Database       |
                                    |  - 11 Entity Tables       |
                                    +---------------------------+
                                              |
                                    +---------------------------+
                                    |  Web Dashboard            |
                                    |  - Thymeleaf SSR          |
                                    |  - Chart.js               |
                                    |  - Responsive Dark UI     |
                                    +---------------------------+
```

---

## Feature-Uebersicht

| Bereich | Features |
|---|---|
| **Dashboard** | Dynamische Kacheln, Drag & Drop, Sektionen, Report-Charts, Live-Status-Indikatoren |
| **Account-Analyse** | Balance/Equity-Overlay-Chart, Performance pro Strategie, Drawdown-Berechnung, Historien-Filter |
| **Trade-Sync** | Automatischer REAL/DEMO-Abgleich, zweistufiges Matching, Sync-Ausnahmen, Alarm bei Diskrepanzen |
| **Copy-Trade-Analyse** | Slippage-Messung, Delay-Berechnung, Trade-Matching ueber Zeitfenster |
| **Alarm-System** | Open-Profit-Schwellwerte (absolut/prozentual), E-Mail, Smart-Home-Sirene, Dashboard-Banner |
| **Security** | RBAC, BCrypt, Brute-Force-Schutz, Rate-Limiting, CSP, HSTS, Audit-Logs |
| **Admin-Panel** | Benutzerverwaltung, DB-Statistiken, Magic-Number-Mapping, Live-Konfiguration aller Parameter |
| **Berichte** | Tages-/Wochen-/Monatsreports mit aggregierten Profit-Charts |
| **Mobile** | Responsive Drawdown-Monitor fuer unterwegs |
| **Integration** | MetaTrader 5 EA, SMTP E-Mail, Homey Smart-Home Webhook |

---

## Technologie-Stack im Detail

| Schicht | Technologie | Einsatzzweck |
|---|---|---|
| **Runtime** | Java 17 | Basis-Plattform |
| **Framework** | Spring Boot 3.2 | Web-Framework, Dependency Injection, Auto-Configuration |
| **Security** | Spring Security 6 | Authentifizierung, Autorisierung, CSRF, Session-Management |
| **ORM** | Spring Data JPA / Hibernate | Objekt-relationales Mapping, Repository-Pattern |
| **Datenbank** | H2 (File-basiert) | Embedded, wartungsfrei, Zero-Config |
| **Templating** | Thymeleaf + Thymeleaf-Extras-SpringSecurity | Server-Side Rendering mit Security-Integration |
| **Charts** | Chart.js | Interaktive Echtzeit-Diagramme |
| **Serialisierung** | Jackson (inkl. JSR-310) | JSON-Verarbeitung mit Java-Time-Support |
| **E-Mail** | Spring Boot Starter Mail | SMTP-basiertes Alerting |
| **Client** | MQL5 (MetaTrader 5) | Nativer Trading-Plattform-Client |
| **Build** | Maven | Dependency-Management und Build-Automatisierung |

---

## Datenbankschema

11 relationale Tabellen, automatisch verwaltet durch JPA (`ddl-auto=update`):

| Tabelle | Zweck | Besonderheit |
|---|---|---|
| `accounts` | Account-Stammdaten, Metriken, Alarm-Konfig | Typ DEMO/REAL, pro-Account Alarm-Schwellwerte |
| `equity_snapshots` | Equity-Zeitreihe fuer Charts | Rate-limitiert (1x/Min), Auto-Cleanup >90 Tage |
| `closed_trades` | Trade-Historie | Duplikat-Erkennung (accountId + ticket) |
| `open_trades` | Aktuelle Positionen | Vollstaendiger Ersatz bei jedem Update |
| `users` | Benutzerverwaltung | BCrypt-Hash, RBAC, Account-Berechtigungen |
| `dashboard_sections` | Layout-Persistenz | Drag & Drop Reihenfolge |
| `magic_mappings` | Strategie-Bezeichnungen | Magic Number zu lesbarem Namen |
| `global_config` | Dynamische Konfiguration | Key-Value Store, alle Security/Alert-Parameter |
| `login_logs` | Authentifizierungs-Audit | Erfolg/Fehlschlag, IP, Zeitstempel |
| `request_logs` | HTTP-Audit | Neue IPs, User-Agent, Status-Code |
| `client_logs` | Client-Aktions-Audit | Register, Update, Heartbeat pro Account |

---

## Screenshots & Benutzeroberflaeche

<img width="3760" height="1268" alt="Dashboard Uebersicht mit Live-Status und Report-Kacheln" src="https://github.com/user-attachments/assets/b47cd987-440b-4c0e-88b5-0f895baaf5e5" />

<details>
<summary><strong>Alle Screenshots anzeigen</strong> (Klick zum Erweitern)</summary>
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

---

## Installation & Schnellstart

### Server

```bash
# Build
cd server
mvn clean package

# Start
java -jar target/trade-monitor-server-0.12.0.jar

# Dashboard oeffnen
# http://localhost:8080
```

### MetaTrader 5 Client

1. `TradeMonitorClient.mq5` nach `MQL5/Experts/` kopieren und im MetaEditor kompilieren (F7).
2. In MT5 unter *Extras > Optionen > Experten* die Server-URL `http://localhost:8080` erlauben.
3. EA auf einen Chart ziehen, "Auto-Trading" aktivieren &mdash; fertig.

Der Account registriert sich automatisch und erscheint innerhalb von Sekunden im Dashboard.

---

## Projektstruktur

```
MqlTradeMonitor/
+-- server/                          # Spring Boot Backend
|   +-- pom.xml                      # Maven Build-Konfiguration
|   +-- src/main/java/de/trademonitor/
|   |   +-- controller/              # 5 REST/Web Controller
|   |   +-- service/                 # 11 Services (Geschaeftslogik)
|   |   +-- entity/                  # 11 JPA Entities
|   |   +-- repository/              # 11 Spring Data Repositories
|   |   +-- dto/                     # 9 Data Transfer Objects
|   |   +-- model/                   # 3 In-Memory Domain Models
|   |   +-- security/                # Authentication & Authorization
|   |   +-- config/                  # Security Filter & Events
|   +-- src/main/resources/
|       +-- templates/               # 12 Thymeleaf Templates
|       +-- static/                  # CSS, JavaScript
|       +-- application.properties   # Spring-Konfiguration
+-- mql5/
|   +-- TradeMonitorClient.mq5       # MQL5 Expert Advisor
+-- Doku/                            # Ausfuehrliche Dokumentation
    +-- Projektbeschreibung.md       # Technische Projektdokumentation
    +-- Benutzerdokumentation.md     # Benutzerhandbuch
    +-- API-Referenz.md              # Vollstaendige API-Dokumentation
    +-- Sicherheit.md               # Security-Dokumentation
    +-- Entwickler.md               # Entwickler-Guide
```

---

## Ausfuehrliche Dokumentation

Im Ordner [`Doku/`](Doku/) finden Sie detaillierte Dokumentation:

| Dokument | Inhalt |
|---|---|
| [Projektbeschreibung](Doku/Projektbeschreibung.md) | Architektur, alle Komponenten, Datenbankschema, Konfiguration |
| [Benutzerdokumentation](Doku/Benutzerdokumentation.md) | Vollstaendiges Benutzerhandbuch mit allen Features |
| [API-Referenz](Doku/API-Referenz.md) | Alle REST-Endpunkte mit Request/Response-Beispielen |
| [Sicherheit](Doku/Sicherheit.md) | Security-Konzept, Brute-Force, Rate-Limiting, Headers, Audit |
| [Entwickler](Doku/Entwickler.md) | Architektur-Patterns, Datenmodell, Algorithmen, Konventionen |

---

## Eingesetzte Software-Engineering Praktiken

- **Clean Architecture**: Strikte Schichtentrennung (Controller / Service / Repository)
- **Domain-Driven Design**: Fachlich geschnittene Services mit klaren Verantwortlichkeiten
- **Defensive Programmierung**: Duplikat-Erkennung, Rate-Limiting, Latch-Logik, Graceful Degradation
- **Security by Design**: Mehrschichtiges Sicherheitskonzept (Authentication, Authorization, Input Validation, Audit)
- **Configuration as Code**: Dynamische Konfiguration via Admin-Panel, keine hartcodierten Werte
- **Responsive Design**: Mobile-First Ansatz fuer kritische Monitoring-Views
- **Zero-Config Deployment**: Embedded Database, selbstregistrierende Clients, Auto-Schema-Migration
