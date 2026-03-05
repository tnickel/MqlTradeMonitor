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

    private String section = "TOP"; // Default to TOP (Deprecated)
    private Long sectionId;
    private int displayOrder = 0;
    private int magicNumberMaxAge = 30; // Default 30 days

    // Transient field for sync warning
    private boolean syncWarning;

    // Transient fields for API error warning
    private String lastErrorMsg;
    private LocalDateTime lastErrorTime;

    public boolean isSyncWarning() {
        return syncWarning;
    }

    public void setSyncWarning(boolean syncWarning) {
        this.syncWarning = syncWarning;
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
        return openTrades.stream().mapToDouble(Trade::getProfit).sum();
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

    // Closed trades history
    private List<ClosedTrade> closedTrades = new ArrayList<>();

    public List<ClosedTrade> getClosedTrades() {
        return closedTrades;
    }

    public void setClosedTrades(List<ClosedTrade> closedTrades) {
        this.closedTrades = closedTrades;
    }

    public double getTotalHistoryProfit() {
        return closedTrades.stream().mapToDouble(ClosedTrade::getProfit).sum();
    }

    /**
     * Build a sorted list of per-magic-number profit entries,
     * combining open and closed trades.
     */
    public List<MagicProfitEntry> getMagicProfitEntries(int maxAgeDays,
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
            int openCount = (int) openTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .count();
            double closedProfit = closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .mapToDouble(ClosedTrade::getProfit)
                    .sum();
            int closedCount = (int) closedTrades.stream()
                    .filter(t -> t.getMagicNumber() == magic)
                    .count();

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

            List<ClosedTrade> magicClosedTrades = new ArrayList<>();
            closedTrades.stream().filter(t -> t.getMagicNumber() == magic).forEach(magicClosedTrades::add);

            // Sort ascending by close time
            magicClosedTrades.sort(Comparator.comparing(t -> t.getCloseTime() == null ? "" : t.getCloseTime()));

            for (ClosedTrade ct : magicClosedTrades) {
                cumulativeProfit += ct.getProfit();
                if (cumulativeProfit > highWaterMark) {
                    highWaterMark = cumulativeProfit;
                }
                double currentDrawdown = highWaterMark - cumulativeProfit;
                if (currentDrawdown > maxDrawdownEur) {
                    maxDrawdownEur = currentDrawdown;
                }

                if (ct.getProfit() < 0) {
                    // Include commission and swap for single loss estimate
                    double loss = Math.abs(ct.getProfit() + ct.getCommission() + ct.getSwap());
                    if (loss > maxSingleLossEur) {
                        maxSingleLossEur = loss;
                    }
                }
            }

            // Estimate Equity Drawdown: Realized DD + Max Single Loss
            double estimatedMaxEquityDrawdownEur = maxDrawdownEur + maxSingleLossEur;

            // Include currently open profit in max drawdown consideration if applicable
            double currentTotalMagicProfit = cumulativeProfit + openProfit;
            double currentOpenDrawdown = highWaterMark - currentTotalMagicProfit;
            if (currentOpenDrawdown > maxDrawdownEur) {
                maxDrawdownEur = currentOpenDrawdown;
            }
            if (currentOpenDrawdown > estimatedMaxEquityDrawdownEur) {
                estimatedMaxEquityDrawdownEur = currentOpenDrawdown;
            }

            // Calculate % relative to current global balance (approximation)
            double maxDrawdownPercent = 0.0;
            double maxEquityDrawdownPercent = 0.0;
            if (this.balance > 0) {
                maxDrawdownPercent = (maxDrawdownEur / this.balance) * 100.0;
                maxEquityDrawdownPercent = (estimatedMaxEquityDrawdownEur / this.balance) * 100.0;
            }

            entries.add(
                    new MagicProfitEntry(magic, magicName, openProfit, closedProfit, openCount, closedCount,
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
}
