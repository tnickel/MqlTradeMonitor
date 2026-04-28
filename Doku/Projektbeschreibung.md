# MQL Trade Monitor &mdash; Technische Projektdokumentation

## 1. Projektsteckbrief

| | |
|---|---|
| **Projekttyp** | Full-Stack Eigenentwicklung (Solo-Projekt) |
| **Status** | Produktiv im Einsatz &mdash; ueberwacht taeglich reale Trading-Konten |
| **Entwicklungszeit** | Laufende Weiterentwicklung seit Projektstart |
| **Umfang** | ~60 Java-Klassen, 12 Templates, 11 DB-Tabellen, 1 MQL5-Client |
| **Technologie** | Java 17, Spring Boot 3.2, Spring Security, JPA/Hibernate, H2, Thymeleaf, Chart.js, MQL5 |

---

## 2. Problemstellung & Loesung

### Das Problem
Professionelle Trader mit mehreren MetaTrader 5 Konten (Real + Demo) benoetigen einen zentralen Ueberblick ueber alle Konten, automatisierte Alarme bei kritischen Drawdowns und eine lueckenlose Analyse der Copy-Trading-Qualitaet (Slippage, Delays) &mdash; Funktionen, die MetaTrader 5 nativ nicht bietet.

### Die Loesung
Eine vollstaendig selbst entwickelte Monitoring-Plattform, die:
- **Daten in Echtzeit erfasst** ueber einen eigens entwickelten MQL5 Expert Advisor mit ausfallsicherer Reconnect-Logik
- **Intelligent aggregiert** durch In-Memory-Caching mit persistentem DB-Backing
- **Anomalien automatisch erkennt** mittels konfigurierbarem Schwellwert-Monitoring und zweistufigem Trade-Matching
- **Ueber multiple Kanaele alarmiert** (Web-Dashboard, E-Mail, Smart-Home-Sirene)
- **Umfassende Analysen bietet** mit interaktiven Charts, Strategie-Performance-Breakdowns und Copy-Trade-Auswertungen

---

## 3. Architektur

### 3.1 Systemuebersicht

```
  MetaTrader 5 Terminal(s)             Spring Boot Server
 +-----------------------+          +---------------------------+
 | MQL5 Expert Advisor   |  REST    |  Controller Layer (5)     |
 | - Trade Monitoring    | -------> |  - Api / Dashboard / Admin|
 | - Heartbeat           |  HTTP    |  - Security / User        |
 | - History Export       |  POST   +---------------------------+
 | - Auto-Reconnect      |          |  Service Layer (11)       |
 +-----------------------+          |  - AccountManager (Cache) |
                                    |  - TradeSyncService       |
                                    |  - OpenProfitAlarmService |
                                    |  - TradeComparisonService |
                                    |  - Email / Homey Service  |
                                    +---------------------------+
                                    |  Persistence Layer        |
                                    |  - Spring Data JPA (11)   |
                                    |  - H2 File Database       |
                                    +---------------------------+
                                              |
                                    +---------------------------+
                                    |  Web Frontend             |
                                    |  - 12 Thymeleaf Templates |
                                    |  - Chart.js Visualisierung|
                                    |  - Responsive Dark UI     |
                                    +---------------------------+
```

### 3.2 Zentrale Architekturentscheidungen

| Entscheidung | Begruendung |
|---|---|
| **In-Memory Cache + DB-Backing** | Dashboard-Latenz minimieren bei gleichzeitiger Datenpersistenz |
| **H2 Embedded Database** | Zero-Config Deployment, keine externe DB-Infrastruktur noetig |
| **Thymeleaf SSR** | Schnelle Seitenladezeiten, SEO-freundlich, kein separates Frontend-Build |
| **Scheduled Tasks** | Entkopplung von Echtzeit-Monitoring und Request-Handling |
| **REST API ohne Auth** | EA-Client in MQL5 hat keine Session-/Cookie-Verwaltung; Absicherung ueber Netzwerk |
| **Dynamische Konfiguration** | Alle Parameter live aenderbar ohne Neustart ueber Admin-Panel |

---

## 4. Komponenten im Detail

### 4.1 MQL5 Expert Advisor (Client)

Der Client ist ein nativer MetaTrader 5 Expert Advisor (Version 1.04), entwickelt in MQL5.

**Kernfunktionalitaet:**
- Kontinuierliches Streaming von Balance, Equity, offenen und geschlossenen Trades an den Server
- Zweistufiges Kommunikationsprotokoll: Initialer Voll-Upload (`/api/trades-init`), danach inkrementelle Deltas (`/api/trades`)
- Heartbeat-Watchdog (`/api/heartbeat`) zur Erkennung von Verbindungsabbruechen

