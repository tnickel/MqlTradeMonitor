# Sicherheitsdokumentation: MQL Trade Monitor

## 1. Übersicht

Der MQL Trade Monitor implementiert ein mehrschichtiges Sicherheitskonzept basierend auf Spring Security. Alle Sicherheitsfunktionen sind über das Admin-Panel konfigurierbar.

---

## 2. Authentifizierung & Autorisierung

### 2.1 Benutzerrollen

| Rolle | Beschreibung | Zugriff |
|---|---|---|
| `ROLE_ADMIN` | Administrator | Alle Seiten inkl. Admin-Bereich |
| `ROLE_USER` | Standardbenutzer | Dashboard, Trades, Reports (eingeschränkt auf zugewiesene Accounts) |

### 2.2 Passwort-Sicherheit

- Passwörter werden mit **BCrypt** gehasht und in der H2-Datenbank gespeichert (`UserEntity`).
- Standard-Admin-Account wird **nur beim allerersten Start** aus `application.properties` erstellt (wenn noch kein Admin existiert). Danach hat `app.admin.password` keine Wirkung mehr.
- Passwortänderung über `/profile/change-password` oder das Admin-Panel möglich.
- Das aktive Produktions-Passwort liegt ausschließlich als BCrypt-Hash in der H2-Datenbank.

---

## 12. Secrets & Zugangsdaten

### 12.1 Übersicht der Secrets

| Secret | Speicherort | Beschreibung |
|---|---|---|
| **Web-Admin-Passwort** | H2-Datenbank (BCrypt-Hash) | Login für `/admin` – wird über UI geändert |
| **H2-Datenbank** | `application.properties` | User: `sa`, Passwort: leer (nur lokal erreichbar) |
| **API-Key (X-User-Key)** | H2-Datenbank | Automatisch generiert, pro User, für EA-Authentifizierung |
| **SMTP-Zugangsdaten** | H2-Datenbank (GlobalConfig) | Konfiguriert über Admin-Panel |
| **Homey Webhook-ID** | H2-Datenbank (GlobalConfig) | Konfiguriert über Admin-Panel |

### 12.2 Was NICHT ins Git darf

Die folgenden Dateien/Informationen dürfen **niemals** ins Git-Repository gelangen:

- **Produktions-Passwörter** (Web-Login, SMTP, etc.)
- **API-Keys** (`X-User-Key`, Homey-ID)
- **H2-Datenbankdateien** (`*.mv.db`, `*.trace.db`) – enthalten alle Secrets als Hash/Klartext
- **Server-spezifische Konfiguration** (IP-Adressen, SSH-Keys)

Die `.gitignore` enthält bereits Regeln für Datenbankdateien (`*.db`).

### 12.3 Sicheres Deployment

Das Default-Passwort in `application.properties` ist `password` und dient nur der initialen Einrichtung. **Nach dem ersten Login** muss das Passwort über `/profile/change-password` geändert werden.

> **Wichtig:** Da die Spring Boot App innerhalb von WildFly läuft (nicht standalone), können Properties **nicht** per `--app.admin.password=...` Kommandozeile überschrieben werden. Das Passwort wird einmalig beim ersten Start verwendet und danach nur noch aus der Datenbank gelesen.

### 12.4 H2-Datenbank-Zugang

Die H2-Datenbank ist mit User `sa` und **leerem Passwort** konfiguriert. Dies ist akzeptabel, da:
- Die H2-Konsole (`/h2-console`) nur für eingeloggte Admins erreichbar ist
- Der H2 AUTO_SERVER-Modus nur auf `localhost` hört
- Der Server über Nginx als Reverse-Proxy abgesichert ist

---

## 13. Sicherheits-Checkliste für Produktionsbetrieb

