# MqlTradeMonitor Custom Agent Rules

## EA Compilation & Distribution Procedure (MQL4 / MQL5)

Whenever asked to bump version, recompile, deploy, or distribute MQL4/MQL5 EAs:

1. **Dual Version Lines:**
   - Always update BOTH `#property version "X.XX"` (Line 7) AND `#define EA_VERSION "X.XX"` (Line 28) in `mql5/TradeMonitorClient.mq5` and `mql4/TradeMonitorClient.mq4`.
   - Never update only one line, otherwise MetaTrader will show old version numbers.

2. **Kompilierung:**
   - MQL5 EA: Compile locally with `C:\Forex\Mt5\TickmillLifeMql5\metaeditor64.exe`.
   - MQL4 EA: Compile remotely via SSH (`192.168.178.164`, User `tnickel`, Password `terminator`) using `C:\Users\trader3\Forex\Mt4\Tickmill344\metaeditor.exe`.

3. **Contabo Server Auto-Update:**
   - SCP `.ex5`, `.ex5.version`, `.ex4`, `.ex4.version` to Contabo server (`84.46.247.222`, Key: `C:\Users\tnickel\.ssh\contabo_key`) into `/opt/wildfly/bin/updates/` and `/opt/wildfly/standalone/updates/`.

4. **Portable AND Roaming Terminal Distribution:**
   - Copy compiled `.ex5` and `.ex4` to ALL `MQL5\Experts` and `MQL4\Experts` directories in:
     - Installation folders: `C:\Forex`, `F:\Forex`, `C:\Users\trader2\Forex`, `C:\Users\trader3\Forex`
     - Roaming Data folders: `C:\Users\<user>\AppData\Roaming\MetaQuotes\Terminal\<HEX-HASH>\MQL5\Experts\` (for `trader2`, `trader3`, `tnickel` on local PC and remote SSH host `192.168.178.164`).

5. **Reference Documentation:**
   - Full guide is located in [doc/ea_compile_and_distribution_guide.md](file:///d:/AntiGravitySoftware/GitWorkspace/MqlTradeMonitor/doc/ea_compile_and_distribution_guide.md).