**Robustheit & Ausfallsicherheit:**
- Automatische Reconnects mit konfigurierbarem Exponential-Backoff (ReconnectIntervalSeconds, MaxReconnectAttempts)
- Erweiterter Retry-Modus (15 Minuten) nach Ausschoepfung aller Reconnect-Versuche
- State-Persistenz ueber GlobalVariables (`TM_TradeListSent_{ID}`, `TM_LastSync_{ID}`) und Config-Datei
- Intelligenter Tick-Mode: Automatische Umschaltung auf hochaufloesende Ueberwachung bei kritischem Drawdown

**Benutzer-Feedback:**
- Chart-Label mit Live-Status: Connected/Offline, Account-ID, Sync-Status, Fehlercodes, Retry-Countdown

### 4.2 Controller Layer (5 Controller)

| Controller | Endpunkte | Verantwortung |
|---|---|---|
| **ApiController** | `POST /api/register`, `/trades-init`, `/trades`, `/heartbeat`, `/history`; `GET /api/accounts`; `POST /api/test-email` | REST-Schnittstelle fuer MetaTrader EA und AJAX |
| **DashboardController** | `GET /`, `/open-trades`, `/account/{id}`, `/report/{period}`, `/trade-comparison`, `/mobile/drawdown`; diverse AJAX-Endpoints | Web-Views und dynamische Datenabfragen |
| **AdminController** | `GET /admin`, `/admin/logs`, `/admin/requests`, `/admin/client-logs`; `POST /admin/create-user`, `/admin/security`, u.v.m. | Administration, Benutzerverwaltung, Konfiguration |
| **SecurityController** | `GET /login` | Login-Seite |
| **UserController** | `GET /profile`; `POST /profile/change-password` | Benutzerprofil und Passwortverwaltung |

### 4.3 Service Layer (11 Services)

| Service | Intervall | Kernaufgabe |
|---|---|---|
| **AccountManager** | On-Demand | In-Memory Account-Cache, Account-/Trade-Verwaltung, Sektions-Management, Magic-Number-Profit-Berechnung |
| **TradeStorage** | On-Demand | DB-Persistenz fuer Trades und Equity-Snapshots (Rate-limitiert: 1x/Min, Auto-Cleanup >90 Tage) |
| **TradeSyncService** | 1 Sekunde | REAL/DEMO-Sync-Check mit zweistufigem Matching (Strict: Symbol+Typ+Zeit; Fallback: Symbol+Typ+SL) |
| **TradeComparisonService** | On-Demand | Slippage- und Delay-Analyse fuer Copy-Trading (Matching ueber 120s-Zeitfenster) |
| **OpenProfitAlarmService** | 5 Sekunden | Schwellwert-Monitoring (absolut/prozentual) mit Latch-Logik gegen Alarm-Fluten |
| **GlobalConfigService** | On-Demand | Zentrale Key-Value-Konfiguration (25+ Parameter), persistiert in DB |
| **MagicMappingService** | On-Demand | Magic Number zu lesbarem Strategienamen, Auto-Discovery neuer Magic Numbers |
| **EmailService** | On-Demand | SMTP-Alerting mit konfigurierbarem taeglichem Rate-Limit |
| **HomeyService** | On-Demand | Smart-Home Webhook-Integration (Sirene) mit konfigurierbarer Wiederholung |
| **UserService** | On-Demand | Benutzerverwaltung, RBAC, BCrypt-Hashing, Account-Berechtigungen |
| **LogCleanupService** | Taeglich 2:00 | Automatische Bereinigung alter Login-/Request-/Client-Logs |

### 4.4 Security Layer

| Komponente | Funktion |
|---|---|
| **SecurityConfig** | Spring Security Konfiguration: BCrypt, CSRF (deaktiviert fuer API), URL-basierte Zugriffskontrolle |
| **BruteForceProtectionService** | IP-basierte Login-Sperre: Konfigurierbare Fehlversuche + Sperrdauer, Auto-Cleanup alle 10 Min |
| **RateLimitFilter** (Order 1) | Per-IP Sliding-Window Rate-Limiting (1 Min), ueberspringt API- und Static-Pfade |
| **SecurityHeadersFilter** (Order 2) | CSP, HSTS, X-Frame-Options, X-XSS-Protection, Referrer-Policy |
| **RequestLoggingFilter** | Audit-Log fuer neue IP-Adressen |
| **AuthenticationEvents** | Event-Listener fuer Login-Erfolg/-Fehlschlag, speist LoginLog und BruteForce-Tracking |

### 4.5 Externe API Clients (Replit App)

Neben dem internen Thymeleaf SSR Frontend unterstützt der Server auch komplett entkoppelte Headless-Clients.
Aktuell existiert eine experimentelle Web-App, die über Replit (Replit AI) entwickelt wird. 

