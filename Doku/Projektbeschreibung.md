# Projekt-Dokumentation: MQL Trade Monitor

## 1. Übersicht

Das Projekt **MQL Trade Monitor** ist eine umfassende Lösung zur Überwachung und Analyse von MetaTrader 5 Trading-Konten in Echtzeit. Es bietet ein modernes Web-Dashboard zur zentralen Anzeige von Account-Metriken, offenen Positionen, historischen Daten und automatisierten Alarmen.

Das System besteht aus zwei Hauptkomponenten:

1. **MQL5 Expert Advisor (Client)**: Läuft im MetaTrader 5 Terminal und exportiert Account-Daten sowie Trades in Echtzeit an den Server.
2. **Java Spring Boot Server (Backend)**: Empfängt die Daten, speichert sie in einer H2-Datenbank und stellt eine interaktive Web-Oberfläche mit umfangreichen Analyse- und Alarmfunktionen bereit.

**Technologie-Stack:**
- Java 17, Spring Boot 3.2, Spring Data JPA, Spring Security
- H2 Database (File-basiert, Zero-Config)
- Thymeleaf (Server-Side Rendering), Chart.js
- MQL5 (MetaTrader 5 Expert Advisor)

---

## 2. Architektur & Komponenten

### 2.1 MQL5 Expert Advisor (`TradeMonitorClient.mq5`)

Der Client agiert als Sensor innerhalb der Handelsplattform (Version 1.04).

- **Funktion**: Überwacht Account-Zustand und Trades, sendet Daten per HTTP POST an den Server.
- **Features**:
  - **Echtzeit-Überwachung**: Sendet Balance, Equity, Margin und offene Trades.
  - **Tick-Mode**: Schaltet automatisch auf tick-basierte Überwachung um, wenn ein Drawdown-Limit (50% des Max-Drawdowns) erreicht wird.
  - **Verbindungsmanagement**: Automatische Reconnects mit konfigurierbaren Intervallen und Retry-Logik (MaxReconnectAttempts), Heartbeats und "Not-Aus"-Erkennung.
  - **Historien-Export**: Inkrementelle Übertragung geschlossener Trades seit dem letzten Sync.
  - **State-Persistenz**: GlobalVariables (`TM_TradeListSent_{AccountID}`, `TM_LastSync_{AccountID}`) und Config-Datei (`TradeMonitorClient.cfg`).
  - **Status-Display**: Chart-Label mit Connected/Offline-Status, Account-ID, Sync-Status, Fehlercodes und Retry-Countdown.
- **Konfiguration**: Server-URL, Update-Intervalle, Heartbeat-Intervalle, Reconnect-Parameter, Magic Number Filter (Whitelist/Blacklist).

### 2.2 Java Spring Boot Server

Das Herzstück der Datenverarbeitung und -aufbereitung.

#### Controller (HTTP-Endpunkte)

| Controller | Pfad | Beschreibung |
|---|---|---|
| `ApiController` | `/api/*` | REST API für MetaTrader EA-Kommunikation |
| `DashboardController` | `/`, `/open-trades`, `/account/*` | Web-Views und AJAX-Endpoints |
| `AdminController` | `/admin/*` | Admin-Panel (erfordert ROLE_ADMIN) |
| `SecurityController` | `/login` | Login-Seite |
| `UserController` | `/profile/*` | Benutzerprofil und Passwortänderung |

#### Services (Geschäftslogik)

| Service | Beschreibung |
|---|---|
| `AccountManager` | In-Memory Account-Cache mit DB-Backing, Hauptlogik für Account- und Trade-Verwaltung |
| `TradeStorage` | DB-Persistenz für Trades, Equity-Snapshots (Rate-limitiert: 1x/Min, Auto-Cleanup > 90 Tage) |
| `TradeSyncService` | Überwacht Trade-Sync zwischen REAL- und DEMO-Accounts (jede Sekunde), Strict/Fallback Matching |
| `TradeComparisonService` | Vergleicht REAL vs. DEMO Closed Trades: Slippage, Delays, Matching |
| `OpenProfitAlarmService` | Überwacht Open Profit mit absoluten und prozentualen Schwellwerten (alle 5 Sek.) |
| `GlobalConfigService` | Zentrale Konfigurationsverwaltung (persistiert in DB) |
| `MagicMappingService` | Magic Number zu lesbarem Namen Mapping |
| `EmailService` | E-Mail-Alerts mit täglichem Rate-Limit |
| `HomeyService` | Homey Smart-Home Webhook-Integration (Sirene) |
| `UserService` | Benutzerverwaltung mit rollenbasiertem Zugriff |
| `LogCleanupService` | Tägliche Log-Bereinigung (2:00 Uhr, konfigurierbare Aufbewahrungsdauer) |

