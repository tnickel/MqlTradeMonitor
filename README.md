# MqlTradeMonitor

Ein Trade-Monitoring-System für MetaTrader 5, bestehend aus einem MQL5 Expert Advisor und einem Java Spring Boot Server.

## Projektstruktur

```
MqlTradeMonitor/
├── mql5/
│   └── TradeMonitorClient.mq5    # Expert Advisor für MetaTrader
└── server/
    ├── pom.xml                    # Maven Projekt
    └── src/main/java/de/trademonitor/
        ├── TradeMonitorApplication.java
        ├── controller/
        ├── service/
        ├── model/
        └── dto/
```

## Setup

### 1. Java Server in Eclipse importieren

1. **File → Import → Existing Maven Projects**
2. Wähle den `server` Ordner als Root Directory
3. Klicke auf **Finish**

### 2. Server starten

In Eclipse:
- Rechtsklick auf `TradeMonitorApplication.java`
- **Run As → Java Application**

Der Server läuft auf **http://localhost:8080**

### 3. MetaTrader EA installieren

1. Kopiere `mql5/TradeMonitorClient.mq5` in deinen MetaTrader `MQL5/Experts` Ordner
2. Kompiliere im MetaEditor (F7)
3. **Wichtig**: Erlaube WebRequest in MetaTrader:
   - Tools → Optionen → Expert Advisors
   - Aktiviere "WebRequest für aufgelistete URLs erlauben"
   - Füge `http://localhost:8080` hinzu
4. Ziehe den EA auf einen Chart

## Web Dashboard

Öffne http://localhost:8080 im Browser um:
- Alle verbundenen MetaTrader-Accounts zu sehen
- Online/Offline Status zu prüfen
- Offene Trades anzuzeigen
- Balance/Equity zu überwachen

## API Endpunkte

| Methode | Endpunkt | Beschreibung |
|---------|----------|--------------|
| POST | `/api/register` | Account registrieren |
| POST | `/api/trades` | Trades aktualisieren |
| POST | `/api/heartbeat` | Keep-alive Signal |
| GET | `/api/accounts` | Accounts abfragen (JSON) |
