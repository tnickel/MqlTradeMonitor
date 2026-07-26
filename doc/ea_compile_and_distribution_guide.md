# TradeMonitor: EA Kompilierung & Verteilungs-Leitfaden (MQL4 / MQL5)

Dieses Dokument beschreibt den vollständigen Prozess zur Versionsverwaltung, Kompilierung, Server-Bereitstellung und Verteilung des **TradeMonitorClient** EAs auf MQL4 und MQL5.

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
