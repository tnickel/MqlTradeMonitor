# Sicherheitskonzept: Server-Client Kommunikation (MQL4/5 ↔ Java Server)

## 1. Ausgangssituation
Die Kommunikation zwischen MetaTrader Clients (MQL4/MQL5) und dem Server (Java Spring Boot/WildFly) erfolgt über das Internet. Um die Authentizität, Integrität und Vertraulichkeit der übertragenen Daten sicherzustellen, wird ein mehrschichtiges Sicherheitskonzept implementiert.

## 2. Kernkomponenten des Sicherheitskonzepts

Das Konzept beruht auf einem **Hybrid-Ansatz** aus etablierter Transportschicht-Sicherheit (TLS/HTTPS) und einer anwendungsspezifischen Authentifizierung auf Basis von Benutzerschlüsseln.

### 2.1 Transportsicherheit durch HTTPS (TLS/SSL)
Die gesamte Kommunikation wird **ausschließlich über HTTPS** abgewickelt. HTTP wird nicht unterstützt.

**Vorteile von HTTPS:**
*   **Verschlüsselung (Confidentiality):** Alle Daten (Kontonummern, Trades, Passwörter) werden während der Übertragung verschlüsselt. Niemand kann den Datenverkehr "mitlesen" (Schutz vor Sniffing).
*   **Integrität (Integrity):** TLS stellt sicher, dass Pakete auf dem Weg vom Client zum Server nicht unbemerkt verändert werden können.
*   **Schutz vor Replay-Attacken:** Jede TLS-Verbindung handelt automatisch dynamische Session-Keys und Paket-MACs aus. Ein Angreifer kann ein abgefangenes Datenpaket nicht einfach später erneut senden.

### 2.2 Server-Architektur (Reverse Proxy Setup)
Anstatt die SSL-Zertifikate direkt im Java-Applikationsserver (WildFly/Spring Boot) zu verwalten, wird ein **Reverse Proxy** vorgeschaltet.

*   **Produktiv-Umgebung (Linux Server):**
    *   **Nginx** läuft als Reverse Proxy auf Port `443`.
    *   SSL-Zertifikate werden über **Let's Encrypt (Certbot)** automatisch bezogen und alle 90 Tage erneuert.
    *   Nginx leitet den entschlüsselten Traffic intern auf Port `8080` an den Java-Server weiter.
*   **Entwicklungs-Umgebung (Windows Arbeitsplatz):**
    *   **Caddy** wird als lokaler Reverse Proxy eingesetzt.
    *   Caddy stellt automatisch lokale HTTPS-Unterstützung bereit.
    *   Der Traffic wird analog auf `localhost:8080` (Java-Backend) weitergeleitet.

### 2.3 Authentifizierung auf Anwendungsebene (User Key)
Da HTTPS "nur" die Leitung absichert, muss dem Server zusätzlich mitgeteilt werden, *wer* gerade kommuniziert. 

*   **Der User Key:** Der Administrator erstellt für jeden Kunden / jedes Konto einen eindeutigen, kryptografischen Schlüssel (User Key), der serverseitig in der Datenbank gespeichert ist.
*   **Lokale Konfiguration:** Der Nutzer trägt diesen Schlüssel in seinem MetaTrader 4/5 Client (Expert Advisor/Skript) als Input-Parameter ein.
*   **Datenübertragung:** Bei jedem Datenaustausch (`WebRequest`) schickt der MetaTrader Client diesen User Key als benutzerdefinierten HTTP-Header (z.B. `X-User-Key`) mit der Anfrage mit.
*   **Serverseitige Prüfung:** Das Java-Backend extrahiert bei jedem Request den `X-User-Key`-Header, validiert ihn gegen die Datenbank und ordnet die gesendeten Handelsdaten exakt diesem Benutzer zu.

### 2.4 Software-Erkennung (Optional: Hardcoded Secret)
Als zusätzliche, nachrangige Schutzschicht kann ein zentrales App-Secret ("Hardcoded Key") im `.ex4/.ex5` Code des Clients hinterlegt werden. 

*   Dieses Secret wird zusätzlich als Header (z.B. `X-App-Secret`) gesendet.
*   Der Server kann dadurch auf einfache Skripte oder unautorisierte Fremd-Clients reagieren und diese blockieren.
*   *Hinweis:* Aufgrund der Möglichkeit von Reverse-Engineering (Dekompilierung) von MQL-Dateien wird dieser Mechanismus nur als "Defense-in-Depth"-Maßnahme angesehen und nicht als alleiniges Sicherheitsmerkmal.

---

## 3. Gegenüberstellung: Altes vs. Neues Konzept

| Merkmal | Ursprüngliche Idee | Neues Konzept (Best Practice) |
| :--- | :--- | :--- |
| **Verschlüsselung** | Nur Pakete, keine Vollverschlüsselung | **Vollständig** (dank HTTPS) |
| **Session Key Austausch** | Manuell in MQL/Java implementieren | **Entfällt** (Übernimmt TLS automatisch) |
| **Paket Signierung / Hash** | Manuelle HMAC Prüfung (Schutz vor Replay) | **Entfällt** (Übernimmt TLS automatisch) |
| **User Identifikation** | In Nutzlast (Payload) Hash eingebettet | **Im HTTP-Header (`X-User-Key`)** |
| **Server Setup** | Schwierig (Zertifikate direkt in Java) | **Einfach/Wartungsarm** (Nginx/Caddy Reverse Proxy) |

## 4. Umsetzungsschritte

1.  **Server Backend (Java):** Controller anpassen, sodass diese den `X-User-Key` Header verlangen (`@RequestHeader("X-User-Key")`) und validieren.
2.  **Client (MQL4/5):** Die `WebRequest` Aufrufe von `http://` auf `https://` umstellen und den Custom Header `X-User-Key` hinzufügen.
3.  **Infrastruktur (Windows):** `Caddy` als Reverse Proxy konfigurieren und mit Let's Encrypt für die lokale Entwicklungs-Domain einrichten.
4.  **Infrastruktur (Linux):** `Nginx` installieren und mit `certbot` für die Live-Domain konfigurieren.
    
    *Beispiel einer Nginx Konfiguration (`/etc/nginx/sites-available/trademonitor`):*
    ```nginx
    server {
        listen 80;
        server_name deine-domain.de;

        # Redirect all HTTP requests to HTTPS
        return 301 https://$host$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name deine-domain.de;

        # SSL-Zertifikate (werden von Certbot gepflegt)
        ssl_certificate /etc/letsencrypt/live/deine-domain.de/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/deine-domain.de/privkey.pem;

        location / {
            # Weiterleitung an lokales Java Spring Boot Backend
            proxy_pass http://localhost:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
    ```
    Nach dem Erstellen der Datei:
    `sudo ln -s /etc/nginx/sites-available/trademonitor /etc/nginx/sites-enabled/`
    `sudo systemctl reload nginx`
