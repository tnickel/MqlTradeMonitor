# MqlTradeMonitor – Projektübersicht

> Diese Datei ist die erste Anlaufstelle für jede neue Session.
> Immer aktuell halten wenn sich Struktur oder Tools ändern.

---

## Build & Run

**Java:** Oracle JDK (verfügbar über `java.exe` im Windows PATH via `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`)
**Maven:** **KEIN `mvn` im PATH, KEIN `mvnw`!**
→ Build & Start ausschließlich über **Eclipse IDE** (Run as Spring Boot App) oder direkt über das JAR.

```
# Kein Maven-CLI verfügbar → Build immer über Eclipse starten
# Alternativ: JAR aus target/ direkt ausführen:
java -jar server/target/trademonitor-*.jar
```

**Spring Boot Server Port:** `8080` (Standard)
**Datenbankdatei:** `server/data/trademonitor.mv.db` (H2 File-DB)
→ Bei gesperrter DB-Datei: Task Manager → Java-Prozess beenden, dann neu starten.

---

## Projektstruktur

```
MqlTradeMonitor/
├── server/                          # Spring Boot Java Backend
│   ├── pom.xml
│   ├── data/                        # H2-Datenbankdateien (trademonitor.mv.db)
│   └── src/main/
│       ├── java/de/trademonitor/
│       │   ├── TradeMonitorApplication.java
│       │   ├── controller/
│       │   │   ├── ApiController.java         # REST API für MT-EA (Trades, Heartbeat)
│       │   │   ├── DashboardController.java   # Web-Views + AJAX-Endpoints
│       │   │   ├── AdminController.java       # Admin-Seiten
│       │   │   └── SecurityController.java    # Login
│       │   ├── service/
│       │   │   ├── AccountManager.java        # In-Memory Account-State, Hauptlogik
│       │   │   ├── TradeStorage.java          # DB-Persistenz für Trades, Equity-Snapshots
│       │   │   ├── TradeSyncService.java      # Scheduled sync / Heartbeat-Timeout
│       │   │   ├── GlobalConfigService.java   # Admin-Konfiguration
│       │   │   ├── MagicMappingService.java   # Magic-Number → Name/Kommentar Mapping
│       │   │   ├── HomeyService.java          # Homey Smart Home Integration
│       │   │   └── EmailService.java          # E-Mail Alerts
│       │   ├── entity/                        # JPA Entities (H2-Tabellen)
│       │   │   ├── AccountEntity.java         # Account (balance, equity, lastSeen)
│       │   │   ├── EquitySnapshotEntity.java  # Equity-Snapshots für Equity-Kurve
│       │   │   ├── ClosedTradeEntity.java     # Geschlossene Trades
│       │   │   ├── OpenTradeEntity.java       # Offene Trades
│       │   │   ├── DashboardSectionEntity.java# Dashboard-Sektionen (dynamisch)
│       │   │   ├── MagicMappingEntity.java    # Magic-Number Mappings
│       │   │   ├── GlobalConfigEntity.java    # Admin-Konfig in DB
│       │   │   ├── RequestLog.java            # HTTP-Request-Logs
│       │   │   ├── ClientLog.java             # Client-Verbindungs-Logs
│       │   │   └── LoginLog.java             # Login-Logs
│       │   ├── repository/                    # Spring Data JPA Repositories (10x)
│       │   ├── dto/                           # Request/Response DTOs (8x)
│       │   │   ├── TradeUpdateRequest.java    # POST /api/trades
│       │   │   ├── TradeInitRequest.java      # POST /api/trades-init
│       │   │   ├── HeartbeatRequest.java      # POST /api/heartbeat
│       │   │   └── MagicProfitEntry.java      # u.a. für Report-Charts
│       │   ├── model/                         # In-Memory Modelle (Account, Trade, ClosedTrade)
│       │   └── config/                        # Spring Security Config etc.
│       └── resources/
│           ├── templates/                     # Thymeleaf HTML-Templates
│           │   ├── dashboard.html             # Hauptdashboard (Kacheln/Kompakt/Real Focus)
│           │   ├── account-detail.html        # Account-Detail mit Balance+Equity Chart
│           │   ├── open-trades.html           # Alle offenen Trades (globale Übersicht)
│           │   ├── mobile-drawdown.html       # Mobile Drawdown-Übersicht
│           │   ├── report.html                # Berichte (täglich/wöchentlich/monatlich)
│           │   ├── admin.html                 # Admin-Panel
│           │   ├── admin-logs.html            # Login-Logs
│           │   ├── admin-requests.html        # Request-Logs
│           │   ├── admin-client-logs.html     # Client-Verbindungslogs
│           │   └── login.html                 # Login-Seite
│           └── application.properties        # Spring-Konfiguration
├── mql5/
│   └── TradeMonitorClient.mq5       # MQL5 EA – sendet Trades an den Server
├── mql4/                            # MQL4 EA (veraltet / für Referenz)
└── Doku/
    └── Projektbeschreibung.md       # Ausführliche Feature-Dokumentation
```

