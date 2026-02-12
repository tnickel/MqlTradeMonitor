# Projekt-Dokumentation: MQL Trade Monitor

## 1. Übersicht
Das Projekt **MQL Trade Monitor** dient zur Überwachung von Trading-Konten (MetaTrader 5) über ein Web-Dashboard. Es ermöglicht die zentrale Anzeige von Account-Metriken, offenen Positionen und der Historie geschlossener Trades.

Das System besteht aus zwei Hauptkomponenten:
1.  **MQL5 Expert Advisor (Client)**: Läuft im MetaTrader Terminal, exportiert Account-Daten und Trades in Echtzeit an den Server.
2.  **Java Spring Boot Server**: Empfängt die Daten, speichert sie und stellt eine Web-Oberfläche zur Anzeige bereit.

---

## 2. Architektur & Komponenten

### 2.1 MQL5 Expert Advisor (`TradeMonitorClient.mq5`)
*   **Funktion**: Sendet bei jedem Tick oder Trade-Event den aktuellen Account-Status (Balance, Equity) sowie Listen aller offenen Positionen und der Historie an den Server.
*   **Datenübertragung**: JSON über HTTP POST.
*   **Konfiguration**:
    *   `ServerUrl`: URL des Java Servers (Standard: `http://localhost:8080`).
    *   `CheckInterval`: Sende-Intervall in Sekunden (Standard: 1 Sekunde).
    *   `MagicNumbers`: Optionaler Filter für bestimmte Strategien.
*   **Besonderheiten**:
    *   Überträgt Kommentare (`comment`) und Magic Numbers für detaillierte Analysen.
    *   Exportiert die Historie der letzten 30 Tage (konfigurierbar im Code).
    *   Fehlerbehandlung bei Verbindungsabbruch.

### 2.2 Java Server
*   **Technologie**: Spring Boot, Thymeleaf (Frontend), Java 17+.
*   **API Endpoints**:
    *   `POST /api/register`: Registriert einen neuen Account beim ersten Start.
    *   `POST /api/update`: Empfängt Updates zu offenen Trades und Account-Metriken.
    *   `POST /api/update-history`: Empfängt die geschlossene Trade-Historie.
*   **Datenhaltung**:
    *   In-Memory (`AccountManager`) für schnelle Echtzeit-Zugriffe.
    *   JSON-Dateisystem (`TradeStorage`) für Persistenz der Daten über Server-Neustarts hinweg.
    *   Verzeichnis: `data/` im Projektordner.

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
    java -jar target/trade-monitor-0.0.1-SNAPSHOT.jar
    ```
4.  Der Server ist nun unter `http://localhost:8080` erreichbar.

### 3.2 MetaTrader Client
1.  Kopiere die Datei `mql5/TradeMonitorClient.mq5` in den Ordner `MQL5/Experts/` deiner MT5-Installation.
2.  Öffne den MetaEditor (F4 im MT5) und kompiliere die Datei Button "Kompilieren").
3.  In MT5 unter "Extras" -> "Optionen" -> "Experten":
    *   Haken bei "WebRequest für folgende URLs erlauben" setzen.
    *   URL `http://localhost:8080` (oder deine Server-IP) hinzufügen.
4.  Ziehe den EA aus dem Navigator auf einen beliebigen Chart.
5.  Der EA sollte nun oben rechts im Chart "TradeMonitorClient" anzeigen und Daten senden.

---

## 4. Bedienung & Hinweise

*   **Refresh**: Um neue Daten im Dashboard zu sehen, muss die Seite im Browser manuell neu geladen werden (F5).
*   **Filter**: Die Filter in der Historie ("Heute", "1 Woche"...) wirken sich auf die angezeigten Trades und die Summe darunter aus.
*   **Daten-Reset**: Um alle Daten zurückzusetzen, stoppe den Server und lösche den Ordner `server/data`.

---

## 5. Entwicklung & Wartung

*   **Erweiterungen**: Neue Felder müssen in allen Schichten hinzugefügt werden (MQL5 -> JSON -> DTO -> Model -> HTML).
*   **Datum & Zeit**: Der Server erwartet Datumsangaben im Format `yyyy-MM-dd HH:mm:ss` oder `yyyy.MM.dd HH:mm:ss`.