1. Nach erstem Login das Admin-Passwort über `/profile/change-password` ändern.
2. HTTPS aktivieren (TLS-Zertifikat konfigurieren oder Reverse-Proxy).
3. H2-Konsole im Admin-Panel deaktivieren.
4. Brute-Force-Schutz aktiviert lassen.
5. Rate-Limiting mit angemessenen Werten konfigurieren.
6. Security-Headers aktiviert lassen.
7. Regelmäßig Login-Logs auf verdächtige Aktivitäten prüfen.
8. Log-Aufbewahrungsdauer an Compliance-Anforderungen anpassen.
9. API-Zugriff per Firewall/Reverse-Proxy einschränken.
10. Keine Produktions-Passwörter im Git-Repository speichern.

### 2.3 Account-Berechtigungen

- Pro Benutzer können erlaubte Account-IDs konfiguriert werden (`allowedAccountIds`).
- Benutzer ohne Einschränkung sehen alle Accounts.
- Benutzer mit zugewiesenen Account-IDs sehen nur diese Accounts im Dashboard.

### 2.4 URL-basierte Zugriffssteuerung

| Pfad | Zugriff |
|---|---|
| `/api/**` | Öffentlich (MetaTrader EA, kein CSRF) |
| `/login`, `/css/**`, `/js/**` | Öffentlich |
| `/mobile/**` | Öffentlich |
| `/admin/**` | Nur ROLE_ADMIN |
| Alle anderen Pfade | Authentifizierung erforderlich |

---

## 3. Brute-Force-Schutz

**Klasse:** `BruteForceProtectionService`

### Funktionsweise

- Verfolgt fehlgeschlagene Login-Versuche pro IP-Adresse.
- Nach Überschreitung der konfigurierten maximalen Fehlversuche wird die IP temporär gesperrt.
- Gesperrte IPs erhalten HTTP 429 (Too Many Requests) mit formatierter HTML-Fehlermeldung.
- Abgelaufene Sperreinträge werden automatisch alle 10 Minuten bereinigt.

### Konfiguration (Admin-Panel)

| Parameter | Beschreibung | Standard |
|---|---|---|
| `secBruteForceEnabled` | Schutz aktiviert/deaktiviert | true |
| `secBruteForceMaxAttempts` | Max. Fehlversuche vor Sperre | 5 |
| `secBruteForceLockoutMins` | Sperrdauer in Minuten | 15 |

### Logging

Jeder Login-Versuch (erfolgreich/fehlgeschlagen) wird in der `login_logs`-Tabelle protokolliert:
- Zeitstempel, Benutzername, IP-Adresse, Erfolg/Misserfolg, Details.

---

## 4. Rate-Limiting

**Klasse:** `RateLimitFilter` (Filter-Order: 1)

### Funktionsweise

- Per-IP Request-Rate-Limiting mit gleitendem 1-Minuten-Fenster.
- Überschreitungen werden mit HTTP 429 beantwortet.

### Ausnahmen

Folgende Pfade sind vom Rate-Limiting ausgenommen:
- `/api/**` (MetaTrader EA muss ungehindert kommunizieren)
- `/css/**`, `/js/**`, `/images/**`, `/favicon.ico` (statische Ressourcen)

### Konfiguration

| Parameter | Beschreibung | Standard |
|---|---|---|
| `secRateLimitEnabled` | Limitierung aktiviert/deaktiviert | true |
| `secRateLimitPerMin` | Max. Anfragen pro Minute pro IP | 60 |

---

## 5. Security-Headers

**Klasse:** `SecurityHeadersFilter` (Filter-Order: 2)

### Gesetzte Headers (wenn aktiviert)

| Header | Wert | Zweck |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | Verhindert MIME-Type-Sniffing |
| `X-Frame-Options` | `SAMEORIGIN` | Schutz gegen Clickjacking |
| `X-XSS-Protection` | `1; mode=block` | XSS-Filter im Browser |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Kontrolliert Referrer-Informationen |
| `Content-Security-Policy` | `default-src 'self' ...` | Kontrolliert geladene Ressourcen |
| `Strict-Transport-Security` | `max-age=31536000` | HTTPS-Erzwingung (nur bei HTTPS) |

