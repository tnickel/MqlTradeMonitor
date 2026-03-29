package de.trademonitor.service;

import de.trademonitor.entity.ClosedTradeEntity;
import de.trademonitor.entity.EquitySnapshotEntity;
import de.trademonitor.model.Account;
import de.trademonitor.repository.ClosedTradeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for advanced strategy analytics:
 * - Heatmap (Weekday x Hour profit aggregation)
 * - Quant KPIs (Profit Factor, Sharpe Ratio, Win Rate, Recovery Factor)
 * - Correlation Matrix between account equity curves
 */
@Service
public class StrategyAnalyticsService {

    private static final DateTimeFormatter TRADE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ClosedTradeRepository closedTradeRepository;
    private final TradeStorage tradeStorage;
    private final AccountManager accountManager;
    private final MagicMappingService magicMappingService;

    public StrategyAnalyticsService(ClosedTradeRepository closedTradeRepository,
                                     TradeStorage tradeStorage,
                                     AccountManager accountManager,
                                     MagicMappingService magicMappingService) {
        this.closedTradeRepository = closedTradeRepository;
        this.tradeStorage = tradeStorage;
        this.accountManager = accountManager;
        this.magicMappingService = magicMappingService;
    }

    // ==================== HEATMAP ====================

    /**
     * Build a 7x24 heatmap of profit, trade count, and win rate
     * for a specific account or all accounts.
     */
    public Map<String, Object> buildHeatmap(Long accountId, String type) {
        List<ClosedTradeEntity> trades;
        if (accountId != null) {
            trades = closedTradeRepository.findByAccountId(accountId);
        } else {
            // All accounts, optionally filtered by type
            trades = new ArrayList<>();
            for (Account acc : accountManager.getAccountsSortedByPrivilege()) {
                if (type != null && !type.isEmpty() && !type.equalsIgnoreCase(acc.getType())) continue;
                trades.addAll(closedTradeRepository.findByAccountId(acc.getAccountId()));
            }
        }

        double[][] profit = new double[7][24];
        int[][] tradeCount = new int[7][24];
        int[][] winCount = new int[7][24];

        for (ClosedTradeEntity trade : trades) {
            if (trade.getCloseTime() == null) continue;
            try {
                LocalDateTime dt = LocalDateTime.parse(trade.getCloseTime(), TRADE_FMT);
                int dayIdx = dt.getDayOfWeek().getValue() - 1; // Mon=0, Sun=6
                int hour = dt.getHour();
                profit[dayIdx][hour] += trade.getProfit();
                tradeCount[dayIdx][hour]++;
                if (trade.getProfit() > 0) winCount[dayIdx][hour]++;
            } catch (Exception ignored) {}
        }

        // Calculate win rates
        double[][] winRate = new double[7][24];
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                winRate[d][h] = tradeCount[d][h] > 0
                        ? (winCount[d][h] * 100.0 / tradeCount[d][h])
                        : 0.0;
                profit[d][h] = Math.round(profit[d][h] * 100.0) / 100.0;
                winRate[d][h] = Math.round(winRate[d][h] * 10.0) / 10.0;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profit", profit);
        result.put("tradeCount", tradeCount);
        result.put("winRate", winRate);
        result.put("totalTrades", trades.size());
        return result;
    }

    // ==================== STRATEGY KPIs ====================

