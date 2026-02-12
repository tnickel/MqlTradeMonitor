# Projekt-Dokumentation: MQL Trade Monitor

## 1. Übersicht
Das Projekt **MQL Trade Monitor** dient zur Überwachung von Trading-Konten (MetaTrader 5) über ein Web-Dashboard. Es ermöglicht die zentrale Anzeige von Account-Metriken, offenen Positionen und der Historie geschlossener Trades.

Das System besteht aus zwei Hauptkomponenten:
1.  **MQL5 Expert Advisor (Client)**: Läuft im MetaTrader Terminal, exportiert Account-Daten und Trades in Echtzeit an den Server.
2.  **Java Spring Boot Server**: Empfängt die Daten, speichert sie in einer H2-Datenbank und stellt eine Web-Oberfläche zur Anzeige bereit.

---

## 2. Architektur & Komponenten

### 2.1 MQL5 Expert Advisor (`TradeMonitorClient.mq5`)
*   **Funktion**: Sendet bei jedem Tick oder Trade-Event den aktuellen Account-Status (Balance, Equity) sowie Listen aller offenen Positionen und der Historie an den Server.
*   **Datenübertragung**: JSON über HTTP POST.
*   **Konfiguration**:
    *   `ServerUrl`: URL des Java Servers (Standard: `http://127.0.0.1:8080`).
    *   `UpdateIntervalSeconds`: Sende-Intervall in Sekunden (Standard: 5 Sekunden).
    *   `HeartbeatIntervalSeconds`: Heartbeat-Intervall (Standard: 30 Sekunden).
    *   `ReconnectIntervalSeconds`: Reconnect-Intervall bei Verbindungsverlust (Standard: 30 Sekunden).
    *   `MaxReconnectAttempts`: Maximale automatische Reconnect-Versuche (Standard: 10, 0=unbegrenzt).
*   **Erstverbindung (Init)**:
    *   Beim ersten Start (`OnInit`) wird die vollständige Trade-Liste (offene Trades + Historie) einmalig an den Server gesendet (`/api/trades-init`).
    *   Der Zeitpunkt der erfolgreichen Übertragung wird in einer globalen Variable gespeichert.
    *   Danach werden nur noch inkrementelle Updates gesendet.
*   **Retry-Logik**:
    *   Ist der Server nicht erreichbar, wird alle 30 Sekunden (konfigurierbar) ein Reconnect versucht.
    *   Nach 10 Versuchen (konfigurierbar) werden keine automatischen Reconnect-Versuche mehr gemacht.
*   **Reconnect-Button**:
    *   Auf dem Chart erscheint ein "Reconnect Server" Button.
    *   Beim Klick werden alle Zähler zurückgesetzt und ein sofortiger Reconnect inkl. vollständiger Trade-Übertragung gestartet.
*   **Besonderheiten**:
    *   Überträgt Kommentare (`comment`) und Magic Numbers für detaillierte Analysen.
    *   Exportiert die Historie der letzten 30 Tage (konfigurierbar im Code).
    *   Fehlerbehandlung bei Verbindungsabbruch mit automatischem Retry.

### 2.2 Java Server
*   **Technologie**: Spring Boot, Spring Data JPA, H2-Datenbank, Thymeleaf (Frontend), Java 17+.
*   **API Endpoints**:
    *   `POST /api/register`: Registriert einen neuen Account beim ersten Start.
    *   `POST /api/trades-init`: Empfängt die vollständige Trade-Liste (offene + geschlossene Trades) beim ersten Connect. Server prüft auf Duplikate und fügt nur neue Trades ein.
    *   `POST /api/trades`: Empfängt inkrementelle Updates zu offenen Trades und Account-Metriken.
    *   `POST /api/history`: Empfängt inkrementelle Updates zur Trade-Historie mit Duplikat-Prüfung.
    *   `POST /api/heartbeat`: Empfängt Heartbeat-Signale.
    *   `GET /api/accounts`: Liefert Account-Statusdaten als JSON.
*   **Datenhaltung**:
    *   **H2-Datenbank** (File-basiert: `./data/trademonitor`) für persistente Speicherung über Server-Neustarts hinweg.
    *   **In-Memory Cache** (`AccountManager`) für schnelle Echtzeit-Zugriffe im Dashboard.
    *   Duplikat-Erkennung per Composite Key (`accountId` + `ticket`).
    *   Beim Server-Start werden alle Daten aus der H2-Datenbank in den In-Memory Cache geladen.
