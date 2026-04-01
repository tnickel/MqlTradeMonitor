# API-Referenz: MQL Trade Monitor

## 1. MetaTrader EA API (`/api/*`)

Diese Endpunkte werden vom MQL5 Expert Advisor aufgerufen. CSRF ist für `/api/**` deaktiviert.

### POST `/api/register`

Registriert einen neuen Account oder aktualisiert einen bestehenden.

**Request Body:**
```json
{
  "accountId": "12345678",
  "broker": "ICMarkets",
  "currency": "EUR",
  "balance": 10000.00,
  "timestamp": "2024-01-15T10:30:00"
}
```

**Response:** `200 OK` mit Bestätigungstext.

---

### POST `/api/trades-init`

Initialer vollständiger Trade-Upload beim ersten Verbinden oder Reconnect. Enthält sowohl offene als auch geschlossene Trades.

**Request Body:**
```json
{
  "accountId": "12345678",
  "equity": 10500.00,
  "balance": 10000.00,
  "timestamp": "2024-01-15T10:30:00",
  "trades": [
    {
      "ticket": 100001,
      "symbol": "EURUSD",
      "type": "BUY",
      "volume": 0.1,
      "openPrice": 1.08500,
      "openTime": "2024-01-15T09:00:00",
      "stopLoss": 1.08000,
      "takeProfit": 1.09000,
      "profit": 50.00,
      "swap": -1.20,
      "magicNumber": 12345,
      "comment": "Gold Scalper"
    }
  ],
  "closedTrades": [
    {
      "ticket": 100000,
      "symbol": "EURUSD",
      "type": "BUY",
      "volume": 0.1,
      "openPrice": 1.08000,
      "closePrice": 1.08500,
      "openTime": "2024-01-14T09:00:00",
      "closeTime": "2024-01-14T15:00:00",
      "profit": 50.00,
      "swap": -0.80,
      "commission": -0.70,
      "magicNumber": 12345,
      "comment": "Gold Scalper",
      "sl": 1.07500
    }
  ]
}
```

**Response:** `200 OK`

**Hinweis:** Geschlossene Trades werden mit Duplikat-Prüfung gespeichert (accountId + ticket = unique key).

---

### POST `/api/trades`

Inkrementelles Trade-Update nach der Initialisierung. Enthält nur offene Trades.

**Request Body:**
```json
{
  "accountId": "12345678",
  "equity": 10550.00,
  "balance": 10000.00,
  "timestamp": "2024-01-15T10:35:00",
  "trades": [
    {
      "ticket": 100001,
      "symbol": "EURUSD",
      "type": "BUY",
      "volume": 0.1,
      "openPrice": 1.08500,
      "openTime": "2024-01-15T09:00:00",
      "stopLoss": 1.08000,
      "takeProfit": 1.09000,
      "profit": 55.00,
      "swap": -1.20,
      "magicNumber": 12345,
      "comment": "Gold Scalper"
    }
  ]
}
```

**Response:** `200 OK`

---

### POST `/api/heartbeat`

Keep-Alive-Signal vom MetaTrader Client. Aktualisiert den `lastSeen`-Zeitstempel.

**Request Body:**
```json
{
  "accountId": "12345678",
  "timestamp": "2024-01-15T10:36:00"
}
```

**Response:** `200 OK`

---

### POST `/api/history`

Inkrementelles Update geschlossener Trades (Historien-Synchronisation).

**Request Body:**
```json
{
  "accountId": "12345678",
  "timestamp": "2024-01-15T10:40:00",
  "closedTrades": [
    {
      "ticket": 100002,
      "symbol": "GBPUSD",
      "type": "SELL",
      "volume": 0.05,
      "openPrice": 1.27000,
      "closePrice": 1.26800,
      "openTime": "2024-01-15T08:00:00",
      "closeTime": "2024-01-15T10:00:00",
      "profit": 10.00,
      "swap": 0.00,
      "commission": -0.35,
      "magicNumber": 67890,
      "comment": "Trend Follower",
      "sl": 1.27500
    }
  ]
}
```

**Response:** `200 OK`

**Hinweis:** Duplikate (gleiche accountId + ticket) werden automatisch übersprungen.

---

### GET `/api/accounts`

Gibt alle Accounts mit aktuellem Status als JSON zurück (für AJAX-Updates im Dashboard).

**Response:**
```json
[
  {
    "accountId": "12345678",
    "broker": "ICMarkets",
    "currency": "EUR",
    "balance": 10000.00,
    "equity": 10550.00,
    "name": "Mein Hauptkonto",
    "type": "REAL",
    "online": true,
    "lastSeen": "2024-01-15T10:36:00",
    "openTradeCount": 3,
    "todayProfit": 125.50
  }
]
```

---

### POST `/api/test-email`

Sendet eine Test-E-Mail mit der aktuellen SMTP-Konfiguration.

**Response:** `200 OK` bei Erfolg, Fehlerdetails bei Misserfolg.

---

### REST API Auth Endpunkte (`/api/login`, `/api/demo-login`, `/api/logout`)