**Besonderheiten:**
- Eigene REST-API-Auth-Endpunkte (`/api/login`, `/api/demo-login`, `/api/logout`) ohne CSRF-Zwang
- Sämtliche Dokumentation, Prompts und Beschreibungen für die Replit App befinden sich im separaten Verzeichnis `replit/` (z.B. `Replit_Erweiterung.md`)

### 4.6 MCP Server (AI-Sidecar)

Der TradeMonitor bringt einen eigenen Model Context Protocol (MCP) Server mit, um sich nahtlos in KI-Agenten wie die Claude Desktop App zu integrieren. Der MCP-Server läuft lokal als Node.js/TypeScript-Anwendung und spricht über REST mit dem TradeMonitor-Backend.

**Verfügbare Tools (Schnittstellen) für die KI:**
- `get_accounts`, `get_open_trades`, `get_closed_trades`: Trading-Daten lesen
- `get_system_status`, `get_daily_profits`: Dashboard-Metriken aggregieren
- `get_ea_logs`: Logs der Expert Advisors auslesen
- `get_blocked_ips`, `get_server_health`: Fail2Ban und System-Ressourcen (erfordert Admin-Rechte)

### 4.6 KI-Integration (Model Context Protocol)

Der Server verfügt über einen voll integrierten **MCP (Model Context Protocol) Server**, der es modernen Large Language Models (wie Claude) ermöglicht, als intelligente Agenten mit dem TradeMonitor-Backend zu kommunizieren.

**Besonderheiten:**
- **Natürliche Sprache zu API:** Das LLM übersetzt menschliche Anfragen ("Wie sieht der Serverstatus aus?", "Welche IPs sind geblockt?") in exakte Tool-Aufrufe (REST-Endpoints).
- **Verfügbare Tools:** `get_server_health`, `get_system_status`, `get_accounts`, `get_open_trades`, `get_closed_trades`, `get_daily_profits`, `get_ea_logs`, `get_blocked_ips`.
- **Produktionstauglich:** Das System ruft Live-Daten aus der H2-Datenbank und vom Contabo-Server (z.B. Fail2Ban) ab. 

---

## 5. Datenbankschema (11 Tabellen)

| Tabelle | Entity | Besonderheit |
|---|---|---|
| `accounts` | `AccountEntity` | PK: accountId, Typ DEMO/REAL, pro-Account Alarm-Schwellwerte, Sektionszuordnung |
| `equity_snapshots` | `EquitySnapshotEntity` | Index auf (accountId, timestamp), Rate-limitiert 1x/Min, Auto-Cleanup >90 Tage |
| `closed_trades` | `ClosedTradeEntity` | Unique Key: (accountId, ticket), Duplikat-Erkennung beim Import |
| `open_trades` | `OpenTradeEntity` | Vollstaendiger Ersatz bei jedem Update-Zyklus |
| `users` | `UserEntity` | BCrypt-Hash, ROLE_ADMIN/ROLE_USER, `allowedAccountIds` (ElementCollection) |
| `dashboard_sections` | `DashboardSectionEntity` | Name + displayOrder fuer Drag & Drop Layout |
| `magic_mappings` | `MagicMappingEntity` | PK: magicNumber, Custom Label/Kommentar |
| `global_config` | `GlobalConfigEntity` | Key-Value Store fuer 25+ konfigurierbare Parameter |
| `login_logs` | `LoginLog` | Zeitstempel, Benutzer, IP, Erfolg/Fehlschlag, Details |
| `request_logs` | `RequestLog` | IP, Methode, URI, Query, User-Agent, Status (max. 1000 Zeichen) |
| `client_logs` | `ClientLog` | Account-ID, Aktion (REGISTER/UPDATE/HEARTBEAT/HISTORY), IP, Nachricht |

---

## 6. Algorithmen

### 6.1 Trade-Sync (TradeSyncService)

Zweistufiger Abgleich zwischen REAL- und DEMO-Accounts (laeuft jede Sekunde):

**Stufe 1 &mdash; Strict Match:**
- Symbol muss uebereinstimmen
- Typ (BUY/SELL) muss uebereinstimmen
- Open Time innerhalb von 60 Sekunden Toleranz

**Stufe 2 &mdash; Fallback Match (falls Stufe 1 scheitert):**
- Symbol muss uebereinstimmen
- Typ muss uebereinstimmen
- StopLoss muss exakt uebereinstimmen

**Ergebnis pro Trade:** MATCHED | WARNING | EXEMPTED

Bei WARNING ueber konfigurierbarer Dauer: E-Mail + Homey-Sirene

### 6.2 Slippage-Analyse (TradeComparisonService)

Vergleich geschlossener Trades zwischen REAL und DEMO:

- **Matching:** Symbol + Typ + Open Time (120s Toleranz) ODER Symbol + Typ + StopLoss (exakt)
- **Open/Close Delay:** Zeitdifferenz in Sekunden
- **Open/Close Slippage:** Preisdifferenz, normalisiert fuer BUY/SELL-Richtung