---

## Wichtige API-Endpoints (ApiController)

| Method | URL | Beschreibung |
|--------|-----|--------------|
| POST | `/api/register` | EA registriert sich (accountId, name, balance, equity) |
| POST | `/api/trades` | Regelmäßiger Trade-Update vom EA |
| POST | `/api/trades-init` | Initialer vollständiger Trade-Upload |
| POST | `/api/heartbeat` | Heartbeat (accountId, timestamp) |

## Wichtige AJAX/View-Endpoints (DashboardController)

| Method | URL | Beschreibung |
|--------|-----|--------------|
| GET | `/` | Dashboard |
| GET | `/account/{id}` | Account-Detail-Seite |
| GET | `/api/equity-history/{accountId}` | JSON: Equity-Snapshots für Chart |
| GET | `/api/stats/magic-drawdowns` | JSON: Drawdown-Statistiken |
| GET | `/open-trades` | Globale Offene-Trades-Übersicht |
| GET | `/report/{period}` | Berichte (daily/weekly/monthly) |
| GET | `/api/report-chart/{period}` | JSON: Aggregierte Profit-Daten für Tages/Wochen/Monatscharts |
| GET | `/mobile/drawdown` | Mobile Drawdown-Seite |
| GET | `/admin` | Admin-Panel |
| POST | `/admin/sync-exemptions` | Speichert Magic-Number-Ausnahmen für Synccheck |

---

## Datenbankschema (H2 – automatisch erstellt per JPA)

| Tabelle | Entity | Beschreibung |
|---------|--------|--------------|
| `accounts` | AccountEntity | Account-Stammdaten + aktuell balance/equity |
| `equity_snapshots` | EquitySnapshotEntity | Periodische Equity-Snapshots (1x/min, 90 Tage) |
| `closed_trades` | ClosedTradeEntity | Alle geschlossenen Trades |
| `open_trades` | OpenTradeEntity | Aktuell offene Trades |
| `dashboard_sections` | DashboardSectionEntity | Dynamische Dashboard-Sektionen |
| `magic_mappings` | MagicMappingEntity | Magic-Number → Name/Kommentar |
| `global_config` | GlobalConfigEntity | Admin-Einstellungen (u.a. `SYNC_EXEMPT_MAGIC_NUMBERS`) |
| `request_logs` | RequestLog | HTTP-Request-Logs |
| `client_logs` | ClientLog | Client-Verbindungslogs |
| `login_logs` | LoginLog | Authentifizierungs-Logs |

---

## Frontend-Stack

- **Template Engine:** Thymeleaf (server-side rendering)
- **Charts:** Chart.js (CDN)
- **CSS:** Vanilla CSS mit CSS-Custom-Properties (Dark Mode)
- **Kein Build-Tool** für Frontend (kein Webpack, kein npm)
- **JS-Pattern:** Klassen als `function`-Konstruktoren (z.B. `TableManager`, `HistoryChartManager`, `MagicChartManager`)

---

## Wichtige Konventionen

- **Equity ≠ Balance:** Equity = Balance + offene P/L (kommt direkt vom EA)
- **Balance-Kurve:** Rekonstruiert aus geschlossenen Trades rückwärts von aktuellem Balance
- **Equity-Kurve:** Gespeicherte Snapshots aus `equity_snapshots` Tabelle (max. 1x/Min.)
- **Magic Numbers:** Identifizieren Strategien/EAs; können in Admin mit Namen/Kommentaren versehen werden
- **Sync-Status:** Real-Trades erhalten nach Synccheck einen von 3 Stati: `MATCHED` ✅, `WARNING` ⚠️, `EXEMPTED` 🟠
- **Sync-Ausnahmen:** Magic Numbers im Admin unter "🟠 Synccheck Ausnahmen" eintragen → kein Alarm, Anzeige orange. Config-Key: `SYNC_EXEMPT_MAGIC_NUMBERS` (kommagetrennte Zahlen)
- **Sections:** Dashboard-Accounts sind in benannten Sektionen organisiert (DashboardSectionEntity)
- **Filter-Ranges:** `today`, `1week`, `1month`, `thismonth`, `6months`, `thisyear`, `1year`, `all`
- **LocalStorage Key:** `tradeMonitor_filter_{accountId}` speichert den letzten Zeitraum-Filter pro Account

---

## Häufige Fehler & Lösungen

| Problem | Lösung |
|---------|--------|
| DB gesperrt beim Start | Task Manager → Java-Prozess beenden |
| `mvn` nicht gefunden | Kein Maven im PATH — Start über Eclipse |
| EA kann nicht verbinden | MT5 → Tools → Optionen → Expert Advisors → URL des Servers erlauben |
| Equity-Kurve leer | Erst nach ~1 Minute Betrieb hat der Server Snapshots gesammelt |
