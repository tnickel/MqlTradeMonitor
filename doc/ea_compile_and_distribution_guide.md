# TradeMonitor: EA Kompilierung & Verteilungs-Leitfaden (MQL4 / MQL5)

Dieses Dokument beschreibt den vollständigen Prozess zur Versionsverwaltung, Kompilierung, Server-Bereitstellung und Verteilung des **TradeMonitorClient** EAs auf MQL4 und MQL5.

## Release 2026-09-15: MT5 1.23 / Server 0.12.1

- Behebt falsche BUY/SELL- und Öffnungsdaten, wenn der Eröffnungsdeal außerhalb des inkrementellen Zeitfensters liegt.
- Zuerst den Server bereitstellen: Er kann bestehende Fallback-Datensätze mit vollständigen Öffnungsdaten reparieren. Danach MT5 1.23 veröffentlichen.
- MT5 1.23 führt pro Konto einmal einen vollständigen Historienabgleich aus. Die GlobalVariable `TM_HistoryRevision_<account>` wird erst nach erfolgreichem Upload auf Revision 1 gesetzt; fehlgeschlagene Historienauswahl oder Uploads bleiben wiederholbar.
- **Am 2026-09-15 verifiziert:** Die produktive JVM hat `user.dir=/`. Der tatsächlich verwendete Updatepfad ist daher **`/updates/`**. Die unten dokumentierten WildFly-Verzeichnisse allein reichen nicht aus. Bei jedem Deployment `user.dir` prüfen und Binary sowie Versionsdatei im aktiven Pfad aktualisieren.
- Repository-Kopien für MT5: `mql5/`, `updates/`, `server/updates/`. Binary und `.version` müssen übereinstimmen; zuerst das Binary atomar ersetzen, zuletzt die Versionsdatei veröffentlichen.
- **Aktivierung prüfen:** Beim beobachteten Client 1.21 ersetzt das Auto-Update zwar die Datei, lädt den laufenden EA aber nicht neu. Eine Erfolgsmeldung im Download-Log reicht deshalb nicht aus. Nach gezieltem, geordnetem Neustart der betroffenen Terminal-Instanz meldete Konto `5590276401` Version 1.23; der Full Sync korrigierte die beiden falschen BUY-Zeilen zu SELL samt Öffnungsdaten und Einstiegskommission. Andere Terminals benötigen ebenfalls einen bestätigten EA-Neustart, bevor die neue Datei als aktiv gelten darf.
- Client-Regressionsprüfung: `node mql5/tests/run-history-tests.cjs`; Server-Build: `mvn.cmd -f server/pom.xml package` mit JDK 21. Der Server bleibt auf Java-17-Bytecode eingestellt.

---

## 1. Versionsnummern anpassen (WICHTIG!)

Beim Erhöhen der EA-Version müssen in den Quelltexten **immer zwei Zeilen synchron angepasst werden**:

### MQL5 (`mql5/TradeMonitorClient.mq5`):
- **Zeile 7 (Meta-Header):** `#property version "1.21"` *(bestimmt die Anzeige im MetaEditor, Navigator und EA-Eigenschaftsfenster)*
- **Zeile 28 (Text-Konstante):** `#define EA_VERSION "1.21"` *(bestimmt die Anzeige im Chart-Banner und Server-Heartbeat)*

### MQL4 (`mql4/TradeMonitorClient.mq4`):
- **Zeile 7 (Meta-Header):** `#property version "1.12"`
- **Zeile 28 (Text-Konstante):** `#define EA_VERSION "1.12"`

---

## 2. Kompilierung

### MQL5 (`.ex5`)
Kompilierung erfolgt lokal mit dem MetaEditor 64-Bit:
```powershell
Start-Process -FilePath "C:\Forex\Mt5\TickmillLifeMql5\metaeditor64.exe" -ArgumentList '/compile:"C:\Forex\Mt5\TickmillLifeMql5\MQL5\Experts\TradeMonitorClient.mq5" /log:"mql5\compile.log"' -Wait
```
Die erzeugte `.ex5` wird nach `mql5/TradeMonitorClient.ex5` kopiert.

### MQL4 (`.ex4`)
Kompilierung erfolgt per SSH auf dem Remote-Rechner (`192.168.178.164`, User: `tnickel`):
```powershell
Start-Process -FilePath "C:\Users\trader3\Forex\Mt4\Tickmill344\metaeditor.exe" -ArgumentList '/compile:"C:\Users\trader3\Forex\Mt4\Tickmill344\MQL4\Experts\TradeMonitorClient.mq4" /log:"C:\Users\trader3\Forex\Mt4\Tickmill344\MQL4\Experts\compile.log"' -Wait
```
Die erzeugte `.ex4` wird via SFTP nach `mql4/TradeMonitorClient.ex4` heruntergeladen.

---

## 3. Server-Bereitstellung (Contabo Auto-Update)

Damit der Server automatische E-Mail- / Heartbeat-Updates ausliefert, müssen vier Dateien auf den Contabo-Server (`84.46.247.222`) kopiert werden:

- `TradeMonitorClient.ex5` & `TradeMonitorClient.ex5.version` (Inhalt: `1.21`)
- `TradeMonitorClient.ex4` & `TradeMonitorClient.ex4.version` (Inhalt: `1.12`)

**Zielpfad auf Contabo:**
- `/opt/wildfly/bin/updates/`
- `/opt/wildfly/standalone/updates/`

---

## 4. Verteilung auf MetaTrader-Instanzen (Lokal & Remote)

MetaTrader-Terminals laufen in zwei unterschiedlichen Modus-Varianten:
1. **`/portable` Modus:** EA wird gelesen aus `<Installation>\MQL5\Experts\` (z.B. `F:\Forex\mt5\...` oder `C:\Forex\...`).
2. **Standard Windows Modus:** EA wird gelesen aus dem versteckten Roaming-Verzeichnis:
   `C:\Users\<User>\AppData\Roaming\MetaQuotes\Terminal\<HEX-HASH>\MQL5\Experts\` (bzw. `MQL4\Experts\`).

### Verteilungs-Regeln:
- Alle `Experts`-Verzeichnisse müssen rekursiv auf Laufwerken `C:\Forex`, `F:\Forex`, `C:\Users\trader2\Forex`, `C:\Users\trader3\Forex` durchsucht werden.
- Alle `AppData\Roaming\MetaQuotes\Terminal\<HASH>\MQL5\Experts` (und `MQL4\Experts`) Ordner aller Windows-Benutzerprofile (`trader2`, `trader3`, `tnickel`) müssen ebenfalls mit der neuen `.ex5` bzw. `.ex4` überschrieben werden.

---

## 5. Automatisches Python-Master-Skript

Das Skript `scratch/master_compile_deploy_distribute.py` führt alle 6 Schritte vollautomatisch aus:
1. Versionsprüfung im Quellcode
2. Lokale MQL5 Kompilierung
3. Remote MQL4 Kompilierung via SSH
4. Contabo-Server Update-Upload
5. Lokale EA-Verteilung (Portable & Roaming)
6. Remote EA-Verteilung via SSH/SFTP (Portable & Roaming)