### CSP-Konfiguration

Die Content-Security-Policy erlaubt:
- `'self'` als Standardquelle
- Scripts von `cdn.jsdelivr.net` (Chart.js)
- Styles von `'self'`, `'unsafe-inline'`, `fonts.googleapis.com`
- Fonts von `fonts.gstatic.com`
- Bilder von `'self'` und `data:`

### Konfiguration

| Parameter | Beschreibung | Standard |
|---|---|---|
| `secHeadersEnabled` | Headers aktiviert/deaktiviert | true |

---

## 6. CSRF-Schutz

- **Aktiviert** für alle Web-Formulare (Thymeleaf fügt automatisch CSRF-Token ein).
- **Deaktiviert** für `/api/**` Endpunkte, da der MetaTrader EA als externer Client JSON-Requests sendet und keine CSRF-Token verwalten kann.

---

## 7. Request-Logging

**Klasse:** `RequestLoggingFilter`

### Funktionsweise

- Protokolliert den **ersten Request** von neuen IP-Adressen (noch nie gesehen).
- Erfasst: IP, HTTP-Methode, URI, Query-String, User-Agent, Status-Code.
- Überspringt statische Ressourcen und `/h2-console`.

### Einsehbar unter

- `/admin/requests` - Request-Logs
- `/admin/logs` - Login-Logs
- `/admin/client-logs` - Client-Aktionslogs

---

## 8. Session-Management

| Parameter | Beschreibung | Standard |
|---|---|---|
| `secMaxSessions` | Max. gleichzeitige Sessions pro Benutzer | 1 |

---

## 9. H2-Konsole

Die H2-Datenbankkonsole (`/h2-console`) ist standardmäßig aktiviert, kann aber im Admin-Panel deaktiviert werden.

| Parameter | Beschreibung | Standard |
|---|---|---|
| `secH2ConsoleEnabled` | Konsole aktiviert/deaktiviert | true |

**Empfehlung:** In Produktionsumgebungen sollte die H2-Konsole deaktiviert werden.

---

## 10. Log-Aufbewahrung & Bereinigung

**Klasse:** `LogCleanupService`

- Läuft täglich um 2:00 Uhr.
- Löscht Logs älter als die konfigurierte Aufbewahrungsdauer.

| Log-Typ | Konfigurationsschlüssel | Standard |
|---|---|---|
| Login-Logs | `logLoginDays` | 30 Tage |
| Verbindungs-/Request-Logs | `logConnDays` | 14 Tage |
| Client-Aktionslogs | `logClientDays` | 7 Tage |

---

## 11. API-Sicherheit

### MetaTrader EA Endpunkte (`/api/**`)

- Kein CSRF-Schutz (extern aufgerufene REST-API).
- Kein Rate-Limiting (EA muss zuverlässig kommunizieren).
- Öffentlich zugänglich (keine Authentifizierung erforderlich).

**Empfehlung für Produktionsumgebungen:**
- Setzen Sie den Server hinter einen Reverse-Proxy (nginx/Apache).
- Beschränken Sie den Zugriff auf `/api/**` per IP-Whitelist auf bekannte MetaTrader-Server.
- Nutzen Sie HTTPS für die gesamte Kommunikation.

---

## 12. Sicherheits-Checkliste für Produktionsbetrieb

1. Standard-Admin-Passwort in `application.properties` ändern.
2. HTTPS aktivieren (TLS-Zertifikat konfigurieren oder Reverse-Proxy).
3. H2-Konsole im Admin-Panel deaktivieren.
4. Brute-Force-Schutz aktiviert lassen.
5. Rate-Limiting mit angemessenen Werten konfigurieren.
6. Security-Headers aktiviert lassen.
7. Regelmäßig Login-Logs auf verdächtige Aktivitäten prüfen.
8. Log-Aufbewahrungsdauer an Compliance-Anforderungen anpassen.
9. API-Zugriff per Firewall/Reverse-Proxy einschränken.
