import re

with open('server/src/main/resources/templates/account-detail.html', 'r', encoding='utf-8') as f:
    html = f.read()

# 1. Update HistoryChartManager.parseData
old_parse = """        HistoryChartManager.prototype.parseData = function (rows) {
            var data = [];
            rows.forEach(row => {
                var timeStr = row.dataset.closeTime;
                var profitCell = row.querySelector('.profit-cell');
                var profit = parseFloat(profitCell ? profitCell.dataset.profit : 0);"""

new_parse = """        HistoryChartManager.prototype.parseData = function (trades) {
            var data = [];
            trades.forEach(trade => {
                var timeStr = trade.closeTime;
                var profit = parseFloat(trade.profit || 0);"""
html = html.replace(old_parse, new_parse)

# 2. Update HistoryChartManager.updateChart visibleTrades filter
old_update_chart = """            if (historyMgr && historyMgr.filteredRows) {
                historyMgr.filteredRows.forEach(row => {
                    var profitCell = row.querySelector('.profit-cell');
                    var profit = parseFloat(profitCell ? profitCell.dataset.profit : 0);
                    var timeStr = row.dataset.closeTime;"""

new_update_chart = """            if (historyMgr && historyMgr.filteredRows) {
                historyMgr.filteredRows.forEach(trade => {
                    var profit = parseFloat(trade.profit || 0);
                    var timeStr = trade.closeTime;"""
html = html.replace(old_update_chart, new_update_chart)

# 3. Update TableManager
old_tm = """        function TableManager(tableId, paginationId, summaryId) {
            this.table = document.getElementById(tableId);
            this.paginationBar = document.getElementById(paginationId);
            this.pageSize = 30;
            this.currentPage = 1;
            this.allRows = [];

            if (this.table) {
                this.allRows = Array.from(this.table.querySelectorAll('tbody tr'));
            }
            this.filteredRows = this.allRows; // initially all
        }

        TableManager.prototype.applyFilter = function (filterFn) {
            if (!this.table) return;

            // Filter all rows
            this.filteredRows = [];
            var totalProfit = 0.0;

            this.allRows.forEach(row => {
                var visible = true;
                if (filterFn) {
                    visible = filterFn(row);
                }
                if (visible) {
                    this.filteredRows.push(row);
                    // Sum profit if history table
                    var profitCell = row.querySelector('.profit-cell');
                    if (profitCell) {
                        var val = parseFloat(profitCell.dataset.profit || 0);
                        totalProfit += val;
                    }
                }
            });"""

new_tm = """        function TableManager(tableId, paginationId, summaryId) {
            this.table = document.getElementById(tableId);
            this.paginationBar = document.getElementById(paginationId);
            this.pageSize = 30;
            this.currentPage = 1;
            this.allRows = []; // Now stores JS objects instead of DOM elements
            this.filteredRows = []; 
        }
        
        TableManager.prototype.setData = function(trades) {
            this.allRows = trades || [];
            this.filteredRows = this.allRows;
        };

        TableManager.prototype.applyFilter = function (filterFn) {
            if (!this.table) return;

            this.filteredRows = [];
            var totalProfit = 0.0;

            this.allRows.forEach(trade => {
                var visible = true;
                if (filterFn) {
                    visible = filterFn(trade);
                }
                if (visible) {
                    this.filteredRows.push(trade);
                    var val = parseFloat(trade.profit || 0);
                    totalProfit += val;
                }
            });"""
html = html.replace(old_tm, new_tm)

