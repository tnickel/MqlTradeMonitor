# MqlTradeMonitor API Dokumentation für Replit AI

Diese Dokumentation beschreibt die Schnittstellen (API) des MqlTradeMonitor-Servers. Replit AI kann diese Informationen nutzen, um eine eigenständige App (z.B. Web oder Mobile) zu bauen, die sich am Server anmeldet und die Handelsdaten (Accounts, offene Trades, Historie) anzeigt.

## 1. Basis-Informationen

- **Sicherheitskonzept**: Das Backend nutzt **Spring Security** mit sitzungsbasierter Authentifizierung (Cookie `JSESSIONID`).
- **CSRF-Schutz**: Formular-Logins und Web-Endpoints sind durch CSRF-Tokens geschützt. REST-Aufrufe unter `/api/**` haben die CSRF-Prüfung deaktiviert, erfordern aber weiterhin eine gültige Sitzung (Cookie).
- **Datenformat**: Die API-Antworten sind im JSON-Format.

---

## 2. Authentifizierung (Login-Flow)

Da die App sich wie ein normaler Web-Benutzer verhalten muss, ist folgender Flow in der App zu implementieren:

### Schritt 2.1: CSRF-Token holen
Beende einen normalen GET-Aufruf auf die Login-Seite, um das CSRF-Token zu parsen (da Spring Security es im Standard-Setup in die HTML-Response einbettet).

- **Request**: `GET /login`
- **Aktion in App**: Suche im zurückgegebenen HTML-Code nach dem versteckten Input-Feld für das CSRF-Token und extrahiere den Wert. 
  Beispiel: `<input type="hidden" name="_csrf" value="HIER_IST_DAS_TOKEN">`
- **Wichtig**: Speichere das ebenfalls zurückgegebene `JSESSIONID`-Cookie aus dem Response-Header (`Set-Cookie`)!

### Schritt 2.2: Login durchführen
Führe den eigentlichen Login über einen POST-Request durch.

- **Request**: `POST /login`
- **Content-Type**: `application/x-www-form-urlencoded`
- **Cookies**: Übergebe das `JSESSIONID`-Cookie aus Schritt 2.1.
- **Body-Parameter**:
  - `username`: Der Benutzername
  - `password`: Das Passwort
  - `_csrf`: Das in Schritt 2.1 extrahierte CSRF-Token
- **Ergebnis**: Bei einem HTTP Status 302 (Found/Redirect) zu `/` (oder 200 OK) war der Login erfolgreich. Die Session im `JSESSIONID`-Cookie ist nun authentifiziert.

> **Merke:** Für alle folgenden REST-API-Aufrufe (Schritt 3) MUSS dieses authentifizierte `JSESSIONID`-Cookie in den Request-Headern mitgesendet werden. Da alle Daten-Endpunkte unter `/api/` liegen (wo CSRF deaktiviert ist), muss kein CSRF-Token mehr mitgeschickt werden, sondern nur das Cookie.

---

## 3. Daten abrufen (REST API)

Sobald die App (bzw. der Nutzer) eingeloggt ist, können folgende GET-Endpunkte aufgerufen werden, um die Daten als JSON zu erhalten.

### 3.1 Konten (Accounts) abrufen
Gibt eine Liste aller im System registrierten Konten inklusive Status (Online, Equity, Balance etc.) zurück.

- **Request**: `GET /api/accounts`
- **Response**: Array von Account-Objekten.
- **Wichtige Felder (Beispiel)**:
  ```json
  [
    {
      "accountId": 123456,
      "broker": "RoboForex",
      "currency": "EUR",
      "balance": 10000.50,
      "equity": 10500.00,
      "online": true,
      "trades": 5, // Anzahl offene Trades
      ...
    }
  ]
  ```

### 3.2 Offene Trades (Open Trades) abrufen
Gibt alle derzeit offenen Trades zurück. Die Liste wird automatisch anhand der Berechtigungen des eingeloggten Nutzers gefiltert.

- **Request**: `GET /api/trades/open`
- **Response**: Array von Trade-Objekten.
- **Wichtige Felder (Beispiel)**:
  ```json
  [
    {
      "ticket": 987654321,
      "accountId": 123456,
      "symbol": "EURUSD",
      "type": "BUY",
      "volume": 0.1,
      "openPrice": 1.1050,
      "currentPrice": 1.1080,
      "profit": 30.00,
      "magicNumber": 123
    }
  ]
  ```

### 3.3 Geschlossene Trades (Historie / Report) abrufen
Liest die Historie der geschlossenen Trades für ein spezifisches Konto und einen definierten Zeitraum aus.

- **Request**: `GET /api/report-details/{period}/{accountId}`
- **Parameter**:
  - `period`: Zeitraum (z.B. `daily`, `weekly`, `monthly`, `yearly`, `all`)
  - `accountId`: Die ID des Kontos (z.B. `123456`)
- **Response**: Array von ClosedTrade-Objekten (absteigend sortiert nach Schließungszeitpunkt).
- **Beispiel**: `/api/report-details/all/123456`

### 3.4 Equity-Historie (Für Charts) abrufen
Holt die serverseitig gespeicherten Snapshots zum Verlauf von Equity und Balance (ideal für die Darstellung in Line-Charts).

- **Request**: `GET /api/equity-history/{accountId}`
- **Response**: Array aus Snapshots.
- **Beispiel-Daten**:
  ```json
  [
    {
      "timestamp": "2026-03-22T10:00:00",
      "equity": 10500.50,
      "balance": 10000.00
    }
  ]
  ```

### 3.5 Magische Drawdowns abrufen
Eine aggregierte Auswertung der Drawdowns, gruppiert nach Magic-Nutzer über alle Konten hinweg.

- **Request**: `GET /api/stats/magic-drawdowns`
- **Response**: Array von MagicDrawdownItem-Objekten.

---

## 4. Anweisungen für Replit AI Core (Prompt-Hilfe)

Wenn du der Replit AI sagst, sie soll die App bauen, kannst du folgenden Prompt-/System-Befehl nutzen:

> "Replit AI, baue mir eine Frontend-Dashboard-App (z. B. mit React/Next.js oder Vue). Die App kommuniziert mit meinem MqlTradeMonitor Server. Nutze die Cookie-basierte Session-Authentifizierung von Spring Security. Du musst zunächst einen GET-Request auf `/login` machen, um das CSRF-Token (im HTML `_csrf`) und die `JSESSIONID` zu erhalten, dann einen POST auf `/login` mit form-urlencoded `username`, `password` und `_csrf`. Danach speicherst du das Sitzungscookie und rufst den Endpunkt `/api/accounts` auf, um die Konten anzuzeigen, sowie `/api/trades/open`, um die Liste der offenen Trades anzuzeigen."
