package de.trademonitor.service;

import de.trademonitor.entity.CopierLinkEntity;
import de.trademonitor.model.Account;
import de.trademonitor.model.Trade;
import de.trademonitor.repository.CopierLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class CopierVerificationService {

    private static final Logger LOG = Logger.getLogger(CopierVerificationService.class.getName());
    private static final DateTimeFormatter MT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    
    // In-memory cache for the confirmed time offset (in seconds) between two specific accounts
    // Key format: "targetId_sourceId"
    private static final Map<String, Long> knownOffsets = new ConcurrentHashMap<>();

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private CopierLinkRepository copierLinkRepository;

    @Autowired
    private GlobalConfigService globalConfigService;

    private LocalDateTime lastRunTime = LocalDateTime.MIN;

    @Scheduled(fixedDelay = 60000) // Check every minute
    public void verifyTrades() {
        int intervalMins = globalConfigService.getCopierIntervalMins();
        if (ChronoUnit.MINUTES.between(lastRunTime, LocalDateTime.now()) < intervalMins) {
            return;
        }

        LOG.info("Running scheduled Copier Trade Verification...");
        lastRunTime = LocalDateTime.now();

        // 1. Reset all copier errors
        for (Account account : accountManager.getAllAccounts()) {
            if (Boolean.TRUE.equals(account.getCopierError())) {
                accountManager.updateCopierError(account.getAccountId(), false, null);
            }
        }

        // 2. Fetch all active copy links and collect unique targets
        List<CopierLinkEntity> links = copierLinkRepository.findAll();
        java.util.Set<Long> targetIdsToVerify = new java.util.HashSet<>();
        for (CopierLinkEntity link : links) {
            targetIdsToVerify.add(link.getTargetAccountId());
        }

        for (Long targetId : targetIdsToVerify) {
            de.trademonitor.dto.CopierVerificationReportDto report = generateReport(targetId);
            if (report == null || report.getTargetTrades() == null) continue;

            // 3. Check if any target trade is unmatched (skip exempt trades)
            for (de.trademonitor.dto.TargetTradeMatchDto match : report.getTargetTrades()) {
                if (!match.getIsMatched() && !match.getIsExempt()) {
                    Trade targetTrade = match.getTargetTrade();
                    String typeStr = targetTrade.getType() != null ? targetTrade.getType().toUpperCase() : "";
                    String readableType = (typeStr.equals("0") || typeStr.equals("BUY")) ? "BUY" : "SELL";
                    String errorMsg = String.format("Trade %s (%s) hat keinen entsprechenden Source-Trade",
                            targetTrade.getSymbol(), readableType);
                    LOG.warning("Copier Error on Account " + targetId + ": " + errorMsg);
                    accountManager.updateCopierError(targetId, true, errorMsg);
                    break; // One error is enough to highlight the account
                }
            }
        }
    }

    public de.trademonitor.dto.CopierVerificationReportDto generateReport(long targetAccountId) {
        Account target = accountManager.getAccount(targetAccountId);
        if (target == null) return null;

        de.trademonitor.dto.CopierVerificationReportDto report = new de.trademonitor.dto.CopierVerificationReportDto();
        report.setTargetAccountId(targetAccountId);
        report.setTargetAccountName(target.getName() != null ? target.getName() : String.valueOf(targetAccountId));
        report.setTargetGmtOffsetSeconds(target.getServerTimeOffsetSeconds() != null ? target.getServerTimeOffsetSeconds() : 0L);
        
        List<CopierLinkEntity> incomingLinks = copierLinkRepository.findByTargetAccountId(targetAccountId);
        List<CopierLinkEntity> outgoingLinks = copierLinkRepository.findBySourceAccountId(targetAccountId);
        
        if (incomingLinks.isEmpty() && outgoingLinks.isEmpty()) {
            return null; // Not part of map
        }
        
        // Determine role: Sender if only outgoing, Receiver if only incoming, Both otherwise.
        if (incomingLinks.isEmpty() && !outgoingLinks.isEmpty()) {
            report.setRole("SENDER");
        } else if (!incomingLinks.isEmpty() && outgoingLinks.isEmpty()) {
            report.setRole("RECEIVER");
        } else {
            report.setRole("MIXED"); // acts as both, but double click shows receiver logic
        }

        List<de.trademonitor.dto.TargetTradeMatchDto> targetMatches = new java.util.ArrayList<>();
        List<de.trademonitor.dto.SourceAccountReportDto> sourceReports = new java.util.ArrayList<>();

        if (report.getRole().equals("SENDER")) {
            // It's purely a sender, just return its own open trades as the "source"
            de.trademonitor.dto.SourceAccountReportDto sReport = new de.trademonitor.dto.SourceAccountReportDto();
            sReport.setSourceAccountId(target.getAccountId());
            sReport.setSourceAccountName(target.getName() != null ? target.getName() : String.valueOf(target.getAccountId()));
            sReport.setSourceTrades(target.getOpenTrades());
            sourceReports.add(sReport);
            
            report.setSources(sourceReports);
            report.setTargetTrades(targetMatches); // empty
            return report;
        }

        List<Account> sources = new java.util.ArrayList<>();
        int toleranceSeconds = globalConfigService.getCopierToleranceSeconds();
        java.util.Set<Long> exemptMagics = globalConfigService.getSyncExemptMagicNumbers();

        for (CopierLinkEntity link : incomingLinks) {
            Account source = accountManager.getAccount(link.getSourceAccountId());
            if (source != null) {
                sources.add(source);
                de.trademonitor.dto.SourceAccountReportDto sReport = new de.trademonitor.dto.SourceAccountReportDto();
                sReport.setSourceAccountId(source.getAccountId());
                sReport.setSourceAccountName(source.getName() != null ? source.getName() : String.valueOf(source.getAccountId()));
                sReport.setSourceTrades(source.getOpenTrades());
                sReport.setGmtOffsetSeconds(source.getServerTimeOffsetSeconds() != null ? source.getServerTimeOffsetSeconds() : 0L);
                sourceReports.add(sReport);
            }
        }
        report.setSources(sourceReports);

        java.util.Set<Long> matchedSourceTickets = new java.util.HashSet<>();

        for (Trade targetTrade : target.getOpenTrades()) {
            String typeStr = targetTrade.getType() != null ? targetTrade.getType().toUpperCase() : "";
            if (!typeStr.equals("0") && !typeStr.equals("1") && !typeStr.equals("BUY") && !typeStr.equals("SELL")) {
                continue; 
            }

            de.trademonitor.dto.TargetTradeMatchDto matchDto = new de.trademonitor.dto.TargetTradeMatchDto();
            matchDto.setTargetTrade(targetTrade);
            matchDto.setIsMatched(false);

            // Check if this trade's magic number is in the exempt list
            if (exemptMagics.contains(targetTrade.getMagicNumber())) {
                matchDto.setIsExempt(true);
            }

            for (Account source : sources) {
                Trade matchedSourceTrade = findMatchingSourceTrade(targetTrade, target, source, toleranceSeconds, matchedSourceTickets);
                if (matchedSourceTrade != null) {
                    matchDto.setIsMatched(true);
                    matchDto.setMatchedBySourceName(source.getName() != null ? source.getName() : String.valueOf(source.getAccountId()));
                    matchDto.setMatchedBySourceTicket(matchedSourceTrade.getTicket());
                    break;
                }
            }
            targetMatches.add(matchDto);
        }

        report.setTargetTrades(targetMatches);
        return report;
    }

    private Trade findMatchingSourceTrade(Trade targetTrade, Account targetAcc, Account sourceAcc, int toleranceSeconds, java.util.Set<Long> matchedSourceTickets) {
        LocalDateTime targetTime;
        try {
            targetTime = LocalDateTime.parse(targetTrade.getOpenTime(), MT_TIME_FORMAT);
        } catch (Exception e) {
            // Unparseable time: treat as match to prevent false alarms
            Trade dummy = new Trade();
            dummy.setTicket(-1);
            return dummy;
        }

        // The user suggested trying raw hour offsets up to ±3 hours because broker times can vary.
        // We look up the known offset for this broker pair and try it first.
        String pairKey = targetAcc.getAccountId() + "_" + sourceAcc.getAccountId();
        Long cachedOffset = knownOffsets.get(pairKey);
        
        long[] shiftsToTry;
        if (cachedOffset != null) {
            // Priority: Try cached offset first, then ±1h, ±2h, ±3h
            shiftsToTry = new long[] {
                cachedOffset,
                0, 3600, -3600, 7200, -7200, 10800, -10800
            };
        } else {
            // Priority: Try 0, then ±1h, ±2h, ±3h
            shiftsToTry = new long[] {
                0, 3600, -3600, 7200, -7200, 10800, -10800
            };
        }

        // We check the "most likely" offset across ALL source trades first.
        // This ensures identical shifts don't cause false positives with unrelated trades.
        for (long shift : shiftsToTry) {
            LocalDateTime adjustedTargetTime = targetTime.plusSeconds(shift);
            
            for (Trade sourceTrade : sourceAcc.getOpenTrades()) {
                if (matchedSourceTickets != null && matchedSourceTickets.contains(sourceTrade.getTicket())) continue;
                if (sourceTrade.getType() == null || !sourceTrade.getType().equals(targetTrade.getType())) continue;
                
                // Allow prefix/suffix differences in symbols (e.g. EURUSD vs EURUSD.m) by checking contains
                // Also normalize common aliases (GOLD=XAUUSD, SILVER=XAGUSD)
                String tSym = normalizeSymbol(targetTrade.getSymbol().toUpperCase());
                String sSym = normalizeSymbol(sourceTrade.getSymbol().toUpperCase());
                if (!tSym.contains(sSym) && !sSym.contains(tSym)) continue;

                LocalDateTime sourceTime;
                try {
                    sourceTime = LocalDateTime.parse(sourceTrade.getOpenTime(), MT_TIME_FORMAT);
                } catch (Exception e) {
                    continue;
                }

                long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(adjustedTargetTime, sourceTime));
                
                if (diffSeconds <= toleranceSeconds) {
                    if (cachedOffset == null || cachedOffset != shift) {
                        knownOffsets.put(pairKey, shift); // Cache the successful offset
                    }
                    if (matchedSourceTickets != null) {
                        matchedSourceTickets.add(sourceTrade.getTicket());
                    }
                    return sourceTrade; // Match found
                }
            }
        }

        return null;
    }

    /**
     * Normalize common symbol aliases to canonical names.
     * E.g. GOLD -> XAUUSD, SILVER -> XAGUSD
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null) return "";
        // Strip common suffixes like .m, .pro, .i etc.
        String clean = symbol.replaceAll("\\.[a-zA-Z]+$", "");
        switch (clean) {
            case "GOLD": return "XAUUSD";
            case "SILVER": return "XAGUSD";
            default: return clean;
        }
    }
}