# 4. Update TableManager.showPage
old_show_page = """        TableManager.prototype.showPage = function (page) {
            if (!this.table) return;

            this.currentPage = page;
            var totalPages = Math.ceil(this.filteredRows.length / this.pageSize);
            if (totalPages === 0) totalPages = 1;
            if (this.currentPage > totalPages) this.currentPage = totalPages;

            // Hide all first
            this.allRows.forEach(r => r.style.display = 'none');

            // Show current page of filtered rows
            var start = (this.currentPage - 1) * this.pageSize;
            var end = start + this.pageSize;

            for (var i = start; i < end && i < this.filteredRows.length; i++) {
                var row = this.filteredRows[i];
                row.style.display = '';

                // Add Timeline Border Logic (row sorts Descending by default)
                row.style.borderBottom = '';
                if (typeof timelineDates !== 'undefined' && timelineDates.length > 0) {
                    var currentDateStr = row.dataset.closeTime;
                    if (currentDateStr) {
                        var currentDate = currentDateStr.substring(0, 10).replace(/\./g, '-');
                        // Check if previous row (which is a newer date) crossed a timeline
                        if (i > 0) { // i=0 is the newest, no timeline divider before the newest (unless we needed it, but mostly between trades)
                            var prevRow = this.filteredRows[i-1];
                            var prevDateStr = prevRow.dataset.closeTime;
                            if (prevDateStr) {
                                var prevDate = prevDateStr.substring(0, 10).replace(/\./g, '-');
                                // if prevDate (newer) >= tlDate && currentDate (older) < tlDate -> crossed boundary!
                                for(var t = 0; t < timelineDates.length; t++) {
                                     var tlDate = timelineDates[t];
                                     if (prevDate >= tlDate && currentDate < tlDate) {
                                          // Add thick border above this row (since this row is the first one *before* the timeline event)
                                          // Or actually, border-bottom on the previous row (the newer one) might be better, or border-top on this row.
                                          // Let's do border-bottom on the previous row if it's visible, but actually border-top on this row is easier.
                                          row.style.borderTop = '3px solid #ef4444';
                                          // Maybe add a pseudo-element or just the thick line. 
                                          break;
                                     } else {
                                        row.style.borderTop = '';
                                     }
                                }
                            }
                        }
                    }
                }
            }

            this.renderPagination(totalPages);
        };"""

new_show_page = """        TableManager.prototype.showPage = function (page) {
            if (!this.table) return;

            this.currentPage = page;
            var totalPages = Math.ceil(this.filteredRows.length / this.pageSize);
            if (totalPages === 0) totalPages = 1;
            if (this.currentPage > totalPages) this.currentPage = totalPages;

            var tbody = this.table.querySelector('tbody');
            if(!tbody) return;

            var start = (this.currentPage - 1) * this.pageSize;
            var end = start + this.pageSize;
            var html = '';
            
            // Formatters
            var fmtNum = function(val) { return val.toFixed(2); };
            var todayStr = new Date().toISOString().substring(0, 10);

            for (var i = start; i < end && i < this.filteredRows.length; i++) {
                var trade = this.filteredRows[i];
                var profitClass = trade.profit >= 0 ? 'profit-positive' : 'profit-negative';
                var typeClass = trade.type === 'BUY' ? 'trade-type buy' : 'trade-type sell';
                var isToday = (trade.closeTime && trade.closeTime.startsWith(todayStr)) ? 'trade-today' : '';
                
                var borderStyle = '';
                if (typeof timelineDates !== 'undefined' && timelineDates.length > 0) {
                    var currentDateStr = trade.closeTime;
                    if (currentDateStr) {
                        var currentDate = currentDateStr.substring(0, 10).replace(/\\./g, '-');
                        if (i > 0) {
                            var prevTrade = this.filteredRows[i-1];
                            var prevDateStr = prevTrade.closeTime;
                            if (prevDateStr) {
                                var prevDate = prevDateStr.substring(0, 10).replace(/\\./g, '-');
                                for(var t = 0; t < timelineDates.length; t++) {
                                     var tlDate = timelineDates[t];
                                     if (prevDate >= tlDate && currentDate < tlDate) {
                                          borderStyle = 'border-top: 3px solid #ef4444;';
                                          break;
                                     }
                                }
                            }
                        }
                    }
                }

                html += `<tr class="${isToday}" style="${borderStyle}">
                            <td>${trade.ticket}</td>
                            <td><strong>${trade.symbol}</strong></td>
                            <td><span class="${typeClass}">${trade.type}</span></td>
                            <td>${trade.volume}</td>
                            <td>${trade.closeTime}</td>
                            <td>${trade.closePrice || ''}</td>
                            <td>${fmtNum(trade.swap)}</td>
                            <td>${fmtNum(trade.commission)}</td>
                            <td class="profit-cell ${profitClass}">${trade.profit >= 0 ? '+'+fmtNum(trade.profit) : fmtNum(trade.profit)}</td>
                            <td>${trade.magicNumber}</td>
                        </tr>`;
            }
            tbody.innerHTML = html;

            this.renderPagination(totalPages);
        };"""
html = html.replace(old_show_page, new_show_page)

