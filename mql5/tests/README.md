# Closed history regression tests

Requirements: Node.js and a C++17 compiler (`g++` on `PATH`, or set `CXX` to its executable).

From the repository root:

```powershell
node mql5/tests/run-history-tests.cjs
```

An optional source path allows testing an older client without changing the working copy:

```powershell
node mql5/tests/run-history-tests.cjs C:/temp/TradeMonitorClient-before.mq5
```

The runner extracts **the actual `BuildClosedTradesJson` function** from the specified MQ5 file, plus `PrepareHistoryRevision` and `SendInitialTradeList` when the repair revision is present. It only translates MQL date literals and 64-bit integer type spelling, then compiles these functions with deterministic mock APIs. Temporary C++ sources and binaries are removed after execution.

The fixture reproduces opening SELL deals at 12:25/12:26 and closing BUY deals at 16:47 outside the incremental history window starting at 14:11:41. Checks cover both positions, opening metadata, full/incremental equivalence, BUY symmetry, partial-close commission allocation, missing opening deals, failed lookups, and failed history restoration. An empty-string selection-failure sentinel is distinguished from a valid empty `[]` history.

Repair revision checks exercise missing/older markers (full sync and cleared persisted bookmark), current/newer markers (preserved state), failed selection (no upload or completion marker), failed HTTP POST (no completion marker), and successful initial uploads with empty/nonempty history (completion marker and correct bookmark). The current client has 16 checks. Older source files without the revision feature run only the eight history checks.

## Scope and limitations

This checks the production function's selection and serialization logic; it does not run MetaTrader, contact a broker, place orders, access real account data, or validate the website/database. The mock deliberately replaces the selected deal list when selecting a position and makes deal properties available only within the selected list. Failure scenarios clear that list to exercise restoration robustly.

Time conversion uses UTC with no broker timezone behavior. Market ticks, candles, order setup times, open trades, account details, HTTP, terminal global variables, and bookmark persistence are stubbed. Revision tests execute the real preparation and initial-upload functions, but do not run `OnInit`, the incremental upload caller, terminal restarts, or actual HTTP/file persistence. The fixture covers one opening deal per position and proportional partial exits; it does not establish correctness for netting reversals (`INOUT`), multiple scale-in entries, or broker-specific close-by histories. Compile the complete MQ5 in MetaEditor separately to verify MQL syntax and terminal compatibility.
