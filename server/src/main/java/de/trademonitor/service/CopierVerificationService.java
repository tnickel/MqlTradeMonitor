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

    // Metrics & Alarms
    private Long syncErrorStartTime = null;
    private boolean warningEmailSent = false;
    private String lastSyncStatus = "OK";
    private int lastCheckedTradeCount = 0;

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private CopierLinkRepository copierLinkRepository;

    @Autowired
    private GlobalConfigService globalConfigService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private HomeyService homeyService;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private de.trademonitor.service.AdminNotificationService adminNotificationService;

    private LocalDateTime lastRunTime = LocalDateTime.MIN;

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("lastCheckTime", lastRunTime == LocalDateTime.MIN ? 0L : lastRunTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        int intervalSeconds = globalConfigService.getCopierIntervalMins() * 60;
        metrics.put("nextCheckTime", (lastRunTime == LocalDateTime.MIN ? 0L : lastRunTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) + (intervalSeconds * 1000L));
        metrics.put("status", lastSyncStatus);
        metrics.put("checkedCount", lastCheckedTradeCount);
        metrics.put("interval", intervalSeconds);
        return metrics;
    }

    private boolean isWeekendForCopierAlerts() {
        boolean hasMonitoredReal = false;
        for (Account account : accountManager.getAllAccounts()) {
            if ("REAL".equalsIgnoreCase(account.getType()) && account.isMonitored()) {
                hasMonitoredReal = true;
                if (!accountManager.isWeekendForAccount(account)) {
                    return false;
                }
            }
        }
        return hasMonitoredReal;
    }

    @Scheduled(fixedDelay = 60000) // Check every minute
    public void verifyTrades() {
        int intervalMins = globalConfigService.getCopierIntervalMins();
        if (ChronoUnit.MINUTES.between(lastRunTime, LocalDateTime.now()) < intervalMins) {
            return;
        }

        LOG.info("Running scheduled Copier Trade Verification...");
        lastRunTime = LocalDateTime.now();

        // 1. Fetch all active copy links and collect unique targets
        List<CopierLinkEntity> links = copierLinkRepository.findAll();
        java.util.Set<Long> targetIdsToVerify = new java.util.HashSet<>();
        for (CopierLinkEntity link : links) {
            Account targetAcc = accountManager.getAccount(link.getTargetAccountId());
            Account sourceAcc = accountManager.getAccount(link.getSourceAccountId());
            if (targetAcc != null && ("CSV".equalsIgnoreCase(targetAcc.getType()) || !targetAcc.isMonitored())) {
                continue;
            }
            if (sourceAcc != null && ("CSV".equalsIgnoreCase(sourceAcc.getType()) || !sourceAcc.isMonitored())) {
                continue;
            }
            targetIdsToVerify.add(link.getTargetAccountId());
        }

        boolean globalErrorDetected = false;
        int totalCheckedTrades = 0;
        java.util.Map<Long, String> newCopierErrors = new java.util.HashMap<>();
        java.util.Map<Long, Integer> newWorstStages = new java.util.HashMap<>();

        for (Long targetId : targetIdsToVerify) {
            Account targetAcc = accountManager.getAccount(targetId);
            if (targetAcc != null && !targetAcc.isMonitored()) {
                newWorstStages.put(targetId, 0);
                continue;
            }

            de.trademonitor.dto.CopierVerificationReportDto report = generateReport(targetId);
            if (report == null || report.getTargetTrades() == null) continue;

            int worstStage = 0;
            boolean hasError = false;
            
            if (targetAcc != null && "REAL".equalsIgnoreCase(targetAcc.getType())) {
                totalCheckedTrades += targetAcc.getOpenTrades().size();
            }

            java.util.Map<Long, String> newStatuses = new java.util.HashMap<>();
            java.util.List<String> errorMessages = new java.util.ArrayList<>();
            
            for (de.trademonitor.dto.TargetTradeMatchDto match : report.getTargetTrades()) {
                Trade targetTrade = match.getTargetTrade();
                
                if (match.getIsMatched()) {
                    newStatuses.put(targetTrade.getTicket(), "MATCHED");
                    int stage = 1;
                    if (match.getIsStage3Match()) stage = 3;
                    else if (match.getIsStage2Match()) stage = 2;
                    if (stage > worstStage) worstStage = stage;
                } else if (match.getIsExempt()) {
                    newStatuses.put(targetTrade.getTicket(), "EXEMPTED");
                } else {
                    newStatuses.put(targetTrade.getTicket(), "WARNING");
                    String typeStr = targetTrade.getType() != null ? targetTrade.getType().toUpperCase() : "";
                    String readableType = (typeStr.equals("0") || typeStr.equals("BUY")) ? "BUY" : "SELL";
                    String errorMsg = String.format("Trade %s (%s) hat keinen entsprechenden Source-Trade",
                            targetTrade.getSymbol(), readableType);
                    LOG.warning("Copier Error on Account " + targetId + ": " + errorMsg);
                    errorMessages.add(errorMsg);
                    hasError = true;
                    
                    if (targetAcc != null && "REAL".equalsIgnoreCase(targetAcc.getType())) {
                        globalErrorDetected = true;
                    }
                }
            }

            if (targetAcc != null) {
                targetAcc.updateSyncStatuses(newStatuses);
            }

            if (hasError) {
                newCopierErrors.put(targetId, String.join("; ", errorMessages));
            } else {
                newWorstStages.put(targetId, worstStage);
            }
        }

        for (Account account : accountManager.getAllAccounts()) {
            long accountId = account.getAccountId();
            String errorMsg = newCopierErrors.get(accountId);
            if (errorMsg != null) {
                accountManager.updateCopierError(accountId, true, errorMsg);
            } else if (Boolean.TRUE.equals(account.getCopierError())) {
                accountManager.updateCopierError(accountId, false, null);
            }
            if (!newCopierErrors.containsKey(accountId) && newWorstStages.containsKey(accountId)) {
                accountManager.updateCopierWorstStage(accountId, newWorstStages.get(accountId));
            }
        }

        lastCheckedTradeCount = totalCheckedTrades;

        // --- Global Alerting Logic ---
        if (globalErrorDetected) {
            lastSyncStatus = "WARNING";
            if (syncErrorStartTime == null) {
                syncErrorStartTime = System.currentTimeMillis();
            }

            long delayMs = globalConfigService.getSyncAlarmDelayMins() * 60L * 1000L;

            if (System.currentTimeMillis() - syncErrorStartTime >= delayMs) {
                if (!warningEmailSent && !isWeekendForCopierAlerts()) {
                    emailService.sendSyncWarningEmail("Trade Monitor Warnung",
                            "Achtung: Die Copier-Map meldet Sync-Fehler (Unmatched Trades) auf Real-Konten! Bitte Dashboard prüfen.");
                    telegramService.sendNotification("⚠️ *Trade Monitor Warnung:*\n\n"
                            + "Achtung: Die Copier-Map meldet Sync-Fehler (Unmatched Trades) auf Real-Konten! Bitte Dashboard prüfen.");
                    adminNotificationService.addNotification(new de.trademonitor.dto.AdminNotification(
                            de.trademonitor.dto.AdminNotification.Category.HEALTH,
                            de.trademonitor.dto.AdminNotification.Severity.CRITICAL,
                            "🔄 Copier-Sync Fehler",
                            "Die Copier-Map meldet Sync-Fehler (Unmatched Trades) auf Real-Konten!"
                    ));
                    warningEmailSent = true;
                }

                // Activate Homey Siren alarm state if enabled
                if (globalConfigService.isHomeyTriggerSync()) {
                    if (isWeekendForCopierAlerts()) {
                        homeyService.setAlarmState("COPIER_SYNC", false);
                    } else {
                        homeyService.setAlarmState("COPIER_SYNC", true);
                    }
                }
            }
        } else {
            if ("WARNING".equals(lastSyncStatus)) {
                adminNotificationService.addNotification(new de.trademonitor.dto.AdminNotification(
                        de.trademonitor.dto.AdminNotification.Category.HEALTH,
                        de.trademonitor.dto.AdminNotification.Severity.WARNING,
                        "🔄 Copier-Sync wieder OK",
                        "Alle Kopier-Verbindungen sind wieder synchron."
                ));
            }
            lastSyncStatus = "OK";
            syncErrorStartTime = null;
            warningEmailSent = false;
            homeyService.setAlarmState("COPIER_SYNC", false);
        }
    }

    public de.trademonitor.dto.CopierVerificationReportDto generateReport(long targetAccountId) {
        Account target = accountManager.getAccount(targetAccountId);
        if (target == null || "CSV".equalsIgnoreCase(target.getType()) || !target.isMonitored()) return null;

        de.trademonitor.dto.CopierVerificationReportDto report = new de.trademonitor.dto.CopierVerificationReportDto();
        report.setTargetAccountId(targetAccountId);
        report.setTargetAccountName(target.getName() != null ? target.getName() : String.valueOf(targetAccountId));
        report.setTargetGmtOffsetSeconds(target.getServerTimeOffsetSeconds() != null ? target.getServerTimeOffsetSeconds() : 0L);
        
        List<CopierLinkEntity> incomingLinks = copierLinkRepository.findByTargetAccountId(targetAccountId).stream()
                .filter(link -> {
                    Account src = accountManager.getAccount(link.getSourceAccountId());
                    return src != null && !"CSV".equalsIgnoreCase(src.getType()) && src.isMonitored();
                })
                .collect(java.util.stream.Collectors.toList());
        List<CopierLinkEntity> outgoingLinks = copierLinkRepository.findBySourceAccountId(targetAccountId).stream()
                .filter(link -> {
                    Account tgt = accountManager.getAccount(link.getTargetAccountId());
                    return tgt != null && !"CSV".equalsIgnoreCase(tgt.getType()) && tgt.isMonitored();
                })
                .collect(java.util.stream.Collectors.toList());
        
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

        // First pass: Initialize all matches
        for (Trade targetTrade : target.getOpenTrades()) {
            String typeStr = targetTrade.getType() != null ? targetTrade.getType().toUpperCase() : "";
            if (!typeStr.equals("0") && !typeStr.equals("1") && !typeStr.equals("BUY") && !typeStr.equals("SELL")) {
                continue; 
            }

            de.trademonitor.dto.TargetTradeMatchDto matchDto = new de.trademonitor.dto.TargetTradeMatchDto();
            matchDto.setTargetTrade(targetTrade);
            matchDto.setIsMatched(false);
            matchDto.setIsStage2Match(false);
            matchDto.setIsStage3Match(false);

            // Check if this trade's magic number is in the exempt list
            if (exemptMagics.contains(targetTrade.getMagicNumber())) {
                matchDto.setIsExempt(true);
            }
            targetMatches.add(matchDto);
        }

        boolean useStage1 = globalConfigService.isCopierUseStage1();
        boolean useStage2 = globalConfigService.isCopierUseStage2();
        double stage2Tol = globalConfigService.getCopierStage2Tolerance();
        boolean useStage3 = globalConfigService.isCopierUseStage3();

        // PHASE 1: Try exact time match for ALL trades first
        if (useStage1) {
            for (de.trademonitor.dto.TargetTradeMatchDto matchDto : targetMatches) {
                if (matchDto.getIsExempt() || matchDto.getIsMatched()) continue;
                Trade targetTrade = matchDto.getTargetTrade();
                for (Account source : sources) {
                    Trade matchedSourceTrade = findStage1Match(targetTrade, target, source, toleranceSeconds, matchedSourceTickets);
                    if (matchedSourceTrade != null) {
                        matchDto.setIsMatched(true);
                        matchDto.setIsStage2Match(false);
                        matchDto.setIsStage3Match(false);
                        matchDto.setMatchedBySourceName(source.getName() != null ? source.getName() : String.valueOf(source.getAccountId()));
                        matchDto.setMatchedBySourceTicket(matchedSourceTrade.getTicket());
                        break;
                    }
                }
            }
        }

        // PHASE 2: Fallback exact data match for remaining unmatched trades
        if (useStage2) {
            for (de.trademonitor.dto.TargetTradeMatchDto matchDto : targetMatches) {
                if (matchDto.getIsExempt() || matchDto.getIsMatched()) continue;
                Trade targetTrade = matchDto.getTargetTrade();
                for (Account source : sources) {
                    Trade matchedSourceTrade = findStage2Match(targetTrade, source, stage2Tol, matchedSourceTickets);
                    if (matchedSourceTrade != null) {
                        matchDto.setIsMatched(true);
                        matchDto.setIsStage2Match(true);
                        matchDto.setIsStage3Match(false);
                        matchDto.setMatchedBySourceName(source.getName() != null ? source.getName() : String.valueOf(source.getAccountId()));
                        matchDto.setMatchedBySourceTicket(matchedSourceTrade.getTicket());
                        break;
                    }
                }
            }
        }

        // PHASE 3: Letzter Fallback (Nur Symbol & Richtung) for remaining unmatched trades
        if (useStage3) {
            for (de.trademonitor.dto.TargetTradeMatchDto matchDto : targetMatches) {
                if (matchDto.getIsExempt() || matchDto.getIsMatched()) continue;
                Trade targetTrade = matchDto.getTargetTrade();
                for (Account source : sources) {
                    Trade matchedSourceTrade = findStage3Match(targetTrade, source, matchedSourceTickets);
                    if (matchedSourceTrade != null) {
                        matchDto.setIsMatched(true);
                        matchDto.setIsStage2Match(false);
                        matchDto.setIsStage3Match(true);
                        matchDto.setMatchedBySourceName(source.getName() != null ? source.getName() : String.valueOf(source.getAccountId()));
                        matchDto.setMatchedBySourceTicket(matchedSourceTrade.getTicket());
                        break;
                    }
                }
            }
        }

        report.setTargetTrades(targetMatches);
        return report;
    }

    private Trade findStage1Match(Trade targetTrade, Account targetAcc, Account sourceAcc, int toleranceSeconds, java.util.Set<Long> matchedSourceTickets) {
        LocalDateTime targetTime;
        try {
            targetTime = LocalDateTime.parse(targetTrade.getOpenTime(), MT_TIME_FORMAT);
        } catch (Exception e) {
            LOG.warning("Unparseable open time for target trade " + targetTrade.getTicket() + ": " + targetTrade.getOpenTime());
            return null;
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
                if (!typesMatch(sourceTrade.getType(), targetTrade.getType())) continue;
                if (targetTrade.getSymbol() == null || sourceTrade.getSymbol() == null) continue;
                
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

    private Trade findStage2Match(Trade targetTrade, Account sourceAcc, double tol, java.util.Set<Long> matchedSourceTickets) {
        if (targetTrade.getSymbol() == null) return null;
        for (Trade sourceTrade : sourceAcc.getOpenTrades()) {
            if (matchedSourceTickets != null && matchedSourceTickets.contains(sourceTrade.getTicket())) continue;
            if (!typesMatch(sourceTrade.getType(), targetTrade.getType())) continue;
            if (sourceTrade.getSymbol() == null) continue;

            String tSym = normalizeSymbol(targetTrade.getSymbol().toUpperCase());
            String sSym = normalizeSymbol(sourceTrade.getSymbol().toUpperCase());
            if (!tSym.contains(sSym) && !sSym.contains(tSym)) continue;

            // Stage 2 specific checks: SL and TP comparison with tolerance
            if (Math.abs(targetTrade.getStopLoss() - sourceTrade.getStopLoss()) > tol) continue;
            if (Math.abs(targetTrade.getTakeProfit() - sourceTrade.getTakeProfit()) > tol) continue;

            // Match found! (Stage 2 relies purely on Symbol, Type, SL and TP, ignoring time)

            // Match found!
            if (matchedSourceTickets != null) {
                matchedSourceTickets.add(sourceTrade.getTicket());
            }
            return sourceTrade;
        }

        return null;
    }

    private Trade findStage3Match(Trade targetTrade, Account sourceAcc, java.util.Set<Long> matchedSourceTickets) {
        if (targetTrade.getSymbol() == null) return null;
        for (Trade sourceTrade : sourceAcc.getOpenTrades()) {
            if (matchedSourceTickets != null && matchedSourceTickets.contains(sourceTrade.getTicket())) continue;
            if (!typesMatch(sourceTrade.getType(), targetTrade.getType())) continue;
            if (sourceTrade.getSymbol() == null) continue;

            String tSym = normalizeSymbol(targetTrade.getSymbol().toUpperCase());
            String sSym = normalizeSymbol(sourceTrade.getSymbol().toUpperCase());
            if (!tSym.contains(sSym) && !sSym.contains(tSym)) continue;

            // Match found! (Stage 3 relies purely on Symbol and Type)
            if (matchedSourceTickets != null) {
                matchedSourceTickets.add(sourceTrade.getTicket());
            }
            return sourceTrade;
        }

        return null;
    }

    private String normalizeTradeType(String type) {
        if (type == null) return null;
        String t = type.trim().toUpperCase();
        if ("0".equals(t) || "BUY".equals(t)) return "BUY";
        if ("1".equals(t) || "SELL".equals(t)) return "SELL";
        return t;
    }

    private boolean typesMatch(String typeA, String typeB) {
        String a = normalizeTradeType(typeA);
        String b = normalizeTradeType(typeB);
        return a != null && a.equals(b);
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