### 6.3 Open-Profit-Alarm (OpenProfitAlarmService)

- **Absoluter Schwellwert:** Alarm wenn Open Profit < Wert (z.B. -5000 EUR)
- **Prozentualer Schwellwert:** Alarm wenn Drawdown > x% der Balance
- **Latch-Logik:** Alarm feuert einmalig, setzt sich erst zurueck wenn Bedingung nicht mehr zutrifft

---

## 7. Konfiguration

### 7.1 Statisch (application.properties)

```properties
server.port=8080
spring.datasource.url=jdbc:h2:file:./data/trademonitor
spring.jpa.hibernate.ddl-auto=update
account.timeout.seconds=60
app.admin.username=admin
app.admin.password=password
server.tomcat.max-connections=200
server.tomcat.threads.max=50
```

### 7.2 Dynamisch (Admin-Panel, persistiert in DB)

| Kategorie | Parameter |
|---|---|
| **Live-Indicator** | Schwellwerte in Minuten (gruen/gelb/orange), Farben (Hex) |
| **E-Mail** | SMTP-Host, Port, User, Password, From, To, Max/Tag |
| **Logging** | Aufbewahrungsdauer Login/Verbindungs/Client-Logs (Tage) |
| **Security** | Rate-Limit ein/aus + Wert, Brute-Force ein/aus + Attempts + Lockout, Headers, Max Sessions, H2-Konsole |
| **Homey** | Webhook-ID, Event, Trigger-Quellen (Sync/API), Wiederholungen, Alarm-Delay |
| **Sync** | Ausgenommene Magic Numbers (kommagetrennt) |

---

## 8. Web-Oberflaeche

### Templates (12 Thymeleaf Views)

| Template | URL | Beschreibung |
|---|---|---|
| `dashboard.html` | `/` | Hauptdashboard: Kacheln, Sektionen, Report-Charts, Live-Status, Alarm-Banner |
| `account-detail.html` | `/account/{id}` | Balance/Equity-Overlay-Chart, Magic-Profit-Kurven, Historientabelle |
| `open-trades.html` | `/open-trades` | Globale Uebersicht offener Positionen (REAL priorisiert) |
| `report.html` | `/report/{period}` | Aggregierte Profit-Reports (daily/weekly/monthly) |
| `trade-comparison.html` | `/trade-comparison` | REAL vs. DEMO Slippage/Delay-Analyse |
| `mobile-drawdown.html` | `/mobile/drawdown` | Mobiles Drawdown-Ranking |
| `admin.html` | `/admin` | Komplettes Admin-Panel mit allen Konfigurationen |
| `admin-logs.html` | `/admin/logs` | Login-Audit-Logs |
| `admin-requests.html` | `/admin/requests` | Request-Audit-Logs |
| `admin-client-logs.html` | `/admin/client-logs` | Client-Aktions-Logs (filterbar) |
| `profile.html` | `/profile` | Benutzerprofil, Passwortwechsel |
| `login.html` | `/login` | Login-Formular |

---

## 9. Installation & Betrieb

### Voraussetzungen
- Java JDK 17+
- Maven (fuer Build)

### Build & Start
```bash
cd server
mvn clean package
java -jar target/trade-monitor-server-0.12.0.jar
```

### MetaTrader 5 Client
1. `TradeMonitorClient.mq5` nach `MQL5/Experts/` kopieren und kompilieren
2. Server-URL in MT5 freigeben (*Extras > Optionen > Experten*)
3. EA auf Chart ziehen, Auto-Trading aktivieren
4. Account erscheint automatisch im Dashboard

### Backup & Reset
- **Backup:** Server stoppen, `./data/trademonitor.mv.db` kopieren
- **Reset:** Server stoppen, Datenbankdatei loeschen (wird beim Start neu erstellt)

---

## 10. Eingesetzte Patterns & Praktiken

- **Controller-Service-Repository**: Strikte Schichtentrennung
- **In-Memory Cache mit Write-Through**: Minimale Latenz bei gleichzeitiger Persistenz
- **Scheduled Task Pattern**: Entkoppeltes Echtzeit-Monitoring (1s, 5s, taeglich)
- **Observer/Event Pattern**: Authentication-Events fuer Audit-Logging und Brute-Force-Tracking
- **Strategy Pattern**: Zweistufiges Trade-Matching (Strict + Fallback)
- **Latch Pattern**: Alarm-Deduplication im Open-Profit-Monitoring
- **Rate-Limiting**: Sliding-Window-Algorithmus per IP
- **Duplicate Detection**: Idempotente Trade-Imports ueber Composite Key (accountId + ticket)
- **Configuration as Code**: Key-Value Store mit UI-Editor, keine hartcodierten Werte
