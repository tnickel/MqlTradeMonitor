package de.trademonitor.model;

import java.util.concurrent.ConcurrentHashMap;

import de.trademonitor.dto.MagicProfitEntry;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a MetaTrader account.
 */
public class Account {
    private long accountId;
    private String broker;
    private String currency;
    private double balance;
    private double equity;
    private String name;
    private String type; // "DEMO" or "REAL"
    private LocalDateTime lastSeen; // DO NOT DEFAULT TO NOW!
    private LocalDateTime registeredAt;
    private LocalDateTime eaLogAcceptedAt;

    private String section = "TOP"; // Default to TOP (Deprecated)
    private Long sectionId;
    private int displayOrder = 0;
    private int magicNumberMaxAge = 30; // Default 30 days
    private int magicMinTrades = 5; // Default 5 trades
    private double commissionFactor = 1.0; // Broker-specific commission correction factor

    // Transient field for sync warning
    private boolean syncWarning;

    // Open Profit Alarm config (persisted via AccountEntity)
    private boolean openProfitAlarmEnabled;
    private Double openProfitAlarmAbs; // absolute min open profit, e.g. -5000
    private Double openProfitAlarmPct; // max drawdown % of balance, e.g. 10.0

    // Transient runtime field: alarm currently triggered
    private boolean openProfitAlarmTriggered;

    // Transient fields for API error warning
    private String lastErrorMsg;
    private LocalDateTime lastErrorTime;

    private String metaTraderInfo;

    public boolean isSyncWarning() {
        return syncWarning;
    }

    public void setSyncWarning(boolean syncWarning) {
        this.syncWarning = syncWarning;
    }

    public boolean isOpenProfitAlarmEnabled() {
        return openProfitAlarmEnabled;
    }

    public void setOpenProfitAlarmEnabled(boolean openProfitAlarmEnabled) {
        this.openProfitAlarmEnabled = openProfitAlarmEnabled;
    }

    public Double getOpenProfitAlarmAbs() {
        return openProfitAlarmAbs;
    }

    public void setOpenProfitAlarmAbs(Double openProfitAlarmAbs) {
        this.openProfitAlarmAbs = openProfitAlarmAbs;
    }

    public Double getOpenProfitAlarmPct() {
        return openProfitAlarmPct;
    }

    public void setOpenProfitAlarmPct(Double openProfitAlarmPct) {
        this.openProfitAlarmPct = openProfitAlarmPct;
    }

    public boolean isOpenProfitAlarmTriggered() {
        return openProfitAlarmTriggered;
    }

    public void setOpenProfitAlarmTriggered(boolean openProfitAlarmTriggered) {
        this.openProfitAlarmTriggered = openProfitAlarmTriggered;
    }

    public String getLastErrorMsg() {
        return lastErrorMsg;
    }

    public void setLastErrorMsg(String lastErrorMsg) {
        this.lastErrorMsg = lastErrorMsg;
    }

    public LocalDateTime getLastErrorTime() {
        return lastErrorTime;
    }

    public void setLastErrorTime(LocalDateTime lastErrorTime) {
        this.lastErrorTime = lastErrorTime;
    }

    // Check if account is in error state (error within last 5 minutes)
    public boolean isErrorState() {
        if (lastErrorTime == null)
            return false;
        return LocalDateTime.now().minusMinutes(5).isBefore(lastErrorTime);
    }

    public String getMetaTraderInfo() {
        return metaTraderInfo;
    }

    public void setMetaTraderInfo(String metaTraderInfo) {
        this.metaTraderInfo = metaTraderInfo;
    }

    private List<Trade> openTrades = new ArrayList<>();
    private final Map<Long, String> syncStatusMap = new ConcurrentHashMap<>();

    public Account() {
    }

    public Account(long accountId, String broker, String currency, double balance) {
        this.accountId = accountId;
        this.broker = broker;
        this.currency = currency;
        this.balance = balance;
        this.registeredAt = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();
    }

    public double getCommissionFactor() {
        return commissionFactor;
    }

    public void setCommissionFactor(double commissionFactor) {
        this.commissionFactor = commissionFactor;
    }

    // Status check
    public boolean isOnline(int timeoutSeconds) {
        if (lastSeen == null)
            return false;
        return LocalDateTime.now().minusSeconds(timeoutSeconds).isBefore(lastSeen);
    }