### 2.3 Web Dashboard

Das Frontend ist eine Server-Side-Rendered Webanwendung (Thymeleaf) mit dynamischen JavaScript-Modulen.

- **Dashboard (`/`)**: Dynamisches Kachel-Layout mit Sektionen, Drag & Drop, Report-Kacheln (Tages-/Wochen-/Monatscharts), Echtzeit-Status-Indikatoren, Open-Profit-Alarm-Banner.
- **Offene Trades (`/open-trades`)**: Konsolidierte Ansicht aller offenen Positionen, sortiert nach Account-Typ (REAL zuerst).
- **Account-Details (`/account/{id}`)**: Überlagerter Balance/Equity-Chart, Performance pro Magic Number, Historien-Tabelle mit Zeitraum-Filter.
- **Berichte (`/report/{period}`)**: Tägliche, wöchentliche und monatliche Profit-Reports.
- **Trade-Vergleich (`/trade-comparison`)**: Analyse des Copy-Tradings zwischen REAL/DEMO mit Delay und Slippage.
- **Admin-Bereich (`/admin`)**: DB-Statistiken, Konfiguration (Live-Indicator, Mail, Logs, Security, Homey), Magic Mappings, Benutzerverwaltung, Sync-Ausnahmen.
- **Benutzerprofil (`/profile`)**: Passwortänderung.
- **Mobile Ansicht (`/mobile/drawdown`)**: Ranking nach Magic-Number-Drawdown.

---

## 3. Datenbankschema

Die H2-Datenbank wird automatisch per JPA erstellt und verwaltet.

| Tabelle | Entity | Beschreibung |
|---|---|---|
| `accounts` | `AccountEntity` | Account-Stammdaten: accountId, broker, currency, balance, equity, name, type (DEMO/REAL), Alarm-Konfiguration |
| `equity_snapshots` | `EquitySnapshotEntity` | Periodische Equity-Snapshots (1x/Min, 90 Tage Aufbewahrung) |
| `closed_trades` | `ClosedTradeEntity` | Geschlossene Trades (accountId + ticket = unique key) |
| `open_trades` | `OpenTradeEntity` | Aktuell offene Trades |
| `dashboard_sections` | `DashboardSectionEntity` | Dashboard-Sektionen mit Sortierung |
| `magic_mappings` | `MagicMappingEntity` | Magic Number zu Name Mapping |
| `global_config` | `GlobalConfigEntity` | Key-Value Konfigurationsspeicher |
| `users` | `UserEntity` | Benutzer mit BCrypt-Passwort, Rolle und Account-Berechtigungen |
| `request_logs` | `RequestLog` | HTTP-Request-Logs (neue IPs) |
| `client_logs` | `ClientLog` | MetaTrader Client-Aktionslogs |
| `login_logs` | `LoginLog` | Authentifizierungs-Logs |

### Account-Entity (wichtige Felder)

| Feld | Typ | Beschreibung |
|---|---|---|
| `accountId` | String (PK) | MetaTrader Account-ID |
| `broker` | String | Broker-Name |
| `currency` | String | Kontowährung |
| `balance` / `equity` | Double | Aktueller Kontostand / Equity |
| `name` | String | Anzeigename |
| `type` | String | DEMO oder REAL |
| `sectionId` | Long | Dashboard-Sektion |
| `displayOrder` | Integer | Sortierung im Dashboard |
| `openProfitAlarmEnabled` | Boolean | Alarm aktiv/inaktiv |
| `openProfitAlarmAbs` | Double | Absoluter Schwellwert (z.B. -5000) |
| `openProfitAlarmPct` | Double | Prozentualer Drawdown-Schwellwert (z.B. 10.0) |

---

## 4. Installation & Start

### 4.1 Server

1. **Voraussetzung**: Java JDK 17+ und Maven.
2. **Build**: `mvn clean package`
3. **Start**: `java -jar server/target/trade-monitor-server-0.12.0.jar`
4. **Zugriff**: `http://localhost:8080`

### 4.2 MetaTrader 5 Client

1. `TradeMonitorClient.mq5` in das Verzeichnis `MQL5/Experts/` kopieren.
2. Im MetaEditor kompilieren (F7).
3. In MT5 unter *Extras > Optionen > Experten* die URL `http://localhost:8080` erlauben.
4. Den EA auf einen Chart ziehen und "Auto-Trading" aktivieren.
5. Der Account registriert sich automatisch und erscheint im Dashboard.

---

## 5. Datenspeicherung & Backup

