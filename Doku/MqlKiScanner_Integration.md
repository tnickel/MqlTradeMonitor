# MqlKiScanner-Integration (KiScanner-Sync-Protokoll v1)

Stand: 20.09.2026 · Gegenstück: MqlKiScanner `doc/06_tradeserver-sync.md`

## Überblick

Der **MqlKiScanner** (Python/Streamlit, `D:\git\MQL\MqlKiScanner`) ist ein
forensischer Scanner für MQL5-Handelssignale. Er überträgt auf Knopfdruck
seine Ergebnis-Tabelle (Ampel/Score/Urteil je Signal) **und** alle zugehörigen
PDF-Berichte auf diesen Tradeserver. Der Server ist damit **Spiegel**: Alles
bleibt in der H2-Datenbank sichtbar, auch wenn der Scanner gerade nicht
verbunden ist.

Der MqlKiScanner ist **kein MetaTrader-EA** und durchläuft deshalb nicht den
EA-Weg (`/api/register` → trades-init → heartbeat). Er erhält eine
**Sonderbehandlung**: ein eigenes, zustandsloses Einmal-Protokoll unter
`/api/kiscanner` mit Pflicht-Handshake. Die Verbindung besteht nur für die
Dauer eines Sync-Laufs — danach ist sie beendet (kein Heartbeat, keine
Offline-Erkennung nötig; die Kachel zeigt den letzten Lauf).

```
Register (Handshake) ──▶ Signals (Snapshot) ──▶ Documents (je 1 PDF) ──▶ Complete
       │                                                          Fehler: │
       └── 401 ohne gültigen X-User-Key     Inventory (docKey+sha256) ◀──┘ Abort
```

## Endpunkte (Server)

Alle Maschinen-Aufrufe: Header `X-User-Key: <API-Key eines Benutzers>` und
`Content-Type: application/json`. CSRF-frei und permitAll **nur** für exakt
diese Pfade (siehe `SecurityConfig`); Fehlerantworten sind immer
`{"status":"error","message":"…"}`.

| Methode | Pfad | Zweck |
|---------|------|-------|
| GET | `/api/kiscanner/ping` | Verbindungstest des Scanners (Erreichbarkeit + Key). |
| POST | `/api/kiscanner/register` | Handshake: verlangt `client:"MqlKiScanner"`, `protocolVersion:1`, `scannerVersion`, `signalCount`. Öffnet `ki_sync_runs`-Lauf; Antwort: `runId`, `serverTime`, `documents:[{docKey,sha256}]`, `signalIds`. |
| POST | `/api/kiscanner/signals` | Voll-Snapshot `{signals:[…]}`; Spiegel-Semantik (fehlende signalIds werden gelöscht); max. 2000 Zeilen. |
| POST | `/api/kiscanner/documents` | Ein PDF je Aufruf (`contentBase64`, nur `application/pdf`, Magic-Byte-Prüfung, 12-MB-Limit); Upsert über `docKey`. |
| POST | `/api/kiscanner/complete` | Lauf abschließen (`{signals,documents,uploaded,skipped,bytes}`). |
| POST | `/api/kiscanner/abort` | Fehlerfall: Lauf auf `aborted` setzen (Grund als `reason`). |

**Browser-Endpunkte (Session-Login nötig, kein CSRF da GET):**

| Methode | Pfad | Zweck |
|---------|------|-------|
| GET | `/api/kiscanner/status` | JSON-Aggregat für die Dashboard-Kachel (verbunden, Zähler, letzte Synchronisierung). |
| GET | `/api/kiscanner/documents/{id}/view` | PDF inline im Browser-Viewer (nosniff). |
| GET | `/kiscanner` | Ansichtsseite: Tabelle, Ampel-Donut, Risiko-Ertrag-Scatter, PDF-Dialoge, Portfolio-Bericht. |

## Signal-Zeile (Felder)