# 5. Initialization logic: Fetching trades!
old_init = """            // History trades: Setup filters
            historyMgr = new TableManager('closed-trades-table', 'closed-trades-pagination', 'history-summary');

            // History Chart
            var allHistoryRows = Array.from(document.querySelectorAll('#closed-trades-table tbody tr'));
            historyChartMgr = new HistoryChartManager('balance-history-chart', currentBalance, allHistoryRows);

            // Load equity snapshots and render chart
            fetch('/api/equity-history/' + accountId)
                .then(function (r) { return r.json(); })
                .catch(function () { return []; })
                .then(function (snapshots) {
                    historyChartMgr.equitySnapshots = snapshots;
                    // Show equity overlay immediately with current live values
                    updateEquityOverlay(currentEquity, currentBalance);

                    // Magic Chart Manager
                    magicChartMgr = new MagicChartManager('magic-charts-container');

                    // The Global Pie Chart is now updated via updateGlobalPieChart() inside applyHistoryFilter.
                    // We just need to initialize a global variable for it.
                    window.globalPieChart = null;

                    // Restore filter
                    var savedFilter = localStorage.getItem(STORAGE_KEY_FILTER) || 'thismonth';

                    // Filter click handlers
                    var btns = document.querySelectorAll('.filter-btn');
                    btns.forEach(function (btn) {
                        var range = btn.dataset.range;
                        if (range === savedFilter) {
                            btn.classList.add('active');
                        } else {
                            btn.classList.remove('active');
                        }

                        btn.addEventListener('click', function () {
                            btns.forEach(function (b) { b.classList.remove('active'); });
                            this.classList.add('active');
                            var r = this.dataset.range;
                            localStorage.setItem(STORAGE_KEY_FILTER, r);
                            applyHistoryFilter(r);
                        });
                    });

                    // Initialize Magic Number Filter
                    initMagicFilter();

                    // Default filter
                    applyHistoryFilter(savedFilter);
                });
        });

        // ============ MAGIC NUMBER FILTER ============
        var activeMagics = new Set(); // tracks which magic numbers are checked

        function initMagicFilter() {
            var container = document.getElementById('magic-checkboxes');
            if (!container) return;

            // Collect unique magic numbers from ALL closed trades
            var magicSet = new Set();
            var rows = document.querySelectorAll('#closed-trades-table tbody tr');
            rows.forEach(function (row) {
                var m = row.dataset.magic;
                if (m) magicSet.add(m);
            });"""

new_init = """            // History trades: Setup filters
            historyMgr = new TableManager('closed-trades-table', 'closed-trades-pagination', 'history-summary');

            // Load Trades
            fetch('/api/account/' + accountId + '/closed-trades')
                .then(function (r) { return r.json(); })
                .catch(function () { return []; })
                .then(function(trades) {
                    historyMgr.setData(trades);
                    
                    historyChartMgr = new HistoryChartManager('balance-history-chart', currentBalance, trades);

                    // Load equity snapshots and render chart
                    fetch('/api/equity-history/' + accountId)
                        .then(function (r) { return r.json(); })
                        .catch(function () { return []; })
                        .then(function (snapshots) {
                            historyChartMgr.equitySnapshots = snapshots;
                            updateEquityOverlay(currentEquity, currentBalance);

                            magicChartMgr = new MagicChartManager('magic-charts-container');
                            window.globalPieChart = null;

                            var savedFilter = localStorage.getItem(STORAGE_KEY_FILTER) || 'thismonth';
                            var btns = document.querySelectorAll('.filter-btn');
                            btns.forEach(function (btn) {
                                var range = btn.dataset.range;
                                if (range === savedFilter) {
                                    btn.classList.add('active');
                                } else {
                                    btn.classList.remove('active');
                                }

                                btn.addEventListener('click', function () {
                                    btns.forEach(function (b) { b.classList.remove('active'); });
                                    this.classList.add('active');
                                    var r = this.dataset.range;
                                    localStorage.setItem(STORAGE_KEY_FILTER, r);
                                    applyHistoryFilter(r);
                                });
                            });

                            initMagicFilter(trades);
                            applyHistoryFilter(savedFilter);
                        });
                });
        });

        // ============ MAGIC NUMBER FILTER ============
        var activeMagics = new Set(); // tracks which magic numbers are checked

        function initMagicFilter(trades) {
            var container = document.getElementById('magic-checkboxes');
            if (!container) return;

            // Collect unique magic numbers from ALL closed trades
            var magicSet = new Set();
            trades.forEach(function (trade) {
                var m = String(trade.magicNumber);
                if (m) magicSet.add(m);
            });"""
html = html.replace(old_init, new_init)

# 6. ApplyHistoryFilter Step 2 & 5
old_apply_step2 = """            // Gather closed trades
            if (historyMgr && historyMgr.allRows) {
                historyMgr.allRows.forEach(row => {
                    var magic = row.dataset.magic || '';
                    if (!magic) return;

                    var closeTimeStr = row.dataset.closeTime;"""