Diese Endpunkte dienen explizit externen Headless-Clients (wie der Replit App), um sich per REST/JSON anzumelden, ohne HTML-Seiten für CSRF-Tokens parsen zu müssen. Sie geben das `JSESSIONID` Cookie im Set-Cookie Header zurück.
Weitere Details hierzu befinden sich in der Dokumentation unter `replit/Replit_Erweiterung.md`.

---

## 2. Dashboard-Endpunkte

### GET `/`

Hauptdashboard. Zeigt alle Accounts gruppiert nach Sektionen. Erfordert Authentifizierung.

### GET `/open-trades`

Übersicht aller offenen Trades über alle Accounts. Erfordert Authentifizierung.

### GET `/account/{id}`

Detailansicht eines einzelnen Accounts. Erfordert Authentifizierung.

### GET `/report/{period}`

Trading-Berichte. `period` kann sein: `daily`, `weekly`, `monthly`.

### GET `/trade-comparison`

Trade-Vergleichsansicht zwischen REAL- und DEMO-Accounts.

### GET `/mobile/drawdown`

Mobile Drawdown-Übersicht (öffentlich zugänglich).

---

## 3. AJAX/JSON-Endpunkte

### GET `/api/equity-history/{accountId}`

Equity-Snapshots für den Chart eines Accounts.

**Response:** Array von Equity-Datenpunkten mit Zeitstempel, Equity und Balance.

---

### GET `/api/stats/magic-drawdowns`

Drawdown-Statistiken pro Magic Number über alle Accounts.

**Response:** Array von `MagicDrawdownItem` mit Account-Info, Magic Number, Drawdown in EUR und Prozent.

---

### GET `/api/report-chart/{period}`

Aggregierte Profit-Daten für Tages-/Wochen-/Monatscharts.

**Parameter:** `period` = `daily`, `weekly`, `monthly`

---

### POST `/api/mapping`

Magic Number Mapping per AJAX aktualisieren.

**Parameter:** `magicNumber` (Long), `customComment` (String)

---

### POST `/api/account/update`

Account-Details per AJAX aktualisieren (Name, Typ, Alarm-Konfiguration).

---

### POST `/api/test-siren`

Test der Homey-Sirene-Integration.

---

## 4. Admin-Endpunkte (erfordert ROLE_ADMIN)

### GET `/admin`

Admin-Dashboard mit Account-Statistiken, Konfigurationsformularen und Benutzerverwaltung.

### POST `/admin/create-user`

Neuen Benutzer erstellen.

**Parameter:** `username`, `password`, `role` (ROLE_ADMIN/ROLE_USER), `allowedAccountIds` (optional)

### POST `/admin/update-user-accounts`

Account-Berechtigungen eines Benutzers aktualisieren.

**Parameter:** `userId` (Long), `allowedAccountIds` (optional)

### POST `/admin/delete-user`

Benutzer löschen.

**Parameter:** `userId` (Long)

### POST `/admin/sync-exemptions`

Sync-Ausnahmen setzen (Magic Numbers die vom Sync-Check ausgenommen werden).

**Parameter:** `magicNumbers` (String, kommagetrennt)

### POST `/admin/live-config`

Live-Indicator-Schwellwerte und Farben konfigurieren.

**Parameter:** `liveGreenMins`, `liveYellowMins`, `liveOrangeMins`, `liveColorGreen`, `liveColorYellow`, `liveColorOrange`, `liveColorRed`

### POST `/admin/log-retention`

Log-Aufbewahrungsdauer konfigurieren.

**Parameter:** `logLoginDays`, `logConnDays`, `logClientDays`

### POST `/admin/security`

Sicherheitseinstellungen konfigurieren.

**Parameter:** `secRateLimitEnabled`, `secRateLimitPerMin`, `secBruteForceEnabled`, `secBruteForceMaxAttempts`, `secBruteForceLockoutMins`, `secHeadersEnabled`, `secMaxSessions`, `secH2ConsoleEnabled`

### GET `/admin/logs`

Login-/Authentifizierungs-Logs anzeigen.

### GET `/admin/requests`

HTTP-Request-Logs anzeigen.

### GET `/admin/client-logs`

Client-Aktionslogs anzeigen.

**Optional:** `?accountId=12345678` zum Filtern nach Account.

---

## 5. Benutzer-Endpunkte

### GET `/profile`

Benutzerprofil anzeigen.

### POST `/profile/change-password`

Passwort ändern.

**Parameter:** `newPassword`, `confirmPassword`

---

## 6. Authentifizierung

### GET `/login`

Login-Seite.

### POST `/login`

Form-basierter Login (Spring Security). Brute-Force-Schutz aktiv.

### POST `/logout`

Abmelden und Sitzung beenden.

---

## 7. Öffentliche Ressourcen

Die folgenden Pfade sind ohne Authentifizierung zugänglich:
- `/api/**` - MetaTrader EA API
- `/login` - Login-Seite
- `/css/**`, `/js/**` - Statische Ressourcen
- `/mobile/**` - Mobile Ansichten