`signalId` (Key), `name`, `platform`, `url`, `ampel` (🟢🟡🔴⛔⚪),
`score` (1–10, klein = weniger Risiken), `urteil`, `kurzfassung`, `stop` +
`stopEvidence` (direct|cluster|partial|none), `tradingDdPct`, `ddEquityPct`,
`ddBalancePct`, `ertragMonatPct`, `growthPct`, `pf`, `winratePct`,
`aboPreisUsd`, `abonnenten`, `wochen`, `aboDelta7/30`, `aboStand`,
`martingale`, `peakPositionen`, `peakNettoLots`, `shockUsd`,
`kapitalbasisUsd`, `brokerServer`, `symbole`, `berichtVom`, `docsBerichte`,
`docsTiefenanalyse` (🟡), `docsDownloader` (📄-Spiegel), `tradesSha256`,
`stand` („NEU“). Zusätzlich setzt der Server `displayOrder` aus der
Snapshot-Reihenfolge (die Tabelle des Scanners behält ihre Sortierung).

## Datenmodell (neue Tabellen)

| Tabelle | Inhalt |
|---------|--------|
| `ki_signals` | Signal-Zeilen (unique `signal_id`), incl. `display_order`, `updated_at`. |
| `ki_documents` | PDFs als BLOB (`doc_key` unique, `group_name` eigene/downloader/portfolio, `sha256`, `file_size`, `last_modified`). |
| `ki_sync_runs` | Läufe: `status` running/ok/aborted, `scanner_version`, Bilanzzähler, `note` (Abbruchgrund). |

Die Kachel „🔬 MqlKiScanner“ im Dashboard (`dashboard.html`,
`header-cards-container`) zeigt: VERBUNDEN/KEINE DATEN (letzter Lauf ok),
Anzahl Signale + PDFs, letzte Synchronisierung; sie aktualisiert sich per
AJAX alle 30 s (`/api/kiscanner/status`). Klick öffnet `/kiscanner`.

## Einrichtung (Betrieb)

1. **Scanner-Benutzer anlegen:** Admin-Oberfläche → Benutzer (z. B.
   `kiscanner`, Rolle USER) — **keine** Accounts freigeben (der Scanner
   braucht keine Account-Rechte; die EA-Endpunkte bleiben ihm verwehrt,
   weil keine physische Account-Nummer freigegeben ist).
2. **API-Key kopieren** (Benutzer-Detailseite) und im MqlKiScanner hinter-
   legen: Admin → Tradeserver → API-Key + Base-URL (z. B.
   `http://192.168.178.50:8080` bzw. im Produktivbetrieb die
   monitor.tnickel-ki.de-URL).
3. Im Scanner: „Verbindung testen“, dann Ergebnisse-Seite → „Tradeserver-Sync“.

Sicherheit: Fehlgeschlagene Key-Prüfungen landen wie bei EAs im
ClientErrorLog (`KISCANNER_AUTH_FAILED`); Dokumente werden nur bei
korrektem PDF-Magic-Byte gespeichert und inline mit `nosniff` ausgeliefert.
Grenzen: 12 MB je Dokument (KiScannerService), Scanner sendet max. 8 MB
PDFs (Base64 +33 %); nginx auf monitor.tnickel-ki.de erlaubt
`client_max_body_size 200m`. **Der Monitor ist nur über
https://monitor.tnickel-ki.de vollständig erreichbar** (proxyt alles auf
8080) — andere VHosts derselben Maschine geben nur eine Pfad-Allowlist
frei und beantworten `/api/kiscanner` mit nginx-404.

## Build/Test-Hinweis

`mvn test` auf der Entwicklungsmaschine mit **JDK 21** ausführen
(`JAVA_HOME=C:\Program Files\Java\jdk-21`) — JDK 25 lässt Mockitos Byte
Buddy scheitern (Umgebungsproblem aller Klassen-Mock-Tests, nicht spezifisch
für diese Integration). Neuer Test: `KiScannerApiControllerTest` (Key-Pflicht,
Handshake-Validierung, Komplettlauf mit Spiegel-Löschung, PDF-Validierung,
Session-Schutz der Browser-Endpunkte).

## Code-Karte

- `controller/KiScannerApiController.java` — REST-Protokoll + Browser-Views
- `service/KiScannerService.java` — Lauflogik, Spiegel, Validierung, Status
- `entity/KiSignalEntity/KiDocumentEntity/KiSyncRunEntity.java`,
  `repository/Ki*Repository.java`, `dto/KiScannerDtos.java`
- `templates/kiscanner.html` (Ansichtsseite), `templates/dashboard.html`
  (Kachel + 30-s-Refresh), `config/SecurityConfig.java` (Pfad-Freigaben)