    /**
     * Calculate quant KPIs for each magic number in an account.
     */
    public List<Map<String, Object>> getStrategyKpis(long accountId) {
        List<ClosedTradeEntity> allTrades = closedTradeRepository.findByAccountId(accountId);
        Map<Long, String> mappings = magicMappingService.getAllMappings();

        // Group by magic number
        Map<Long, List<ClosedTradeEntity>> byMagic = allTrades.stream()
                .collect(Collectors.groupingBy(ClosedTradeEntity::getMagicNumber));

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map.Entry<Long, List<ClosedTradeEntity>> entry : byMagic.entrySet()) {
            Long magic = entry.getKey();
            List<ClosedTradeEntity> trades = entry.getValue();

            if (trades.size() < 3) continue; // Need minimum trades for meaningful stats

            Map<String, Object> kpi = new LinkedHashMap<>();
            kpi.put("magicNumber", magic);
            kpi.put("name", mappings.getOrDefault(magic, "Magic " + magic));
            kpi.put("totalTrades", trades.size());

            // Sort by close time
            trades.sort(Comparator.comparing(t -> t.getCloseTime() == null ? "" : t.getCloseTime()));

            // Profit Factor
            double grossProfit = trades.stream().filter(t -> t.getProfit() > 0).mapToDouble(ClosedTradeEntity::getProfit).sum();
            double grossLoss = Math.abs(trades.stream().filter(t -> t.getProfit() < 0).mapToDouble(ClosedTradeEntity::getProfit).sum());
            double profitFactor = grossLoss > 0 ? Math.round(grossProfit / grossLoss * 100.0) / 100.0 : (grossProfit > 0 ? 99.99 : 0.0);
            kpi.put("profitFactor", profitFactor);

            // Win Rate
            long wins = trades.stream().filter(t -> t.getProfit() > 0).count();
            double winRate = Math.round(wins * 1000.0 / trades.size()) / 10.0;
            kpi.put("winRate", winRate);

            // Average Trade
            double avgTrade = Math.round(trades.stream().mapToDouble(ClosedTradeEntity::getProfit).average().orElse(0.0) * 100.0) / 100.0;
            kpi.put("avgTrade", avgTrade);

            // Total net profit
            double totalProfit = Math.round(trades.stream().mapToDouble(ClosedTradeEntity::getProfit).sum() * 100.0) / 100.0;
            kpi.put("totalProfit", totalProfit);

            // Max Drawdown (from cumulative P/L curve)
            double cumulative = 0, hwm = 0, maxDD = 0;
            for (ClosedTradeEntity t : trades) {
                cumulative += t.getProfit();
                if (cumulative > hwm) hwm = cumulative;
                double dd = hwm - cumulative;
                if (dd > maxDD) maxDD = dd;
            }
            maxDD = Math.round(maxDD * 100.0) / 100.0;
            kpi.put("maxDrawdown", maxDD);

            // Recovery Factor
            double recoveryFactor = maxDD > 0 ? Math.round(totalProfit / maxDD * 100.0) / 100.0 : 0.0;
            kpi.put("recoveryFactor", recoveryFactor);

            // Sharpe Ratio (annualized, based on daily returns)
            kpi.put("sharpeRatio", calculateSharpeRatio(trades));

            // Longest Win/Loss Streaks
            int winStreak = 0, lossStreak = 0, maxWinStreak = 0, maxLossStreak = 0;
            for (ClosedTradeEntity t : trades) {
                if (t.getProfit() > 0) {
                    winStreak++;
                    lossStreak = 0;
                    maxWinStreak = Math.max(maxWinStreak, winStreak);
                } else {
                    lossStreak++;
                    winStreak = 0;
                    maxLossStreak = Math.max(maxLossStreak, lossStreak);
                }
            }
            kpi.put("maxWinStreak", maxWinStreak);
            kpi.put("maxLossStreak", maxLossStreak);

            // Sparkline data (cumulative profit curve, max 50 points)
            List<Double> sparkline = new ArrayList<>();
            double cum = 0;
            int step = Math.max(1, trades.size() / 50);
            for (int i = 0; i < trades.size(); i++) {
                cum += trades.get(i).getProfit();
                if (i % step == 0 || i == trades.size() - 1) {
                    sparkline.add(Math.round(cum * 100.0) / 100.0);
                }
            }
            kpi.put("sparkline", sparkline);

            results.add(kpi);
        }

        // Sort by total profit descending
        results.sort((a, b) -> Double.compare((double) b.get("totalProfit"), (double) a.get("totalProfit")));

