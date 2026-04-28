# Erweiterung der Replit App: Demo-Login und Logout (Neue API)

Um die Entwicklung der Replit App deutlich zu vereinfachen, wurden extra neue REST-API-Endpunkte im MqlTradeMonitor Server hinzugefügt. Diese benötigen **kein CSRF-Token**, da sie für reine API-Clients gedacht sind, und geben sauberes JSON zurück, sodass die App keine HTML-Seiten mehr parsen muss.

Gib der Replit AI einfach diesen Prompt:

---

## Prompt für Replit AI

> "Replit AI, unser Backend hat jetzt dedizierte REST-API-Endpunkte für den Login, den Demo-Login und das Logout erhalten. Diese Endpunkte sind unter `/api/` erreichbar, erfordern **kein CSRF-Token** (CSRF ist hier deaktiviert) und geben JSON zurück. Bitte passe die App wie folgt an:
> 
> **1. Demo-Login einbauen:**
> - Füge einen 'Demo Login'-Button auf dem Startscreen hinzu.
> - Wenn dieser Button geklickt wird, mache einen simplen `POST`-Request auf `/api/demo-login`.
> - Dieser Request braucht **keinen** Body und keine Credentials.
> - Das Backend legt automatisch die serverseitige Session an und sendet dir das `JSESSIONID`-Cookie mit. Stelle sicher, dass bei fetch `credentials: 'include'` gesetzt ist, damit das Cookie im Browser gespeichert und bei zukünftigen Anfragen mitgesendet wird.
> - Bei Erfolg (HTTP 200) ist der Nutzer eingeloggt und kann zum Dashboard weitergeleitet werden.
> 
> **2. Normaler Login (falls die App aktuell noch den HTML-Login nutzt):**
> - Du kannst ab sofort den Login viel einfacher abwickeln.
> - Sende einen `POST`-Request an `/api/login` mit dem JSON-Body `{ "username": "...", "password": "..." }` (und `Content-Type: application/json`).
> - Bei Erfolg (HTTP 200) erhältst du das Session-Cookie und bist eingeloggt. Es wird kein CSRF Support mehr beim Login benötigt.
> 
> **3. Logout-Funktion:**
> - Füge einen 'Logout'-Button im Dashboard/Navigationsbereich hinzu.
> - Wenn dieser geklickt wird, mache einen `POST`-Request auf `/api/logout`. 
> - Das Backend invalidiert die Session. Die App sollte danach programmatisch auf den Login-Screen weiterleiten und ggf. lokale Zustände (State) leeren."
