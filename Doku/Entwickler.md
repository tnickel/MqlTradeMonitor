# Entwicklerdokumentation: MQL Trade Monitor

## 1. Projektstruktur

```
MqlTradeMonitor/
├── server/                              # Spring Boot Backend
│   ├── pom.xml                          # Maven Build-Konfiguration
│   ├── data/                            # H2-Datenbankdateien (Runtime)
│   └── src/main/
│       ├── java/de/trademonitor/
│       │   ├── TradeMonitorApplication.java   # Spring Boot Entry Point
│       │   ├── controller/              # HTTP-Endpunkte (5 Controller)
│       │   │   ├── ApiController.java         # REST API für MetaTrader EA
│       │   │   ├── DashboardController.java   # Web-Views + AJAX
│       │   │   ├── AdminController.java       # Admin-Panel (ROLE_ADMIN)
│       │   │   ├── SecurityController.java    # Login-Seite
│       │   │   └── UserController.java        # Benutzerprofil
│       │   ├── service/                 # Geschäftslogik (11 Services)
│       │   │   ├── AccountManager.java        # In-Memory Cache + Hauptlogik
│       │   │   ├── TradeStorage.java          # DB-Persistenz
│       │   │   ├── TradeSyncService.java      # REAL/DEMO Sync-Check
│       │   │   ├── TradeComparisonService.java# Trade-Vergleich
│       │   │   ├── OpenProfitAlarmService.java# Open-Profit-Alarm
│       │   │   ├── GlobalConfigService.java   # Konfigurationsverwaltung
│       │   │   ├── MagicMappingService.java   # Magic Number Mapping
│       │   │   ├── EmailService.java          # E-Mail-Versand
│       │   │   ├── HomeyService.java          # Smart-Home-Integration
│       │   │   ├── UserService.java           # Benutzerverwaltung
│       │   │   └── LogCleanupService.java     # Log-Bereinigung
│       │   ├── entity/                  # JPA Entities (11 Tabellen)
│       │   │   ├── AccountEntity.java
│       │   │   ├── EquitySnapshotEntity.java
│       │   │   ├── ClosedTradeEntity.java
│       │   │   ├── OpenTradeEntity.java
│       │   │   ├── DashboardSectionEntity.java
│       │   │   ├── MagicMappingEntity.java
│       │   │   ├── GlobalConfigEntity.java
│       │   │   ├── UserEntity.java
│       │   │   ├── RequestLog.java
│       │   │   ├── ClientLog.java
│       │   │   └── LoginLog.java
│       │   ├── repository/              # Spring Data JPA Repos (11)
│       │   ├── dto/                     # Request/Response DTOs (9)
│       │   │   ├── RegisterRequest.java
│       │   │   ├── TradeInitRequest.java
│       │   │   ├── TradeUpdateRequest.java
│       │   │   ├── HeartbeatRequest.java
│       │   │   ├── HistoryUpdateRequest.java
│       │   │   ├── MagicDrawdownItem.java
│       │   │   ├── MagicProfitEntry.java
│       │   │   ├── TradeComparisonDto.java
│       │   │   └── AccountDbStats.java
│       │   ├── model/                   # In-Memory Modelle
│       │   │   ├── Account.java
│       │   │   ├── Trade.java
│       │   │   └── ClosedTrade.java
│       │   ├── security/               # Spring Security
│       │   │   ├── CustomUserDetails.java
│       │   │   ├── CustomUserDetailsService.java
│       │   │   └── BruteForceProtectionService.java
│       │   └── config/                  # Konfiguration & Filter
│       │       ├── SecurityConfig.java
│       │       ├── SecurityHeadersFilter.java
│       │       ├── RateLimitFilter.java
│       │       ├── RequestLoggingFilter.java
│       │       └── AuthenticationEvents.java
│       └── resources/
│           ├── templates/               # Thymeleaf HTML (12 Templates)
│           ├── static/css/style.css     # CSS (Dark Mode)
│           ├── static/js/global-warnings.js
│           └── application.properties
├── mql5/
│   └── TradeMonitorClient.mq5           # MQL5 Expert Advisor
├── mql4/                                # MQL4 EA (Legacy)
├── mcp-server/                          # Model Context Protocol (MCP) Server
│   ├── src/                             # TypeScript Quellcode (API-Bridge)
│   └── tsconfig.json                    # TS Konfiguration
└── Doku/                                # Dokumentation
```