- Alle Daten werden lokal im Ordner `./data` relativ zum Server-Startpunkt gespeichert.
- Hauptdatei: `trademonitor.mv.db` (H2 Datenbank).
- **Backup**: Server stoppen, `.db`-Datei kopieren.
- **Reset**: Server stoppen, Datenbankdatei löschen.
- **H2-Konsole**: Erreichbar unter `/h2-console` (konfigurierbar im Admin-Bereich).

---

## 6. Konfiguration

### 6.1 application.properties

| Eigenschaft | Standardwert | Beschreibung |
|---|---|---|
| `server.port` | 8080 | Server-Port |
| `spring.datasource.url` | `jdbc:h2:file:./data/trademonitor` | Datenbankpfad |
| `account.timeout.seconds` | 60 | Timeout für Offline-Markierung |
| `app.admin.username` | admin | Standard-Admin-Benutzername |
| `app.admin.password` | password | Standard-Admin-Passwort |

### 6.2 Dynamische Konfiguration (Admin-Panel)

Alle folgenden Einstellungen werden in der DB gespeichert und sind über das Admin-Panel konfigurierbar:

- **Live-Indicator**: Schwellwerte und Farben für Online-Status (grün/gelb/orange/rot)
- **E-Mail**: SMTP-Host, Port, Benutzer, Passwort, Absender, Empfänger, tägliches Limit
- **Log-Aufbewahrung**: Konfigurierbare Tage für Login-, Verbindungs- und Client-Logs
- **Security**: Rate-Limiting, Brute-Force-Schutz, Security-Headers, Max Sessions, H2-Konsole
- **Homey**: Webhook-ID, Event-Name, Trigger-Konfiguration, Alarm-Delay
- **Sync-Ausnahmen**: Magic Numbers die vom Sync-Check ausgenommen werden

---

## 7. Entwicklung

### 7.1 Architektur-Patterns

- **Backend**: Striktes Controller-Service-Repository Pattern
- **In-Memory Cache**: AccountManager cached Accounts für schnellen Dashboard-Zugriff
- **Scheduled Tasks**: TradeSyncService (1s), OpenProfitAlarmService (5s), LogCleanupService (täglich 2:00)
- **Duplicate Detection**: Trades eindeutig über (accountId, ticket)
- **Role-Based Access Control**: UserEntity.allowedAccountIds für benutzerspezifische Account-Sichtbarkeit

### 7.2 Frontend

- **Template Engine**: Thymeleaf (Server-Side Rendering)
- **Charts**: Chart.js (CDN)
- **CSS**: Vanilla CSS mit CSS Custom Properties (Dark Mode)
- **Kein Build-Tool**: Kein Webpack, kein npm
- **JS-Pattern**: Klassen als Function-Konstruktoren

### 7.3 Projektstruktur

```
MqlTradeMonitor/
├── server/                          # Spring Boot Java Backend
│   ├── pom.xml
│   ├── data/                        # H2-Datenbankdateien
│   └── src/main/
│       ├── java/de/trademonitor/
│       │   ├── TradeMonitorApplication.java
│       │   ├── controller/          # 5 Controller
│       │   ├── service/             # 11 Services
│       │   ├── entity/              # 11 JPA Entities
│       │   ├── repository/          # 11 Spring Data Repositories
│       │   ├── dto/                 # 9 Request/Response DTOs
│       │   ├── model/               # 3 In-Memory Modelle
│       │   ├── security/            # UserDetails, BruteForce
│       │   └── config/              # Security, Filter, Events
│       └── resources/
│           ├── templates/           # 12 Thymeleaf Templates
│           ├── static/              # CSS, JS
│           └── application.properties
├── mql5/
│   └── TradeMonitorClient.mq5       # MQL5 Expert Advisor
├── mql4/                            # MQL4 EA (Legacy)
└── Doku/                            # Dokumentation
```

---

## 8. Wichtige Konventionen

- **Equity vs. Balance**: Equity = Balance + offene P/L (kommt direkt vom EA)
- **Balance-Kurve**: Rekonstruiert aus geschlossenen Trades rückwärts vom aktuellen Balance
- **Equity-Kurve**: Gespeicherte Snapshots aus `equity_snapshots` (max. 1x/Min.)
- **Magic Numbers**: Identifizieren Strategien/EAs; können mit Namen versehen werden
- **Sync-Status**: MATCHED, WARNING, EXEMPTED
- **Filter-Ranges**: today, 1week, 1month, thismonth, 6months, thisyear, 1year, all
- **LocalStorage**: `tradeMonitor_filter_{accountId}` speichert den letzten Filter