        return results;
    }

    /**
     * Global strategy leaderboard across all accounts.
     */
    public List<Map<String, Object>> getGlobalLeaderboard(String type) {
        List<Map<String, Object>> all = new ArrayList<>();

        for (Account acc : accountManager.getAccountsSortedByPrivilege()) {
            if (type != null && !type.isEmpty() && !type.equalsIgnoreCase(acc.getType())) continue;
            List<Map<String, Object>> kpis = getStrategyKpis(acc.getAccountId());
            for (Map<String, Object> kpi : kpis) {
                kpi.put("accountId", acc.getAccountId());
                kpi.put("accountName", acc.getName() != null ? acc.getName() : "Account " + acc.getAccountId());
                kpi.put("accountType", acc.getType());
                all.add(kpi);
            }
        }

        // Sort by profit factor descending, then total profit
        all.sort((a, b) -> {
            int cmp = Double.compare((double) b.get("profitFactor"), (double) a.get("profitFactor"));
            if (cmp != 0) return cmp;
            return Double.compare((double) b.get("totalProfit"), (double) a.get("totalProfit"));
        });

        // Add rank
        for (int i = 0; i < all.size(); i++) {
            all.get(i).put("rank", i + 1);
        }

        return all;
    }

    // ==================== CORRELATION MATRIX ====================

    /**
     * Calculate Pearson correlation matrix between account daily returns.
     */
    public Map<String, Object> getCorrelationMatrix(String type, String period) {
        List<Account> accounts = accountManager.getAccountsSortedByPrivilege().stream()
                .filter(a -> type == null || type.isEmpty() || type.equalsIgnoreCase(a.getType()))
                .collect(Collectors.toList());

        // Build daily return series for each account
        List<String> accountNames = new ArrayList<>();
        List<List<Double>> allReturns = new ArrayList<>();

        java.util.function.Predicate<String> dateFilter = getSnapshotPeriodFilter(period);

        for (Account acc : accounts) {
            List<EquitySnapshotEntity> snapshots = tradeStorage.loadEquitySnapshots(acc.getAccountId());

            // Downsample to daily (take last snapshot per day)
            Map<String, Double> dailyEquity = new LinkedHashMap<>();
            for (EquitySnapshotEntity snap : snapshots) {
                if (snap.getTimestamp() == null) continue;
                if (!dateFilter.test(snap.getTimestamp())) continue;
                String day = snap.getTimestamp().substring(0, 10); // yyyy-MM-dd
                dailyEquity.put(day, snap.getEquity());
            }

            if (dailyEquity.size() < 5) continue; // Need minimum data points

            // Calculate daily returns
            List<Double> returns = new ArrayList<>();
            double prev = -1;
            for (double eq : dailyEquity.values()) {
                if (prev > 0) {
                    returns.add((eq - prev) / prev);
                }
                prev = eq;
            }

            if (returns.size() >= 4) {
                accountNames.add(acc.getName() != null ? acc.getName() : "Acc " + acc.getAccountId());
                allReturns.add(returns);
            }
        }

        int n = accountNames.size();
        double[][] matrix = new double[n][n];

        // Compute pairwise Pearson correlation
        for (int i = 0; i < n; i++) {
            matrix[i][i] = 1.0;
            for (int j = i + 1; j < n; j++) {
                double corr = pearsonCorrelation(allReturns.get(i), allReturns.get(j));
                corr = Math.round(corr * 100.0) / 100.0;
                matrix[i][j] = corr;
                matrix[j][i] = corr;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", accountNames);
        result.put("matrix", matrix);
        return result;
    }

    // ==================== DRAWDOWN CURVES ====================

    /**
     * Calculate percentage drawdown time series for each account.
     */
    public List<Map<String, Object>> getDrawdownCurves(String type, String period) {
        List<Account> accounts = accountManager.getAccountsSortedByPrivilege().stream()
                .filter(a -> type == null || type.isEmpty() || type.equalsIgnoreCase(a.getType()))
                .collect(Collectors.toList());

        java.util.function.Predicate<String> dateFilter = getSnapshotPeriodFilter(period);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Account acc : accounts) {
            List<EquitySnapshotEntity> snapshots = tradeStorage.loadEquitySnapshots(acc.getAccountId());

            double peak = 0;
            Map<String, Double> hourlyDrawdown = new LinkedHashMap<>();
            Map<String, String> hourToTs = new LinkedHashMap<>();

            for (EquitySnapshotEntity snap : snapshots) {
                if (snap.getTimestamp() == null || !dateFilter.test(snap.getTimestamp())) continue;

                double eq = snap.getEquity();
                if (eq > peak) peak = eq;

                double ddPct = peak > 0 ? ((eq - peak) / peak) * 100.0 : 0.0;
                
                // Downsample to max 1 point per hour
                String hourStr = snap.getTimestamp().length() >= 13 ? snap.getTimestamp().substring(0, 13) : snap.getTimestamp();
                
                // Keep the lowest (most severe) drawdown per hour to preserve max DD visually
                Double currentMin = hourlyDrawdown.get(hourStr);
                if (currentMin == null || ddPct < currentMin) {
                    hourlyDrawdown.put(hourStr, ddPct);
                    hourToTs.put(hourStr, snap.getTimestamp());
                }
            }

            List<String> timestamps = new ArrayList<>(hourToTs.values());
            List<Double> drawdowns = new ArrayList<>();
            for (String ts : timestamps) {
                String hourStr = ts.length() >= 13 ? ts.substring(0, 13) : ts;
                drawdowns.add(Math.round(hourlyDrawdown.get(hourStr) * 100.0) / 100.0);
            }

            if (!timestamps.isEmpty()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("accountId", acc.getAccountId());
                entry.put("name", acc.getName() != null ? acc.getName() : "Account " + acc.getAccountId());
                entry.put("type", acc.getType());
                entry.put("timestamps", timestamps);
                entry.put("drawdown", drawdowns);
                result.add(entry);
            }
        }

        result.sort((a, b) -> {
            String tA = (String) a.get("type"), tB = (String) b.get("type");
            if (!Objects.equals(tA, tB)) return "REAL".equals(tA) ? -1 : 1;
            return String.CASE_INSENSITIVE_ORDER.compare((String) a.get("name"), (String) b.get("name"));
        });

        return result;
    }

    // ==================== HELPER METHODS ====================

    private double calculateSharpeRatio(List<ClosedTradeEntity> trades) {
        if (trades.size() < 5) return 0.0;

        // Group profit by day
        Map<String, Double> dailyProfit = new LinkedHashMap<>();
        for (ClosedTradeEntity t : trades) {
            if (t.getCloseTime() == null) continue;
            String day = t.getCloseTime().substring(0, 10); // yyyy.MM.dd
            dailyProfit.merge(day, t.getProfit(), Double::sum);
        }

        if (dailyProfit.size() < 3) return 0.0;

        double[] returns = dailyProfit.values().stream().mapToDouble(Double::doubleValue).toArray();
        double mean = Arrays.stream(returns).average().orElse(0.0);
        double variance = Arrays.stream(returns).map(r -> Math.pow(r - mean, 2)).average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) return 0.0;

        // Annualized: multiply by sqrt(252 trading days)
        double sharpe = (mean / stdDev) * Math.sqrt(252);
        return Math.round(sharpe * 100.0) / 100.0;
    }

    private double pearsonCorrelation(List<Double> x, List<Double> y) {
        int n = Math.min(x.size(), y.size());
        if (n < 3) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            double xi = x.get(i), yi = y.get(i);
            sumX += xi;
            sumY += yi;
            sumXY += xi * yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return denominator == 0 ? 0.0 : numerator / denominator;
    }

    private java.util.function.Predicate<String> getSnapshotPeriodFilter(String period) {
        if (period == null || period.isEmpty()) return s -> true;
        java.time.LocalDate today = java.time.LocalDate.now();

        switch (period.toLowerCase()) {
            case "daily":
                String todayStr = today.toString();
                return s -> s.startsWith(todayStr);
            case "weekly":
                java.time.LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
                String weekStr = weekStart.toString();
                return s -> s.compareTo(weekStr) >= 0;
            case "monthly":
                String monthStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                return s -> s.startsWith(monthStr);
            default:
                return s -> true;
        }
    }
}
