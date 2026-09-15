/* Run the real BuildClosedTradesJson body against a deterministic history API shim. */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const assert = require('node:assert/strict');

const sourcePath = process.argv[2] || path.join(__dirname, '..', 'TradeMonitorClient.mq5');
const source = fs.readFileSync(sourcePath, 'utf8');
function extract(name) {
  return source.match(new RegExp(`^(?:string|void|bool) ${name}\\([^;\\r\\n]*\\)\\s*\\{[^]*?^\\}`, 'm'))?.[0];
}
const historyFunction = extract('BuildClosedTradesJson');
assert.ok(historyFunction, 'Could not extract BuildClosedTradesJson');
const revisionFunction = extract('PrepareHistoryRevision');
const uploadFunction = extract('SendInitialTradeList');
const revisionDefine = source.match(/^#define HISTORY_SYNC_REVISION\s+\d+/m)?.[0];
const hasRevisionTests = Boolean(revisionFunction && uploadFunction && revisionDefine);
if (revisionDefine) assert.ok(hasRevisionTests, 'Repair revision is defined but its production functions could not be extracted');
// MQL datetime literals and its explicitly 64-bit long types need C++ spelling.
// No algorithm or control-flow changes are made to the extracted function.
const body = [hasRevisionTests ? `${revisionDefine}\n#define HAS_REVISION_TESTS 1` : '', historyFunction,
  hasRevisionTests ? revisionFunction : '', hasRevisionTests ? uploadFunction : ''].join('\n\n')
  .replace(/D'([^']+)'/g, 'StringToTime("$1")')
  .replace(/\bulong\b/g, 'uint64_t')
  .replace(/\blong\b/g, 'int64_t');
