# Protokoll v2 (18.10.2026)

Die Endpunkte bleiben `/api/kiscanner/*`. `/ping` liefert `kiscannerApi: 2`,
`protocolVersion: 2`, `supportedProtocolVersions: [1,2]`. Authentifizierung:
vorhandener `X-User-Key`; Browserabrufe weiter mit angemeldeter Sitzung.

`register` erhält `client: "MqlKiScanner"`, `protocolVersion: 2`, optional
`scannerVersion` und `signalCount` (0..2000). Antwort: `runId`, ausgehandelte
`protocolVersion`, `supportedProtocolVersions`, `signalIdentities` mit
`{sourceUid,source,signalId}` sowie `documents` mit
`{docKey,sha256,sourceUid,source,signalId}`. Eine Quellen-UID bleibt exakt und
unverändert; `source` ist nur das aktuelle Anzeigekürzel. UID: gültiges Unicode,
maximal 255 UTF-8-Bytes, keine Rand-Leerzeichen/Steuerzeichen. Labels maximal
128 Zeichen. `legacy:v1` ist reserviert.

Alle v2-Schreibaufrufe brauchen die registrierte numerische `runId` im JSON.
Sie ist zusätzlich an den Hash des API-Keys gebunden. Ein anderer gültiger Key
kann einen fremden Lauf nicht ändern. Ein laufender v2-Sync sperrt weitere
Registrierungen für 30 Minuten; danach wird nur dessen unvollständiges Staging
verworfen. Serverneustart verwirft Staging und markiert laufende Journale als
abgebrochen. Der letzte vollständig übertragene Bestand bleibt erhalten.

1. `signals`: `{runId, signals:[{sourceUid,source,signalId,...}]}`; positive
   externe Signal-ID. Das Paar `(sourceUid,signalId)` ist eindeutig. Gleiche
   ID bei verschiedenen Quellen ist erlaubt, doppeltes Paar wird abgewiesen.
   Maximal 2000 Signale. Nach dem ersten PDF kann der Snapshot nicht ersetzt
   werden. `stored` bestätigt hier nur Staging (`staged:true`).
2. `documents`: bisherige PDF-Felder plus `runId,sourceUid,source,signalId`.
   Signaldokumente brauchen einen Eigentümer im aktuellen Signal-Snapshot und
   den Key `signal/{RFC3986-escaped sourceUid}/{signalId}/...`; URL-Encoding
   entspricht Python `quote(uid,safe="")`, einschließlich `%20`, `%2F`, `%2A`.
   Ein `docKey` kann nie seinen Eigentümer wechseln. Portfolio-Dokumente haben
   alle drei Eigentümerfelder null und einen `portfolio/`-Key. SHA-256 und
   optionale `sizeBytes` werden gegen echte PDF-Bytes geprüft; PDF-Magic und
   12 MiB je Datei, insgesamt 512 MiB/10000 Dateien im Staging.
3. `complete`: `{runId,signals,documents,uploaded,skipped,bytes,documentKeys}`.
   `documentKeys` enthält exakt alle gewollten Dokumente, auch SHA-übersprungene
   Bestandsdokumente. Zähler und Bytes müssen zum Staging passen. Fehlende,
   fremde oder doppelte Keys verhindern den Commit. Ein Quellen-Labelwechsel
   aktualisiert auch gecachte Dokumente desselben stabilen Eigentümers.
   Erst jetzt ersetzen Signale, PDFs und Erfolgsjournal den aktiven Snapshot
   gemeinsam in einer DB-Transaktion. Nicht genannte PDFs werden gelöscht.
   Antwort enthält endgültige `stored`/`deleted`-Signalzahlen.
4. `abort`: `{runId,reason}` verwirft ausschließlich Staging. Ein fehlgeschlagener
   Commit rollt alle Änderungen zurück; der vorherige Bestand bleibt sichtbar.

Die v2-Signalzeile überträgt außerdem `minCalmarJahr`, `retddJahr`,
`retddJahrVorbehalt`, `retddVorbehaltGrund` und `equityMeasurementStatus`.
Die Hauptspalte zeigt den Jahres-Calmar (Jahres-CAGR / belastbarer eigener
Equity-DD inklusive Floating). Sie vergleicht den ungerundeten Jahreswert mit
`minCalmarJahr`. Unbekannte Schwellen bleiben neutral; kein historischer
Default wird erfunden. Vorbehaltswerte erscheinen orange mit `≈` und können
kein Grün erzeugen. Monatswerte stehen ergänzend im Kennzahlen-Dialog. Die
DD-Färbung respektiert ausschließlich `drawdownLimitPct`, auch 50 Prozent;
Ampel und Berechnungen werden unverändert vom Scanner übernommen. Neue
Schemafelder sind nullable; beim Upgrade bleiben frühere Schwellen unbekannt.

Die Browseransicht verwendet interne Signal-Zeilen-IDs für DOM und Dialoge,
Dokumentfilter verwenden das vollständige Quellenpaar. Quelle und externe ID
sind sichtbar. Das Dashboard zeigt den letzten erfolgreichen Snapshot und
zusätzlich `lastAttemptStatus`/`lastAttemptRunId` des letzten Versuchs.

## Bestand und v1-Kompatibilität

Hibernate legt eigene Tabellen `ki_signals_v2`/`ki_documents_v2` an. Eine
markierte Copy-Migration überträgt alte Tabellen verlustfrei und bewahrt auch
interne IDs/PDF-URLs. Die H2-Identity-Sequenzen werden vor der Copy-Transaktion
auf `max(old.id)+1` gesetzt: H2-DDL läuft nicht mitten in der Copy-Transaktion.
Der Kopiervorgang und sein Marker sind atomar. Alte Tabellen werden weder
gelöscht noch verändert. Ihre Besitzer bleiben ausdrücklich `legacy:v1` /
`Legacy v1`, Portfolio bleibt ohne Besitzer; heutige Quellen werden nie geraten.