---

## 2. Architektur-Pattern

### 2.1 Controller-Service-Repository

```
Controller (HTTP) → Service (Logik) → Repository (DB)
                                    → Model (In-Memory)
```

- **Controller**: Nehmen HTTP-Requests entgegen, delegieren an Services.
- **Services**: Geschäftslogik, Validierung, Orchestrierung.
- **Repositories**: Spring Data JPA Interfaces für DB-Zugriff.
- **Modelle**: In-Memory Repräsentation (Account, Trade, ClosedTrade).
- **Entities**: JPA-annotierte Klassen für DB-Persistenz.

### 2.2 In-Memory Cache mit DB-Backing

Der `AccountManager` hält alle Accounts im Speicher für schnellen Dashboard-Zugriff:

1. **Startup**: Accounts werden aus der DB geladen.
2. **Updates**: Änderungen werden sofort im Cache und asynchron in der DB aktualisiert.
3. **Reads**: Dashboard liest immer aus dem Cache (keine DB-Abfrage).

### 2.3 Scheduled Tasks

| Service | Intervall | Aufgabe |
|---|---|---|
| `TradeSyncService` | 1 Sekunde | Sync-Check REAL vs. DEMO |
| `OpenProfitAlarmService` | 5 Sekunden | Open-Profit-Schwellwert prüfen |
| `LogCleanupService` | Täglich 2:00 Uhr | Alte Logs bereinigen |

### 2.4 Equity-Snapshots
Anstatt bei jedem Seitenaufruf den Profit neu zu berechnen, sendet der MetaTrader regelmäßige Equity-Werte. Der Server berechnet dann den Profit anhand der Differenz zwischen aktueller Equity und den Balance-Operationen (Deposits/Withdrawals).

### 2.5 Asynchrone Datenverarbeitung & Server Health
Um die initiale Ladezeit des Dashboards zu minimieren, werden rechenintensive Operationen (wie z.B. Trade History oder Chart-Rendern) asynchron via Fetch-API in JavaScript geladen (Endpunkte wie `/api/stats/system-status`, `/api/accounts/{id}/equity-history`).
**Wartungsmodus (`NetworkStatusService`):** Der Server überwacht selbstständig das letzte Änderungsdatum seiner WAR-Datei. Wird die Datei durch einen Hot-Deploy aktualisiert, schaltet das System für 20 Minuten (konfigurierbar) in den Zustand `MAINTENANCE`. Dieses Event wird über `networkStatusLogRepository` persistiert und über den Endpunkt `/admin/api/network-timeline` chronologisch an das Frontend ausgeliefert.

---

## 3. Datenmodell

### 3.1 Account (In-Memory)

```java
class Account {
    String accountId;          // PK
    String broker, currency, name, type; // DEMO/REAL
    double balance, equity;
    LocalDateTime lastSeen, registeredAt;
    Long sectionId;
    Integer displayOrder;
    int magicNumberMaxAge;     // Tage für Profit-Kurven
    int magicMinTrades;

    // Alarm
    boolean openProfitAlarmEnabled;
    Double openProfitAlarmAbs;  // z.B. -5000
    Double openProfitAlarmPct;  // z.B. 10.0
    boolean openProfitAlarmTriggered;

    // Sync
    boolean syncWarning;
    Map<Long, String> syncStatusMap; // ticket -> status

    // Error
    String lastErrorMsg;
    LocalDateTime lastErrorTime;

    // Trades
    List<Trade> openTrades;
    List<ClosedTrade> closedTrades;
}
```

### 3.2 Trade (In-Memory)

```java
class Trade {
    long ticket;
    String symbol, type;       // BUY/SELL
    double volume, openPrice;
    String openTime;
    double stopLoss, takeProfit;
    double profit, swap;
    long magicNumber;
    String comment;
    String syncStatus;         // MATCHED/WARNING/EXEMPTED (transient)
}
```