    // Getters and Setters
    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public String getBroker() {
        return broker;
    }

    public void setBroker(String broker) {
        this.broker = broker;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getEquity() {
        return equity;
    }

    public void setEquity(double equity) {
        this.equity = equity;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long getLastSeenMins() {
        if (lastSeen == null)
            return -1;
        return java.time.Duration.between(lastSeen, LocalDateTime.now()).toMinutes();
    }

    public boolean getOnline() {
        // Simple fallback for the dashboard '0 ONLINE' counter.
        // We consider it online if lastSeen is within the last 60 minutes for this UI
        // counter.
        long mins = getLastSeenMins();
        return mins >= 0 && mins <= 60;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getEaLogAcceptedAt() {
        return eaLogAcceptedAt;
    }

    public void setEaLogAcceptedAt(LocalDateTime eaLogAcceptedAt) {
        this.eaLogAcceptedAt = eaLogAcceptedAt;
    }

    public List<Trade> getOpenTrades() {
        return openTrades;
    }

    public void setOpenTrades(List<Trade> openTrades) {
        this.openTrades = openTrades;
        if (this.openTrades != null) {
            // Apply known statuses
            Set<Long> activeTickets = new HashSet<>();
            for (Trade t : this.openTrades) {
                activeTickets.add(t.getTicket());
                if (syncStatusMap.containsKey(t.getTicket())) {
                    t.setSyncStatus(syncStatusMap.get(t.getTicket()));
                }
            }
            // Prune map to avoid memory leaks
            syncStatusMap.keySet().retainAll(activeTickets);
        }
    }

    public void updateSyncStatuses(Map<Long, String> newStatuses) {
        this.syncStatusMap.putAll(newStatuses);
        if (this.openTrades != null) {
            for (Trade t : this.openTrades) {
                if (newStatuses.containsKey(t.getTicket())) {
                    t.setSyncStatus(newStatuses.get(t.getTicket()));
                }
            }
        }
    }

    public double getTotalProfit() {
        return openTrades.stream().mapToDouble(t -> t.getProfit() + t.getSwap()).sum();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public int getMagicNumberMaxAge() {
        return magicNumberMaxAge;
    }

    public void setMagicNumberMaxAge(int magicNumberMaxAge) {
        this.magicNumberMaxAge = magicNumberMaxAge;
    }

    public int getMagicMinTrades() {
        return magicMinTrades;
    }

    public void setMagicMinTrades(int magicMinTrades) {
        this.magicMinTrades = magicMinTrades;
    }

    // Closed trades history
    private List<ClosedTrade> closedTrades = new ArrayList<>();

    public List<ClosedTrade> getClosedTrades() {
        return closedTrades;
    }

    public void setClosedTrades(List<ClosedTrade> closedTrades) {
        this.closedTrades = closedTrades;
    }

    public double getTotalHistoryProfit() {
        return closedTrades.stream().mapToDouble(t -> t.getProfit() + t.getSwap() + t.getCommission() * commissionFactor).sum();
    }

    /**
     * Build a sorted list of per-magic-number profit entries,
     * combining open and closed trades.
     */
    public List<MagicProfitEntry> getMagicProfitEntries(int maxAgeDays, int minTrades,
            java.util.function.Function<Long, String> nameResolver) {
        // Collect all magic numbers from both open and closed trades
        Set<Long> allMagics = new TreeSet<>();
        openTrades.forEach(t -> allMagics.add(t.getMagicNumber()));
        closedTrades.forEach(t -> allMagics.add(t.getMagicNumber()));

        List<MagicProfitEntry> entries = new ArrayList<>();
        LocalDateTime cutoffDate = maxAgeDays > 0 ? LocalDateTime.now().minusDays(maxAgeDays) : null;

        for (Long magic : allMagics) {
            // Check if magic should be visible
            boolean hasOpenTrades = openTrades.stream().anyMatch(t -> t.getMagicNumber() == magic);

            if (!hasOpenTrades && cutoffDate != null) {
                // Check age of most recent closed trade
                Optional<String> maxCloseTime = closedTrades.stream()
                        .filter(t -> t.getMagicNumber() == magic)
                        .map(ClosedTrade::getCloseTime)
                        .filter(Objects::nonNull)
                        .max(String::compareTo);

                if (maxCloseTime.isPresent()) {
                    try {
                        // The date format is likely "yyyy.MM.dd HH:mm:ss" or similar from MT5
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                                .ofPattern("yyyy.MM.dd HH:mm:ss");
                        LocalDateTime closeTime = LocalDateTime.parse(maxCloseTime.get(), formatter);
                        if (closeTime.isBefore(cutoffDate)) {
                            continue; // Skip this magic number (too old)
                        }
                    } catch (java.time.format.DateTimeParseException e) {
                        // If parsing fails, don't filter it out (safe fallback)
                        System.err.println("Failed to parse date: " + maxCloseTime.get() + " - " + e.getMessage());
                    }
                }
            }

            double openProfit = openTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(Trade::getProfit)
                    .sum();
            double openSwap = openTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(Trade::getSwap)
                    .sum();
            int openCount = (int) openTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .count();
            double closedProfit = closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(ClosedTrade::getProfit)
                    .sum();
            double closedSwap = closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(ClosedTrade::getSwap)
                    .sum();
            double closedCommission = closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(ClosedTrade::getCommission)
                    .sum() * commissionFactor;
            int closedCount = (int) closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .count();

            double totalSwap = openSwap + closedSwap;
            double totalCommission = closedCommission;

            if ((openCount + closedCount) < minTrades) {
                continue;
            }

            // Resolve name
            String magicName = nameResolver != null ? nameResolver.apply(magic) : "";
            if (magicName == null || magicName.isEmpty()) {
                // Fallback: Try to find a comment from open trades
                magicName = openTrades.stream()
                        .filter(t -> t.getMagicNumber() == magic && t.getComment() != null
                                && !t.getComment().isEmpty())
                        .map(Trade::getComment)
                        .findFirst()
                        .orElse("");
                // Fallback: Try closed trades
                if (magicName.isEmpty()) {
                    magicName = closedTrades.stream()
                            .filter(t -> t.getMagicNumber() == magic && t.getComment() != null
                                    && !t.getComment().isEmpty())
                            .map(ClosedTrade::getComment)
                            .findFirst()
                            .orElse("");
                }
            }

            // 1. Gather Symbol Data
            Map<String, Integer> tradedSymbols = new HashMap<>();
            openTrades.stream().filter(t -> t.getMagicNumber() == magic).forEach(t -> {
                tradedSymbols.put(t.getSymbol(), tradedSymbols.getOrDefault(t.getSymbol(), 0) + 1);
            });
            closedTrades.stream().filter(t -> t.getMagicNumber() == magic).forEach(t -> {
                tradedSymbols.put(t.getSymbol(), tradedSymbols.getOrDefault(t.getSymbol(), 0) + 1);
            });

            // 2. Calculate Maximum Historic Drawdown for this Magic Number
            // We replay the closed trades in chronological order (oldest first).
            // `closedTrades` is typically newest first due to DB ID sorting, but we need
            // time-based ASC sort for accurate HWM.
            double maxDrawdownEur = 0.0;
            double cumulativeProfit = 0.0;
            double highWaterMark = 0.0;
            double maxSingleLossEur = 0.0;
            double peakAtMaxDrawdown = 0.0;

            List<ClosedTrade> magicClosedTrades = new ArrayList<>();
            closedTrades.stream().filter(t -> t.getMagicNumber() == magic).forEach(magicClosedTrades::add);

            // Sort ascending by close time
            magicClosedTrades.sort(Comparator.comparing(t -> t.getCloseTime() == null ? "" : t.getCloseTime()));

            for (ClosedTrade ct : magicClosedTrades) {
                double netTradeProfit = ct.getProfit() + ct.getSwap() + ct.getCommission() * commissionFactor;
                cumulativeProfit += netTradeProfit;
                if (cumulativeProfit > highWaterMark) {
                    highWaterMark = cumulativeProfit;
                }
                double currentDrawdown = highWaterMark - cumulativeProfit;
                if (currentDrawdown > maxDrawdownEur) {
                    maxDrawdownEur = currentDrawdown;
                    peakAtMaxDrawdown = highWaterMark;
                }

                if (netTradeProfit < 0) {
                    double loss = Math.abs(netTradeProfit);
                    if (loss > maxSingleLossEur) {
                        maxSingleLossEur = loss;
                    }
                }
            }

            // Prevent double-counting of single loss in max equity drawdown.
            // Since we don't have true MAE, realized max drawdown is currently
            // the most accurate baseline without inflating the values artificially.
            double estimatedMaxEquityDrawdownEur = maxDrawdownEur;

            // Include currently open net profit in max drawdown consideration if applicable
            double currentTotalMagicProfit = cumulativeProfit + openProfit + openSwap;
            double currentOpenDrawdown = highWaterMark - currentTotalMagicProfit;
            if (currentOpenDrawdown > maxDrawdownEur) {
                maxDrawdownEur = currentOpenDrawdown;
                peakAtMaxDrawdown = highWaterMark;
            }
            if (currentOpenDrawdown > estimatedMaxEquityDrawdownEur) {
                estimatedMaxEquityDrawdownEur = currentOpenDrawdown;
            }

            // Calculate % relative to peak equity at the time of the max drawdown
            // Myfxbook Drawdown % formula: Max Drawdown / (Net Deposits + Peak Profit)
            double maxDrawdownPercent = 0.0;
            double maxEquityDrawdownPercent = 0.0;
            double netDeposits = this.balance - this.getTotalHistoryProfit();
            double denominator = netDeposits + peakAtMaxDrawdown;

            if (denominator > 0) {
                maxDrawdownPercent = (maxDrawdownEur / denominator) * 100.0;
                maxEquityDrawdownPercent = (estimatedMaxEquityDrawdownEur / denominator) * 100.0;
            }

            entries.add(
                    new MagicProfitEntry(magic, magicName, openProfit, closedProfit, totalSwap, totalCommission, openSwap,
                            openCount, closedCount,
                            tradedSymbols, maxDrawdownEur, maxDrawdownPercent, estimatedMaxEquityDrawdownEur,
                            maxEquityDrawdownPercent));
        }
        return entries;
    }

    // Helper method for Thymeleaf
    public String getUniqueSymbols() {
        if (openTrades == null)
            return "";
        return openTrades.stream()
                .map(Trade::getSymbol)
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    // Helper method for Thymeleaf to get all traded symbols across all magics
    public String getGlobalTradedSymbolsJson() {
        Map<String, Integer> globalSymbols = new HashMap<>();
        if (openTrades != null) {
            openTrades.forEach(t -> globalSymbols.put(t.getSymbol(), globalSymbols.getOrDefault(t.getSymbol(), 0) + 1));
        }
        if (closedTrades != null) {
            closedTrades
                    .forEach(t -> globalSymbols.put(t.getSymbol(), globalSymbols.getOrDefault(t.getSymbol(), 0) + 1));
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(globalSymbols);
        } catch (Exception e) {
            return "{}";
        }
    }

    // Helper method for Thymeleaf: unique algo names via magic number mapping
    public String getUniqueAlgoNames(Map<Long, String> mappings) {
        if (openTrades == null)
            return "";
        return openTrades.stream()
                .map(t -> mappings.getOrDefault(t.getMagicNumber(), "Magic " + t.getMagicNumber()))
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Calculates performance metrics: Profit %, Monthly Profit %, M-PDD (3-Months).
     * @return Map containing the calculated metrics.
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        double totalHistoryProfit = getTotalHistoryProfit();
        double initialBalance = balance - totalHistoryProfit;
        if (initialBalance <= 0) {
            initialBalance = balance > 0 ? balance : 1.0; // Fallback
        }

        // 1. Profit %
        double profitPct = (totalHistoryProfit / initialBalance) * 100.0;
        metrics.put("profitPct", profitPct);

        // 2. Account Age & Monthly Profit %
        double accountAgeMonths = 1.0;
        if (closedTrades != null && !closedTrades.isEmpty()) {
            Optional<String> oldestTimeStr = closedTrades.stream()
                    .map(ClosedTrade::getCloseTime)
                    .filter(Objects::nonNull)
                    .min(String::compareTo);
            
            if (oldestTimeStr.isPresent()) {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
                    LocalDateTime oldestTime = LocalDateTime.parse(oldestTimeStr.get(), formatter);
                    long days = java.time.temporal.ChronoUnit.DAYS.between(oldestTime, LocalDateTime.now());
                    accountAgeMonths = Math.max(1.0, days / 30.44); // Average days in month
                } catch (Exception e) {
                    // Ignore parsing error, default to 1
                }
            }
        }
        double monthlyProfitPct = profitPct / accountAgeMonths;
        metrics.put("monthlyProfitPct", monthlyProfitPct);

        // 3. M-PDD (3-Months)
        double mpdd3 = 0.0;
        if (closedTrades != null && !closedTrades.isEmpty()) {
            LocalDateTime threeMonthsAgo = LocalDateTime.now().minusDays(90);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
            
            List<ClosedTrade> recentTrades = new ArrayList<>();
            for (ClosedTrade t : closedTrades) {
                if (t.getCloseTime() != null) {
                    try {
                        LocalDateTime ctTime = LocalDateTime.parse(t.getCloseTime(), formatter);
                        if (ctTime.isAfter(threeMonthsAgo)) {
                            recentTrades.add(t);
                        }
                    } catch (Exception e) {
                        // ignore unparseable
                    }
                }
            }
            
            if (!recentTrades.isEmpty()) {
                // Sort by time ascending
                recentTrades.sort(Comparator.comparing(ClosedTrade::getCloseTime));
                
                double recentProfit = recentTrades.stream().mapToDouble(t -> t.getProfit() + t.getSwap() + t.getCommission() * commissionFactor).sum();
                double startBalance3M = balance - recentProfit - getTotalProfit(); // Approximate starting balance 3M ago
                if (startBalance3M <= 0) startBalance3M = balance > 0 ? balance : 1.0;
                
                double threeMonthProfitPct = (recentProfit / startBalance3M) * 100.0;
                double avgMonthly3M = threeMonthProfitPct / 3.0;
                
                double maxDrawdownPct3M = 0;
                double hwm = startBalance3M;
                double runningBalance = startBalance3M;
                
                for (ClosedTrade t : recentTrades) {
                    runningBalance += (t.getProfit() + t.getSwap() + t.getCommission() * commissionFactor);
                    if (runningBalance > hwm) {
                        hwm = runningBalance;
                    }
                    double ddVal = hwm - runningBalance;
                    if (ddVal > 0) {
                        double ddPct = (ddVal / hwm) * 100.0;
                        if (ddPct > maxDrawdownPct3M) {
                            maxDrawdownPct3M = ddPct;
                        }
                    }
                }
                
                // Include current open profit in the 3M drawdown calculus
                runningBalance += getTotalProfit();
                if (runningBalance > hwm) {
                    hwm = runningBalance;
                }
                double currentDdVal = hwm - runningBalance;
                if (currentDdVal > 0) {
                    double currentDdPct = (currentDdVal / hwm) * 100.0;
                    if (currentDdPct > maxDrawdownPct3M) {
                        maxDrawdownPct3M = currentDdPct;
                    }
                }
                
                if (maxDrawdownPct3M > 0) {
                    mpdd3 = avgMonthly3M / maxDrawdownPct3M;
                } else if (avgMonthly3M > 0) {
                    mpdd3 = avgMonthly3M; // If no DD, mpdd3 is essentially the profit, though dividing by 0 is undefined. Using value as fallback.
                }
            }
        }
        // 4. Monthly Breakdowns (Last 3 Calendar Months)
        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        java.time.YearMonth prevMonth = currentMonth.minusMonths(1);
        java.time.YearMonth prevPrevMonth = prevMonth.minusMonths(1);

        double m3Profit = 0.0; // Current Month
        double m2Profit = 0.0; // Prev Month
        double m1Profit = 0.0; // P-Prev Month

        double startBalanceM3 = balance - getTotalProfit(); 
        double startBalanceM2 = startBalanceM3;
        double startBalanceM1 = startBalanceM3;

        if (closedTrades != null && !closedTrades.isEmpty()) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
            for (ClosedTrade t : closedTrades) {
                if (t.getCloseTime() != null) {
                    try {
                        LocalDateTime ctTime = LocalDateTime.parse(t.getCloseTime(), formatter);
                        java.time.YearMonth tradeMonth = java.time.YearMonth.from(ctTime);
                        double netProfit = t.getProfit() + t.getSwap() + t.getCommission() * commissionFactor;
                        
                        if (tradeMonth.equals(currentMonth)) {
                            m3Profit += netProfit;
                        } else if (tradeMonth.equals(prevMonth)) {
                            m2Profit += netProfit;
                        } else if (tradeMonth.equals(prevPrevMonth)) {
                            m1Profit += netProfit;
                        }

                        if (!tradeMonth.isBefore(currentMonth)) {
                            startBalanceM3 -= netProfit;
                            startBalanceM2 -= netProfit;
                            startBalanceM1 -= netProfit;
                        } else if (!tradeMonth.isBefore(prevMonth)) {
                            startBalanceM2 -= netProfit;
                            startBalanceM1 -= netProfit;
                        } else if (!tradeMonth.isBefore(prevPrevMonth)) {
                            startBalanceM1 -= netProfit;
                        }
                    } catch (Exception e) {}
                }
            }
        }
        
        if (startBalanceM1 <= 0) startBalanceM1 = balance > 0 ? balance : 1.0;
        if (startBalanceM2 <= 0) startBalanceM2 = balance > 0 ? balance : 1.0;
        if (startBalanceM3 <= 0) startBalanceM3 = balance > 0 ? balance : 1.0;

        // 5. All-Time Closed DD % (High Water Mark) 
        // Instead of trying to calculate startBalance (which is wrong because getTotalHistoryProfit includes deposits),
        // we walk through ALL trades chronologically and reconstruct the balance curve.
        // Trades with type BUY/SELL are real trades. Everything else (BALANCE, etc.) is a deposit/withdrawal.
        double maxDrawdownPctAllTime = 0.0;
        
        if (closedTrades != null && !closedTrades.isEmpty()) {
            List<ClosedTrade> sortedAllTrades = new ArrayList<>(closedTrades);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
            
            sortedAllTrades.sort((a, b) -> {
                LocalDateTime timeA = null;
                LocalDateTime timeB = null;
                try { if (a.getCloseTime() != null) timeA = LocalDateTime.parse(a.getCloseTime(), formatter); } catch (Exception e) {}
                try { if (b.getCloseTime() != null) timeB = LocalDateTime.parse(b.getCloseTime(), formatter); } catch (Exception e) {}
                if (timeA == null && timeB == null) return 0;
                if (timeA == null) return -1;
                if (timeB == null) return 1;
                return timeA.compareTo(timeB);
            });
            
            // Reconstruct balance from the very first trade.
            // The first entry is typically a deposit. We start at 0 and build up.
            double runBal = 0.0;
            double hwm = 0.0;
            
            for (ClosedTrade t : sortedAllTrades) {
                double netTrade = t.getProfit() + t.getSwap() + t.getCommission() * commissionFactor;
                String tType = t.getType();
                
                // Determine if this is a real trade (BUY/SELL) or a balance operation (deposit/withdrawal)
                boolean isRealTrade = tType != null && (tType.equalsIgnoreCase("BUY") || tType.equalsIgnoreCase("SELL"));
                
                runBal += netTrade;
                
                if (isRealTrade) {
                    // Real trade: update HWM, then check drawdown
                    if (runBal > hwm) {
                        hwm = runBal;
                    }
                    if (hwm > 0) {
                        double ddVal = hwm - runBal;
                        if (ddVal > 0) {
                            double ddPct = (ddVal / hwm) * 100.0;
                            if (ddPct > maxDrawdownPctAllTime) {
                                maxDrawdownPctAllTime = ddPct;
                            }
                        }
                    }
                } else {
                    // Balance operation (deposit/withdrawal): adjust HWM equally
                    // so it doesn't count as trading drawdown
                    hwm += netTrade;
                    if (hwm < 0) hwm = 0;
                    // After a deposit, if balance exceeds hwm, update hwm
                    if (runBal > hwm) {
                        hwm = runBal;
                    }
                }
            }
            
            // Include current open profit in the drawdown check
            runBal += getTotalProfit();
            if (runBal > hwm) {
                hwm = runBal;
            }
            if (hwm > 0) {
                double ddValCurrent = hwm - runBal;
                if (ddValCurrent > 0) {
                    double ddPct = (ddValCurrent / hwm) * 100.0;
                    if (ddPct > maxDrawdownPctAllTime) {
                        maxDrawdownPctAllTime = ddPct;
                    }
                }
            }
        }

        metrics.put("m1Pct", (m1Profit / startBalanceM1) * 100.0);
        metrics.put("m2Pct", (m2Profit / startBalanceM2) * 100.0);
        metrics.put("m3Pct", (m3Profit / startBalanceM3) * 100.0);
        metrics.put("mpdd3", mpdd3);
        metrics.put("maxDrawdownPct", maxDrawdownPctAllTime);
        metrics.put("openTradesCount", openTrades != null ? openTrades.size() : 0);
        metrics.put("closedTradesCount", closedTrades != null ? closedTrades.size() : 0);

        return metrics;
    }
}