new_apply_step2 = """            // Gather closed trades
            if (historyMgr && historyMgr.allRows) {
                historyMgr.allRows.forEach(trade => {
                    var magic = String(trade.magicNumber) || '';
                    if (!magic) return;

                    var closeTimeStr = trade.closeTime;"""
html = html.replace(old_apply_step2, new_apply_step2)

old_apply_step5 = """            // STEP 5: Apply Filter to History Table Rows
            if (historyMgr) {
                historyMgr.applyFilter(function (row) {
                    var rowMagic = row.dataset.magic || '';
                    if (currentMagics.size === 0) return false;
                    if (!currentMagics.has(rowMagic)) return false;

                    var closeTimeStr = row.dataset.closeTime;"""

new_apply_step5 = """            // STEP 5: Apply Filter to History Table Rows
            if (historyMgr) {
                historyMgr.applyFilter(function (trade) {
                    var rowMagic = String(trade.magicNumber) || '';
                    if (currentMagics.size === 0) return false;
                    if (!currentMagics.has(rowMagic)) return false;

                    var closeTimeStr = trade.closeTime;"""
html = html.replace(old_apply_step5, new_apply_step5)

# 7. Update Stats
old_stats = """            var trades = historyMgr.filteredRows.map(row => {
                var profitCell = row.querySelector('.profit-cell');
                var profit = parseFloat(profitCell ? profitCell.dataset.profit : 0);
                var swap = parseFloat(row.dataset.swap || 0);
                var commission = parseFloat(row.dataset.commission || 0) * commissionFactor;
                return { profit: profit, netProfit: profit + swap + commission };
            });"""

new_stats = """            var trades = historyMgr.filteredRows.map(trade => {
                var profit = parseFloat(trade.profit || 0);
                var swap = parseFloat(trade.swap || 0);
                var commission = parseFloat(trade.commission || 0) * commissionFactor;
                return { profit: profit, netProfit: profit + swap + commission };
            });"""
html = html.replace(old_stats, new_stats)

old_pie = """            // Re-aggregate symbols from filtered rows
            var symbolCounts = {};
            historyMgr.filteredRows.forEach(row => {
                // The table definition is: Ticket, Symbol, Type, Volume...
                // So index 1 is Symbol.
                var sym = '';
                if (row.cells && row.cells.length > 1) {
                    sym = row.cells[1].textContent.trim();
                }
                if (sym) {
                    symbolCounts[sym] = (symbolCounts[sym] || 0) + 1;
                }
            });"""

new_pie = """            // Re-aggregate symbols from filtered rows
            var symbolCounts = {};
            historyMgr.filteredRows.forEach(trade => {
                var sym = trade.symbol || '';
                if (sym) {
                    symbolCounts[sym] = (symbolCounts[sym] || 0) + 1;
                }
            });"""
html = html.replace(old_pie, new_pie)

# 8. Update MagicStats
old_magic_stats = """            // 1. Aggregate from filtered closed trades
            if (historyMgr && historyMgr.filteredRows) {
                historyMgr.filteredRows.forEach(row => {
                    var magic = row.dataset.magic;
                    if (magic) {
                        var profit = parseFloat(row.querySelector('.profit-cell').dataset.profit || 0);
                        var swap = parseFloat(row.dataset.swap || 0);
                        var commission = parseFloat(row.dataset.commission || 0) * commissionFactor;"""

new_magic_stats = """            // 1. Aggregate from filtered closed trades
            if (historyMgr && historyMgr.filteredRows) {
                historyMgr.filteredRows.forEach(trade => {
                    var magic = String(trade.magicNumber);
                    if (magic) {
                        var profit = parseFloat(trade.profit || 0);
                        var swap = parseFloat(trade.swap || 0);
                        var commission = parseFloat(trade.commission || 0) * commissionFactor;"""
html = html.replace(old_magic_stats, new_magic_stats)

# 9. Also applyHistoryFilter uses allRows[last]
old_apply_all = """                case 'all':
                    if (historyMgr && historyMgr.allRows && historyMgr.allRows.length > 0) {
                        var oldestRow = historyMgr.allRows[historyMgr.allRows.length - 1];
                        var ct = oldestRow.dataset.closeTime;
                        if (ct) {"""
new_apply_all = """                case 'all':
                    if (historyMgr && historyMgr.allRows && historyMgr.allRows.length > 0) {
                        var oldestTrade = historyMgr.allRows[historyMgr.allRows.length - 1];
                        var ct = oldestTrade.closeTime;
                        if (ct) {"""
html = html.replace(old_apply_all, new_apply_all)

with open('server/src/main/resources/templates/account-detail.html', 'w', encoding='utf-8') as f:
    f.write(html)
print("Replaced all JS functions!")