const shim = fs.readFileSync(path.join(__dirname, 'history_mock.cpp'), 'utf8');
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'mt5-history-test-'));
try {
  const cpp = path.join(temp, 'history.cpp');
  const exe = path.join(temp, process.platform === 'win32' ? 'history.exe' : 'history');
  fs.writeFileSync(cpp, shim.replace('// PRODUCTION_FUNCTION', () => body));
  const compiler = process.env.CXX || 'g++';
  const compiled = spawnSync(compiler, ['-std=c++17', '-O0', cpp, '-o', exe], { encoding: 'utf8' });
  assert.equal(compiled.status, 0, `C++ harness compilation failed: ${compiled.error || ''}\n${compiled.stderr}`);
  function run(scenario) {
    const result = spawnSync(exe, [scenario], { encoding: 'utf8' });
    assert.equal(result.status, 0, `Harness failed: ${result.error || ''}\n${result.stderr}`);
    const [json, latest, calls] = result.stdout.trimEnd().split(/\r?\n/);
    return { rows: json === '' ? null : JSON.parse(json), latest, calls: calls.split(' ').map(Number), log: result.stderr };
  }
  let failed = 0;
  function test(name, fn) {
    try { fn(); console.log(`PASS ${name}`); }
    catch (e) { failed++; console.error(`FAIL ${name}\n${e.message}`); }
  }
  test('SELL entries outside the incremental window retain opening details for both positions', () => {
    const actual = run('sell');
    assert.deepEqual(actual.rows.map(r => [r.ticket, r.type, r.openTime, r.openPrice, r.commission, r.magicNumber, r.comment]), [
      [249828460, 'SELL', '2026.09.14 12:25:00', 4284.26, -.26, 1234567890123, 'Gold Spike MT5'],
      [249828953, 'SELL', '2026.09.14 12:26:00', 4282.75, -.88, 9876543210123, 'Gold Spike MT5'],
    ]);
    assert.equal(actual.latest, '2026.09.14 16:47:47');
    assert.deepEqual(actual.calls, [3, 2], 'Each position lookup must restore the original history selection');
    assert.equal(actual.rows[0].openTimeMsc, Date.UTC(2026, 8, 14, 12, 25) + 123);
    assert.equal(actual.rows[1].closePrice, 4280.31);
  });
  test('BUY symmetry: a closing SELL remains an original BUY', () => {
    assert.deepEqual(run('buy').rows.map(r => r.type), ['BUY', 'BUY']);
  });
  test('Full and incremental sync produce identical trade JSON', () => {
    assert.deepEqual(run('full').rows, run('sell').rows);
    assert.deepEqual(run('full').calls, [1, 0], 'Full history must use the entries already selected');
  });
  test('Partial closes allocate opening commission proportionally', () => {
    const rows = run('partial').rows;
    assert.deepEqual(rows.map(r => [r.type, r.volume, r.commission]), [['SELL', .05, -.26], ['SELL', .12, -.62]]);
    assert.equal(Math.round(rows.reduce((sum, r) => sum + r.commission, 0) * 100), -88);
  });
  for (const scenario of ['missing', 'lookup-failure']) {
    test(`${scenario}: inverse exit direction and explicit diagnostic`, () => {
      const actual = run(scenario);
      assert.equal(actual.rows.length, 2);
      assert.deepEqual(actual.rows.map(r => r.type), ['SELL', 'SELL']);
      assert.deepEqual(actual.rows.map(r => r.commission), [-.13, -.44]);
      assert.equal(actual.rows[0].openTime, actual.rows[0].closeTime);
      assert.ok(actual.log.includes('Opening deal unavailable'));
      assert.deepEqual(actual.calls, [3, 2]);
    });
  }
  test('Failed history restore discards the payload and sync bookmark', () => {
    const actual = run('restore-failure');
    assert.equal(actual.rows, null, 'Empty-string failure sentinel must differ from valid [] history');
    assert.equal(actual.latest, '');
    assert.ok(actual.log.includes('Failed to restore history window'));
  });
  test('Initial history failure emits no trades', () => {
    const actual = run('initial-failure');
    assert.equal(actual.rows, null, 'Empty-string failure sentinel must differ from valid [] history');
    assert.equal(actual.latest, '');
  });
  if (hasRevisionTests) {
    for (const scenario of ['revision-missing', 'revision-older']) {
      test(`${scenario}: schedules full sync and clears persisted bookmark without completing revision`, () => {
        const state = run(scenario).rows;
        assert.equal(state.tradeListSent, false);
        assert.equal(state.tradeListTime, 0);
        assert.equal(state.tradeListMarker, 0);
        assert.equal(state.bookmark, '');
        assert.equal(state.savedBookmark, '');
        assert.equal(state.saveCalls, 1);
        assert.equal(state.revision, scenario === 'revision-missing' ? null : state.expectedRevision - 1);
      });
    }
    for (const scenario of ['revision-current', 'revision-newer']) {
      test(`${scenario}: preserves completed sync and its bookmark`, () => {
        const state = run(scenario).rows;
        assert.equal(state.tradeListSent, true);
        assert.equal(state.tradeListTime, 123);
        assert.equal(state.tradeListMarker, 123);
        assert.equal(state.bookmark, '2026.09.14 16:11:41');
        assert.equal(state.savedBookmark, state.bookmark);
        assert.equal(state.saveCalls, 0);
        assert.equal(state.revision, state.expectedRevision + (scenario === 'revision-newer' ? 1 : 0));
      });
    }
    for (const scenario of ['upload-selection-failure', 'upload-post-failure']) {
      test(`${scenario}: failed initial sync cannot mark repair complete`, () => {
        const state = run(scenario).rows;
        assert.equal(state.success, false);
        assert.equal(state.revision, null);
        assert.equal(state.bookmark, '');
        assert.equal(state.savedBookmark, '');
        assert.equal(state.saveCalls, 1, 'Only revision preparation may save on failure');
        assert.equal(state.postCalls, scenario === 'upload-selection-failure' ? 0 : 1);
      });
    }
    test('Successful initial upload of valid empty history completes repair', () => {
      const state = run('upload-empty').rows;
      assert.equal(state.success, true);
      assert.equal(state.revision, state.expectedRevision);
      assert.equal(state.bookmark, '2026.09.15 11:59:59');
      assert.equal(state.savedBookmark, state.bookmark);
      assert.equal(state.saveCalls, 2);
      assert.equal(state.postCalls, 1);
      assert.deepEqual(state.posted.closedTrades, []);
    });
    test('Successful initial upload of actual trades completes repair at latest close', () => {
      const state = run('upload-with-trades').rows;
      assert.equal(state.success, true);
      assert.equal(state.revision, state.expectedRevision);
      assert.equal(state.bookmark, '2026.09.14 16:47:45');
      assert.equal(state.savedBookmark, state.bookmark);
      assert.equal(state.postCalls, 1);
      assert.equal(state.posted.closedTrades[0].type, 'SELL');
    });
  }
  if (failed) process.exitCode = 1;
} finally {
  fs.rmSync(temp, { recursive: true, force: true });
}