### 3.3 ClosedTrade (In-Memory)

```java
class ClosedTrade {
    long ticket;
    String symbol, type;
    double volume, openPrice, closePrice;
    String openTime, closeTime;
    double profit, swap, commission;
    long magicNumber;
    String comment;
    Double sl;                 // nullable
}
```

---

## 4. Trade-Sync Algorithmus

Der `TradeSyncService` vergleicht REAL- und DEMO-Trades in zwei Stufen:

### Stufe 1: Strict Match
- Symbol muss übereinstimmen
- Typ (BUY/SELL) muss übereinstimmen
- Open Time innerhalb von 60 Sekunden Toleranz

### Stufe 2: Fallback Match
- Symbol muss übereinstimmen
- Typ muss übereinstimmen
- StopLoss muss exakt übereinstimmen

### Ergebnis pro Trade
- **MATCHED**: Auf REAL und DEMO vorhanden
- **WARNING**: Nur auf einem Account gefunden
- **EXEMPTED**: Magic Number ist in den Sync-Ausnahmen konfiguriert

---

## 5. Trade-Vergleich (Slippage & Delay)

Der `TradeComparisonService` analysiert geschlossene Trades:

### Matching
1. Symbol + Typ + Open Time (innerhalb 120 Sekunden) ODER
2. Symbol + Typ + StopLoss (exakt)

### Berechnete Metriken
- **Open Delay**: Differenz der Open-Times in Sekunden (Real - Demo)
- **Close Delay**: Differenz der Close-Times in Sekunden
- **Open Slippage**: Preisdifferenz bei Eröffnung (normalisiert für BUY/SELL)
- **Close Slippage**: Preisdifferenz bei Schließung

---

## 6. Magic-Number Profit-Berechnung

Der `AccountManager` berechnet die Performance pro Magic Number:

```
MagicProfitEntry {
    magicNumber, magicName
    openProfit       // Summe offener Trades
    closedProfit     // Summe geschlossener Trades
    totalProfit      // open + closed
    openTradeCount, closedTradeCount
    totalSwap, totalCommission
    tradedSymbols    // Map<Symbol, Count>
    maxDrawdownEur, maxDrawdownPercent
    maxEquityDrawdownEur, maxEquityDrawdownPercent
}
```

---

## 7. Frontend-Entwicklung

### 7.1 Template-Übersicht

| Template | URL | Beschreibung |
|---|---|---|
| `login.html` | `/login` | Login-Formular |
| `dashboard.html` | `/` | Hauptdashboard mit Kacheln |
| `open-trades.html` | `/open-trades` | Offene Trades aller Accounts |
| `account-detail.html` | `/account/{id}` | Account-Detailansicht |
| `report.html` | `/report/{period}` | Berichte |
| `trade-comparison.html` | `/trade-comparison` | Trade-Vergleich |
| `admin.html` | `/admin` | Admin-Panel |
| `admin-logs.html` | `/admin/logs` | Login-Logs |
| `admin-requests.html` | `/admin/requests` | Request-Logs |
| `admin-client-logs.html` | `/admin/client-logs` | Client-Logs |
| `profile.html` | `/profile` | Benutzerprofil |
| `mobile-drawdown.html` | `/mobile/drawdown` | Mobile Drawdown |

### 7.2 CSS-Architektur

- Vanilla CSS mit CSS Custom Properties (Variables).
- Dark Mode als Standard-Theme.
- Datei: `src/main/resources/static/css/style.css`

### 7.3 JavaScript

- Chart.js (via CDN) für Charts.
- Vanilla JavaScript mit Function-Konstruktor-Pattern.
- `global-warnings.js` für globale Alarm-Banner.
- Inline-JavaScript in Templates für seitenspezifische Logik.

---

## 8. Konfigurationssystem

### 8.1 Statisch (application.properties)

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

### 8.2 Dynamisch (GlobalConfigEntity)

Key-Value-Speicher in der DB. Alle Werte sind über das Admin-Panel editierbar:

| Kategorie | Schlüssel-Beispiele |
|---|---|
| Live-Indicator | `LIVE_GREEN_MINS`, `LIVE_COLOR_GREEN` |
| E-Mail | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_FROM`, `MAIL_TO` |
| Logging | `LOG_LOGIN_DAYS`, `LOG_CONN_DAYS`, `LOG_CLIENT_DAYS` |
| Security | `SEC_RATE_LIMIT_ENABLED`, `SEC_BRUTE_FORCE_MAX_ATTEMPTS` |
| Homey | `HOMEY_ID`, `HOMEY_EVENT`, `HOMEY_TRIGGER_SYNC` |
| Sync | `SYNC_EXEMPT_MAGIC_NUMBERS` |

---

## 9. Build & Deployment

### 9.1 Voraussetzungen

- Java JDK 17+
- Maven (oder Eclipse IDE)

### 9.2 Build

```bash
cd server
mvn clean package
```

### 9.3 Start

```bash
java -jar target/trade-monitor-server-0.12.0.jar
```

### 9.4 Konfiguration via Umgebungsvariablen

Spring Boot erlaubt die Überschreibung aller Properties:
```bash
java -jar target/trade-monitor-server-0.12.0.jar \
  --server.port=9090 \
  --app.admin.password=sicheres-passwort
```

### 9.5 MCP Server Build & Konfiguration

Der MCP-Server läuft lokal als Sidecar-Prozess in der Claude Desktop App und kommuniziert mit dem TradeMonitor-Server.

**Build:**
```bash
cd mcp-server
npm install
npm run build
```

**Konfiguration (claude_desktop_config.json):**
Die Zugangsdaten und die Server-URL werden in der Claude-Konfiguration oder in der `.env` Datei (`mcp-server/.env`) abgelegt.
```json
"mqltrademonitor": {
  "command": "node",
  "args": ["D:\\Pfad\\mcp-server\\build\\index.js"],
  "env": {
    "TRADEMONITOR_URL": "https://monitor.deine-domain.de",
    "TRADEMONITOR_USERNAME": "admin",
    "TRADEMONITOR_PASSWORD": "admin_password"
  }
}
```

---

## 10. Datenbank

### 10.1 H2 File-basiert

- Datei: `./data/trademonitor.mv.db`
- Auto-Schema via JPA (`ddl-auto=update`)
- H2-Konsole: `http://localhost:8080/h2-console`

### 10.2 Backup

```bash
# Server stoppen, dann:
cp data/trademonitor.mv.db data/trademonitor_backup.mv.db
```

### 10.3 Reset

```bash
# Server stoppen, dann:
rm data/trademonitor.mv.db
# Beim nächsten Start wird die DB neu erstellt
```

---

## 11. Häufige Entwickler-Aufgaben

### Neuen API-Endpunkt hinzufügen

1. DTO in `dto/` erstellen (falls nötig).
2. Methode im passenden Controller anlegen.
3. Service-Methode implementieren.
4. Repository-Query hinzufügen (falls DB-Zugriff nötig).

### Neue Konfiguration hinzufügen

1. Schlüssel und Default-Wert in `GlobalConfigService` definieren.
2. Getter-/Setter-Methode in `GlobalConfigService` hinzufügen.
3. Formular-Feld in `admin.html` ergänzen.
4. POST-Handler in `AdminController` anpassen.

### Neue Entity/Tabelle hinzufügen

1. Entity-Klasse in `entity/` mit JPA-Annotationen erstellen.
2. Repository-Interface in `repository/` erstellen (extends `JpaRepository`).
3. Service-Klasse erstellen (falls Geschäftslogik nötig).
4. Tabelle wird beim nächsten Start automatisch erstellt (`ddl-auto=update`).

---

## 12. Wichtige Konventionen

- **Equity vs. Balance**: Equity = Balance + offene P/L.
- **Balance-Kurve**: Rückwärts rekonstruiert aus geschlossenen Trades.
- **Equity-Kurve**: Periodische Snapshots (1x/Min, 90 Tage).
- **Unique Keys**: Trades = (accountId, ticket).
- **Zeitformat**: ISO-8601 Strings in DTOs, LocalDateTime intern.
- **Filter-Ranges**: today, 1week, 1month, thismonth, 6months, thisyear, 1year, all.