Vor der ersten erfolgreichen v2-Übertragung bleibt der v1-Vertrag erhalten
(register erforderlich, danach unmittelbare v1-Schreibvorgänge). Nach dem ersten
v2-Commit werden Legacy-Zeilen im aktiven Snapshot ersetzt und v1-Schreibvorgänge
klar abgewiesen. So kann ein alter Client keinen neuen Composite-Bestand löschen.
Die Originaltabellen bleiben als historische Kopie erhalten. Ein Downgrade auf
alte Software ist nach neuen Schreibvorgängen kein zulässiger Daten-Rollback.
Vor dem produktiven Update sind deshalb WAR und geschlossene H2 gemeinsam zu
sichern; Sicherungen enthalten private Laufzeitdaten und gehören nicht ins Repo.

## Registrierungspflicht für Abschluss und Abbruch (24.10.2026)

`POST /api/kiscanner/complete` und `POST /api/kiscanner/abort` benötigen in
beiden Protokollversionen einen aktuell offenen, zuvor per `register`
angelegten Lauf. Ohne diesen Lauf antwortet der Server mit HTTP 400 und
`{"status":"error","message":"Zuerst register aufrufen"}`. Das gilt auch
für v1 und für einen erneuten Abschluss oder Abbruch nach einem bereits
beendeten Lauf; diese Aufrufe sind kein erfolgreicher Leerlauf mit
`runId:null`. Ein gültiger API-Key allein ersetzt den Handshake nicht.
V2 verlangt zusätzlich die zum registrierten Lauf und API-Key passende
`runId`; fremde oder fehlende Laufkennungen werden ebenfalls abgewiesen.

Gezielte Offline-Nachprüfung am 24.10.2026 mit lokalem JDK 21.0.8:
`mvn -o -Dtest=KiScannerApiControllerTest,KiScannerV2Test,KiScannerMigrationTest test`
→ 29 Tests grün (16 Controller-, 11 v2- und 2 Migrationsfälle), keine Skips.
Die Tests verwenden die H2-In-Memory-Datenbank des Testprofils; es fand keine
produktive Migration statt. Der Controller-Test prüft ausdrücklich beide
Abschlussendpunkte ohne offenen Registerlauf.

## Abnahme

JDK 21, Java-17-Buildziel: `mvn -q test` → 73 Tests, 0 Fehler, 0 übersprungen
(18.10.2026). Darin 15 bestehende v1-Tests, 11 neue v2-Integrationstests und
2 eigenständige H2-Migrationsfälle. Belegt: gleiche Anbieter-ID in zwei Quellen,
Quellenumbenennung, PDF-Zuordnung, falsche Run-/Key-Bindung, Duplikate,
Hash-/Owner-Fehler, Abbruch, absichtlich fehlgeschlagener DB-Commit mit Rollback,
Jahres-Schwelle/Rohpräzision, orange Vorbehaltswerte, unbekannte Alt-Schwellen, 50-Prozent-DD-Anzeige, Legacy-IDs/Sequenzen und Copy-Rollback bei Fehler in der zweiten Tabelle.
Keine produktiven Scanner-Daten werden durch diese Tests übertragen.

---

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

### Equity-Effizienz-Vertrag (03.10.2026)

Der Snapshot enthält zusätzlich `maxDrawdownEquityPct` (höchster belastbarer
gemessener Equity-DD inklusive Floating aus Kursen/Monitor),
`ertragMonatGeomPct` (eigene geometrische Gewinn-%/Monat), `cagrJahrPct`,
`retddMonat`, `retddJahr` und `retddBasis`.
Die bestätigte Basis lautet `gemessener_max_equity_drawdown_inkl_floating`.
Monats-RetDD ist geometrischer Monatsgewinn geteilt durch diesen gemessenen
Equity-DD; der Jahreswert verwendet CAGR. Trading-DD und Plattform-DD sind
keine Ersatz-Nenner. Rendite basiert auf einer virtuellen Trade-Netto-Kurve;
abweichende Monitor-Kapitalbasen/Zeiträume begrenzen die Vergleichbarkeit.

DTO und nullable H2-Spalten behalten die übertragenen Rohwerte ohne Rundung.
Fehlende Werte bleiben `null`, auch bei erneutem Snapshot derselben ID.
Legacy-RetDD ohne positive gemessene Equity und bestätigte Basis wird in der
Anzeige ausgeblendet; fehlender Gewinn wird nie aus der Plattform ergänzt.

Optionale Metadaten `drawdownLimitPct`, `minReturnMonthlyPct` und
`minRetddMonthly` übertragen die aktuell konfigurierten Scanner-Schwellen.
Die Tabelle färbt den gemessenen Equity-DD grün bis 80 % der DD-Grenze,
gelb bis zur Grenze und rot darüber. Ohne Messung oder Schwelle bleibt die
Zelle neutral. Es wird keine 30-%-Grenze für ältere Payloads erfunden.
RetDD färbt sich ab dem übertragenen Minimum grün, darunter gelb; die
Formatierung ändert weder Rohwert noch Bewertung. Ampel/Score/Urteil werden
weiterhin unverändert gespiegelt. Details und Diagramm verwenden dieselben
Messwerte und Schwellen. Neue nullable Spalten ergänzt das bestehende
`spring.jpa.hibernate.ddl-auto=update` beim Serverstart.

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
