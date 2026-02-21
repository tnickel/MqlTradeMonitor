package de.trademonitor.service;

import de.trademonitor.dto.TradeComparisonDto;
import de.trademonitor.entity.ClosedTradeEntity;
import de.trademonitor.repository.ClosedTradeRepository;
import de.trademonitor.model.Account;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TradeComparisonService {

    private final AccountManager accountManager;
    private final ClosedTradeRepository closedTradeRepository;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    private static final long MAX_TIME_DIFF_SECONDS = 120; // 2 minutes window

    public TradeComparisonService(AccountManager accountManager, ClosedTradeRepository closedTradeRepository) {
        this.accountManager = accountManager;
        this.closedTradeRepository = closedTradeRepository;
    }

    /**
     * Compares real trades with demo trades for a specific account or all real
     * accounts if accountId is null, filtered by period.
     */
    public List<TradeComparisonDto> compareTrades(Long realAccountId, String period) {
        List<TradeComparisonDto> results = new ArrayList<>();

        LocalDateTime periodStart = getPeriodStartDate(period);

        List<Account> realAccounts = new ArrayList<>();
        if (realAccountId != null) {
            Account acc = accountManager.getAccount(realAccountId);
            if (acc != null && "REAL".equalsIgnoreCase(acc.getType())) {
                realAccounts.add(acc);
            }
        } else {
            realAccounts = accountManager.getAccountsSortedByPrivilege().stream()
                    .filter(a -> "REAL".equalsIgnoreCase(a.getType()))
                    .collect(Collectors.toList());
        }

        List<Account> demoAccounts = accountManager.getAccountsSortedByPrivilege().stream()
                .filter(a -> !"REAL".equalsIgnoreCase(a.getType()))
                .collect(Collectors.toList());

        // Pre-fetch all demo trades to avoid DB queries in the loop
        List<ClosedTradeEntity> allDemoTrades = new ArrayList<>();
        for (Account demoAcc : demoAccounts) {
            allDemoTrades.addAll(closedTradeRepository.findByAccountId(demoAcc.getAccountId()));
        }

        for (Account realAcc : realAccounts) {
            List<ClosedTradeEntity> realTrades = closedTradeRepository.findByAccountId(realAcc.getAccountId());

            for (ClosedTradeEntity realTrade : realTrades) {
                // Filter by period
                if (periodStart != null && realTrade.getCloseTime() != null) {
                    try {
                        LocalDateTime closeTime = LocalDateTime.parse(realTrade.getCloseTime(), formatter);
                        if (closeTime.isBefore(periodStart)) {
                            continue;
                        }
                    } catch (Exception ignored) {
                    }
                }

                TradeComparisonDto dto = new TradeComparisonDto();
                dto.setRealTrade(realTrade);
                dto.setRealAccountName(realAcc.getName());

                // Find matching demo trade
                ClosedTradeEntity matchedDemo = findMatchingDemoTrade(realTrade, allDemoTrades);

                if (matchedDemo != null) {
                    dto.setDemoTrade(matchedDemo);
                    // Get demo account name
                    Account matchedDemoAcc = demoAccounts.stream()
                            .filter(a -> a.getAccountId() == matchedDemo.getAccountId())
                            .findFirst().orElse(null);
                    dto.setDemoAccountName(matchedDemoAcc != null ? matchedDemoAcc.getName() : "Unknown Demo");

                    dto.setStatus("MATCHED");

                    // Calculate delays and slippage
                    calculateMetrics(dto);
                } else {
                    dto.setStatus("NOT FOUND");
                }

                results.add(dto);
            }
        }

        // Sort results: newest closed trades first
        results.sort((a, b) -> {
            String timeA = a.getRealTrade().getCloseTime();
            String timeB = b.getRealTrade().getCloseTime();
            if (timeA == null && timeB == null)
                return 0;
            if (timeA == null)
                return 1;
            if (timeB == null)
                return -1;
            return timeB.compareTo(timeA);
        });

        return results;
    }

    private LocalDateTime getPeriodStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        switch (period != null ? period.toLowerCase() : "") {
            case "today":
                return now.truncatedTo(ChronoUnit.DAYS);
            case "this-week":
                return now.with(java.time.DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
            case "this-month":
                return now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            case "all":
            default:
                return null;
        }
    }

    private ClosedTradeEntity findMatchingDemoTrade(ClosedTradeEntity realTrade, List<ClosedTradeEntity> demoTrades) {
        if (realTrade.getOpenTime() == null || realTrade.getSymbol() == null || realTrade.getType() == null) {
            return null;
        }

        LocalDateTime realOpenTime;
        try {
            realOpenTime = LocalDateTime.parse(realTrade.getOpenTime(), formatter);
        } catch (Exception e) {
            return null; // Cannot parse time
        }

        ClosedTradeEntity bestMatch = null;
        long smallestDiff = Long.MAX_VALUE;

        for (ClosedTradeEntity demoTrade : demoTrades) {
            if (demoTrade.getOpenTime() == null)
                continue;

            // 1. Symbol and Type MUST match exactly
            if (!realTrade.getSymbol().equalsIgnoreCase(demoTrade.getSymbol()))
                continue;
            if (!realTrade.getType().equalsIgnoreCase(demoTrade.getType()))
                continue;

            try {
                LocalDateTime demoOpenTime = LocalDateTime.parse(demoTrade.getOpenTime(), formatter);
                long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(realOpenTime, demoOpenTime));

                // Time must be within window OR SL must be exact match and > 0
                if (diffSeconds <= MAX_TIME_DIFF_SECONDS) {
                    if (diffSeconds < smallestDiff) {
                        smallestDiff = diffSeconds;
                        bestMatch = demoTrade;
                    }
                } else if (bestMatch == null && realTrade.getSl() != null && realTrade.getSl() > 0
                        && demoTrade.getSl() != null && Math.abs(realTrade.getSl() - demoTrade.getSl()) < 0.00001) {
                    // Fallback: If time differ by more than 120s, but SL matches exactly (within
                    // floating point precision)
                    // and we haven't found a better time-based match yet, we take it.
                    bestMatch = demoTrade;
                }
            } catch (Exception ignored) {
            }
        }

        return bestMatch;
    }

    private void calculateMetrics(TradeComparisonDto dto) {
        ClosedTradeEntity real = dto.getRealTrade();
        ClosedTradeEntity demo = dto.getDemoTrade();

        // Open/Close Delays
        try {
            if (real.getOpenTime() != null && demo.getOpenTime() != null) {
                LocalDateTime rOpen = LocalDateTime.parse(real.getOpenTime(), formatter);
                LocalDateTime dOpen = LocalDateTime.parse(demo.getOpenTime(), formatter);
                dto.setOpenDelaySeconds(ChronoUnit.SECONDS.between(dOpen, rOpen)); // Positive means real is later
            }
            if (real.getCloseTime() != null && demo.getCloseTime() != null) {
                LocalDateTime rClose = LocalDateTime.parse(real.getCloseTime(), formatter);
                LocalDateTime dClose = LocalDateTime.parse(demo.getCloseTime(), formatter);
                dto.setCloseDelaySeconds(ChronoUnit.SECONDS.between(dClose, rClose)); // Positive means real is later
            }
        } catch (Exception ignored) {
        }

        // Slippage Calculation (Price difference)
        // Positive Slippage = Worse price for real account
        // Negative Slippage = Better price for real account
        double openDiff = real.getOpenPrice() - demo.getOpenPrice();
        double closeDiff = real.getClosePrice() - demo.getClosePrice();

        if ("BUY".equalsIgnoreCase(real.getType())) {
            dto.setOpenSlippage(openDiff); // Real bought higher than Demo -> Bad (Positive)
            dto.setCloseSlippage(closeDiff * -1); // Real sold lower than Demo -> Bad (Positive)
        } else if ("SELL".equalsIgnoreCase(real.getType())) {
            dto.setOpenSlippage(openDiff * -1); // Real sold lower than Demo -> Bad (Positive)
            dto.setCloseSlippage(closeDiff); // Real bought higher than demo -> Bad (Positive)
        }

        // Format for display: precision to 5 decimals. Very tiny slippage counts as 0.
        if (dto.getOpenSlippage() != null) {
            double val = Math.round(dto.getOpenSlippage() * 100000.0) / 100000.0;
            if (Math.abs(val) < 0.00001)
                val = 0.0;
            dto.setOpenSlippage(val);
            if (val == 0.0) {
                dto.setOpenSlippageFormatted("0");
            } else {
                dto.setOpenSlippageFormatted(String.format(java.util.Locale.US, "%.5f", val));
            }
        }

        if (dto.getCloseSlippage() != null) {
            double val = Math.round(dto.getCloseSlippage() * 100000.0) / 100000.0;
            if (Math.abs(val) < 0.00001)
                val = 0.0;
            dto.setCloseSlippage(val);
            if (val == 0.0) {
                dto.setCloseSlippageFormatted("0");
            } else {
                dto.setCloseSlippageFormatted(String.format(java.util.Locale.US, "%.5f", val));
            }
        }
    }
}
