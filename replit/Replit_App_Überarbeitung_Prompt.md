# Prompt für Replit AI: Komplette App-Überarbeitung

Kopiere den folgenden Text und füge ihn als Anweisung in Replit AI (Agent oder Chat) ein, um die App grundlegend zu überarbeiten.

---

> **Anweisung an Replit AI: Komplette Überarbeitung der Trade Monitor App**
> 
> Hallo Replit AI. Wir müssen die bestehende Trade Monitor Mobile-App komplett überarbeiten und modernisieren. Die App dient als Frontend für ein Java Spring Boot Backend und kommuniziert über dedizierte JSON REST-APIs (`/api/...`).
> 
> Bitte führe ein komplettes Refactoring der App durch und implementiere folgendes neues Navigations- und Layout-Konzept:
> 
> ### 1. Modernes Mobile-Design (UI/UX)
> - Die App benötigt ein sauberes, responsives und modernes Design (vorzugsweise Dark Mode, ähnlich dem Web-Dashboard, mit tiefblauen/grauen Tönen `#1e1e2e` und Akzenten in Grün/Rot für Gewinne und Verluste).
> - Da die App vorrangig auf dem Handy genutzt wird (**kleiner Bildschirm**), darf es **keine großen Grafiken oder Charts** mehr geben. Die reinen Informationstexte und gut lesbare Listen reichen vollkommen aus.
> - Implementiere ein sauberes State-Management (z.B. React Context), um den Login-Status und die Nutzerrolle (`isAdmin`) global verfügbar zu machen.
> 
> ### 2. Hauptnavigation & Startansicht
> - Wenn sich der Nutzer in die App einloggt, soll **standardmäßig der Tagesreport** angezeigt werden.
> - Die komplette Steuerung erfolgt über eine **Bottom Navigation Bar** (untere Menüleiste).
> - Den Rollen-Status (`isAdmin`: true/false) erhältst du direkt vom Backend über den Endpunkt `/api/stats/system-status`.
> 
> ### 3. Navigationspunkte & Ansichten
> 
> **Ansicht 1: Tagesreport (Default View)**
> - Dieser Menüpunkt ist für **alle Nutzer** sichtbar und die Standard-Ansicht nach dem Einloggen.
> - Zeige hier eine reine Text-/Listen-Übersicht des Tagesreports (z.B. welcher Account welchen Gewinn `dailyProfit` am heutigen Tag erwirtschaftet hat).
> - **Ganz wichtig:** Keine Grafiken oder Diagramme, nur die essentiellen Texte (Account-Name und Profit).
> 
> **Ansicht 2: Open Trades**
> - Dieser Menüpunkt ist für **alle Nutzer** sichtbar.
> - Zeige alle aktuell offenen Positionen an (abrufbar via `GET /api/trades/open`).
> - Offene Trades müssen in der Liste strukturiert **nach MetaTrader Account gruppiert** aufgelistet werden.
> - Keine Grafiken – reine tabellarische/kartenbasierte Auflistung (Symbol, Typ, Lot, Open Profit etc.).
> 
> **Ansicht 3: Systemstatus (NUR für Admins - `isAdmin === true`)**
> - Diese Route sowie der Button unten darf nur sichtbar sein und aufgerufen werden, wenn der Nutzer Admin ist.
> - Rufe hierfür die Status-Werte aus `/api/stats/system-status` (Feld `server`) ab, um den Security/Attack-Status und den Health-Status des Servers (Läuft er?) darzustellen.
> - **NEU:** Der Admin muss zusätzlich sehen, wer sich wann eingeloggt hat. Hole dir dafür per `GET /api/login-logs` ein Array der **letzten 20 Logins**. Zeige diese Logins (Nutzer, Zeitstempel `time`) als eine einfache anschauliche, scrollbare Liste in dieser Ansicht unterhalb der System-Checks an.
> 
> ### 4. Technische Vorgaben
> - **API-Security:** Alle API-Requests (`fetch`) müssen zwingend die Option `credentials: 'include'` enthalten, damit das serverseitig gesetzte `JSESSIONID` Session-Cookie korrekt übertragen wird. 
> - **Logout:** Integriere einen sauberen Logout (`POST /api/logout`), der den globalen State leert und den Nutzer zurück zum Login-Screen wirft.
> - **Code Struktur:** Baue das in saubere, einzelne Komponenten um (z.B. `BottomNav.jsx`, `DailyReportView.jsx`, `OpenTradesView.jsx`, `SystemStatusView.jsx`).
> 
> Bitte beginne damit, das Layout der Bottom Navigation Bar dynamisch aufzusetzen, stelle die Default-Ansicht auf den Tagesreport und bestätige, sobald du die Grundstruktur angelegt hast.