*   **H2 Console**: Unter `http://localhost:8080/h2-console` erreichbar (JDBC URL: `jdbc:h2:file:./data/trademonitor`).

### 2.3 Web Dashboard
*   **URL**: `http://localhost:8080` (Standard)
*   **Hauptfunktionen**:
    *   **Dashboard**: Kachel-Ansicht aller verbundenen Accounts mit Status (Online/Offline), Balance, Equity und P/L.
    *   **Detail-Ansicht**:
        *   Tabelle aller offenen Positionen mit Live-Daten (Gewinn/Verlust farblich markiert).
        *   **Magic Number Auswertung**: Tabelle und Charts (Gewinnkurven) pro Strategie (Magic Number).
        *   **Historie**: Tabelle geschlossener Trades, filterbar nach Zeitraum (Heute, 1 Woche, 1 Monat, etc.).
        *   **Kommentare**: Anzeige von Trade-Kommentaren in beiden Tabellen.
    *   **Benutzerfreundlichkeit**:
        *   **Filter-Persistenz**: Die gewählte Filtereinstellung wird lokal im Browser gespeichert.
        *   **Manuelle Aktualisierung**: Kein automatischer Refresh mehr (auf Wunsch entfernt), um in Ruhe analysieren zu können.
        *   **Vollbild-Charts**: Klick auf einen Chart öffnet ihn in Großansicht.

---

## 3. Installation & Start

### 3.1 Server
1.  **Voraussetzung**: Java JDK 17+ und Maven installiert.
2.  **Kompilieren**:
    ```bash
    cd server
    mvn clean package
    ```
3.  **Starten**:
    ```bash
    java -jar target/trade-monitor-server-1.0.0.jar
    ```
4.  Der Server ist nun unter `http://localhost:8080` erreichbar.
5.  Die H2-Datenbank wird automatisch im Ordner `data/` erstellt.

### 3.2 MetaTrader Client
1.  Kopiere die Datei `mql5/TradeMonitorClient.mq5` in den Ordner `MQL5/Experts/` deiner MT5-Installation.
2.  Öffne den MetaEditor (F4 im MT5) und kompiliere die Datei Button "Kompilieren").
3.  In MT5 unter "Extras" -> "Optionen" -> "Experten":
    *   Haken bei "WebRequest für folgende URLs erlauben" setzen.
    *   URL `http://127.0.0.1:8080` (oder deine Server-IP) hinzufügen.
4.  Ziehe den EA aus dem Navigator auf einen beliebigen Chart.
5.  Der EA sollte nun oben rechts im Chart "TradeMonitorClient" anzeigen und Daten senden.
6.  Ein "Reconnect Server" Button erscheint auf dem Chart für manuellen Reconnect.

---

## 4. Bedienung & Hinweise

*   **Refresh**: Um neue Daten im Dashboard zu sehen, muss die Seite im Browser manuell neu geladen werden (F5).
*   **Filter**: Die Filter in der Historie ("Heute", "1 Woche"...) wirken sich auf die angezeigten Trades und die Summe darunter aus.
*   **Daten-Reset**: Um alle Daten zurückzusetzen, stoppe den Server und lösche den Ordner `server/data`.
*   **H2 Console**: Für Debug-Zwecke kann die H2-Datenbank-Konsole unter `http://localhost:8080/h2-console` aufgerufen werden.
*   **Reconnect**: Wenn der Server nicht erreichbar war, kann über den "Reconnect Server" Button im Chart ein manueller Verbindungsaufbau gestartet werden.

---

## 5. Entwicklung & Wartung

*   **Erweiterungen**: Neue Felder müssen in allen Schichten hinzugefügt werden (MQL5 -> JSON -> DTO -> Entity -> Model -> HTML).
*   **Datum & Zeit**: Der Server erwartet Datumsangaben im Format `yyyy-MM-dd HH:mm:ss` oder `yyyy.MM.dd HH:mm:ss`.
*   **Datenbank**: Die H2-Datenbank liegt als File unter `data/trademonitor.mv.db`. JPA DDL-Auto ist auf `update` gesetzt, neue Felder werden automatisch angelegt.
