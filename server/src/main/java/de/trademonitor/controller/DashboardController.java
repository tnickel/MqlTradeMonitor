package de.trademonitor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.trademonitor.model.Account;
import de.trademonitor.model.ClosedTrade;
import de.trademonitor.repository.ClosedTradeRepository;
import de.trademonitor.service.AccountManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import de.trademonitor.security.CustomUserDetails;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller for web dashboard views.
 */
@Controller
public class DashboardController {

    private static final Logger LOG = Logger.getLogger(DashboardController.class.getName());

    private boolean isAllowedAccess(CustomUserDetails userDetails, long accountId) {
        if (userDetails == null)
            return false;
        if ("ROLE_ADMIN".equals(userDetails.getUserEntity().getRole()))
            return true;
        return userDetails.getUserEntity().getAllowedAccountIds().contains(accountId);
    }

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private ClosedTradeRepository closedTradeRepository;

    @Autowired
    private de.trademonitor.service.GlobalConfigService globalConfigService;

    @Autowired
    private de.trademonitor.service.CopierVerificationService copierVerificationService;

    @Autowired
    private de.trademonitor.service.MagicMappingService magicMappingService;

    @Autowired
    private de.trademonitor.service.HomeyService homeyService;

    @Autowired
    private de.trademonitor.service.TradeStorage tradeStorage;

    @Autowired
    private de.trademonitor.service.TradeComparisonService tradeComparisonService;

    @Autowired
    private de.trademonitor.service.SecurityAuditService securityAuditService;

    @Autowired
    private de.trademonitor.service.ServerHealthMonitorService serverHealthMonitorService;

    @Autowired
    private de.trademonitor.service.NetworkStatusService networkStatusService;

    @Autowired
    private de.trademonitor.repository.EaLogEntryRepository eaLogEntryRepository;

    @Autowired
    private de.trademonitor.service.StrategyAnalyticsService strategyAnalyticsService;

    @Autowired
    private de.trademonitor.repository.TimelineRepository timelineRepository;

    @Autowired
    private de.trademonitor.repository.LoginLogRepository loginLogRepository;

    @ModelAttribute
    public void addCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUserEntity());
        }
    }

    @PostMapping("/api/test-siren")
    @ResponseBody
    public String testSiren() {
        homeyService.triggerSiren();
        return "Siren trigger sent";
    }

    /**
     * Main dashboard showing all accounts.
     */
    @GetMapping("/mobile/drawdown")
    public String mobileDrawdown(Model model) {
        List<de.trademonitor.dto.MagicDrawdownItem> list = new ArrayList<>(accountManager.getMagicDrawdowns());

        // Sort: Real first, then Drawdown desc
        list.sort((a, b) -> {
            if (a.isReal() && !b.isReal())
                return -1;
            if (!a.isReal() && b.isReal())
                return 1;
            return Double.compare(b.getCurrentDrawdownEur(), a.getCurrentDrawdownEur());
        });

        model.addAttribute("drawdowns", list);
        return "mobile-drawdown";
    }

    @GetMapping("/")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, 
                           @RequestAttribute(value = "isMobile", required = false) Boolean isMobile,
                           Model model) {
        long t0 = System.currentTimeMillis();
        List<java.util.Map<String, Object>> allAccounts = accountManager.getAccountsWithStatus();
        long t1 = System.currentTimeMillis();

        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        model.addAttribute("isAdmin", isAdmin);

        // Filter by user permissions
        if (userDetails != null && !isAdmin) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            allAccounts = allAccounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && allowed.contains(id);
                    })
                    .collect(Collectors.toList());
        }

        List<de.trademonitor.entity.DashboardSectionEntity> sections = accountManager.getAllSections();

        // Organize accounts by sectionId
        Map<Long, List<java.util.Map<String, Object>>> accountsBySection = new LinkedHashMap<>();

        // Initialize map with empty lists for all sections to ensure they exist
        for (de.trademonitor.entity.DashboardSectionEntity sec : sections) {
            accountsBySection.put(sec.getId(), new ArrayList<>());
        }

        int totalTradesCount = 0;
        java.time.LocalDateTime logCutoff = java.time.LocalDateTime.now().minusHours(24);
        long t2 = System.currentTimeMillis();

        // BATCH: Load EA log severities for ALL accounts in ONE query instead of N queries
        Map<Long, Integer> severityMap = new HashMap<>();
        try {
            List<Object[]> severityRows = eaLogEntryRepository.getLogSeverityForAllAccountsSince(logCutoff);
            for (Object[] row : severityRows) {
                Long accIdRow = ((Number) row[0]).longValue();
                Integer sev = ((Number) row[1]).intValue();
                severityMap.put(accIdRow, sev);
            }
        } catch (Exception e) {
            // Fallback: leave empty map, all severities will be 0
        }

        for (java.util.Map<String, Object> acc : allAccounts) {
            Long accId = (Long) acc.get("accountId");
            if (accId != null) {
                // Use batch result for severity
                Integer severity = severityMap.getOrDefault(accId, 0);
                acc.put("eaLogSeverity", severity);
            }

            Long sectionId = (Long) acc.get("sectionId");
            // Fallback if null (should handled by migration, but safety check)
            if (sectionId == null && !sections.isEmpty()) {
                sectionId = sections.get(0).getId();
            }

            if (sectionId != null && accountsBySection.containsKey(sectionId)) {
                accountsBySection.get(sectionId).add(acc);
            } else if (!sections.isEmpty()) {
                // Fallback to first section
                accountsBySection.get(sections.get(0).getId()).add(acc);
            }

            // Sum up trades for mobile overview
            Integer tradeCount = (Integer) acc.get("trades");
            if (tradeCount != null) {
                totalTradesCount += tradeCount;
            }
        }

        // Sort by displayOrder within each section
        Comparator<java.util.Map<String, Object>> orderComparator = (a, b) -> {
            Integer orderA = (Integer) a.get("displayOrder");
            Integer orderB = (Integer) b.get("displayOrder");
            return Integer.compare(orderA != null ? orderA : 0, orderB != null ? orderB : 0);
        };

        for (List<java.util.Map<String, Object>> list : accountsBySection.values()) {
            list.sort(orderComparator);
        }

        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        java.time.YearMonth prevMonth = currentMonth.minusMonths(1);
        java.time.YearMonth prevPrevMonth = prevMonth.minusMonths(1);
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.GERMAN);
        model.addAttribute("month1Name", prevPrevMonth.format(formatter));
        model.addAttribute("month2Name", prevMonth.format(formatter));
        model.addAttribute("month3Name", currentMonth.format(formatter));

        model.addAttribute("sections", sections);
        model.addAttribute("accountsBySection", accountsBySection);

        // Keep "accounts" for backward compatibility if needed for totals
        model.addAttribute("accounts", allAccounts);
        model.addAttribute("totalTrades", totalTradesCount);

        // Collect accounts with triggered open profit alarms for banner
        List<java.util.Map<String, Object>> alarmedAccounts = allAccounts.stream()
                .filter(acc -> Boolean.TRUE.equals(acc.get("openProfitAlarmTriggered")))
                .collect(Collectors.toList());
        model.addAttribute("alarmedAccounts", alarmedAccounts);

        model.addAttribute("syncMetrics", copierVerificationService.getMetrics());

        // Global Export Stats
        List<Long> allowedAccountIds = allAccounts.stream()
                .map(acc -> (Long) acc.get("accountId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!allowedAccountIds.isEmpty()) {
            model.addAttribute("globalTradeCount", closedTradeRepository.countByAccountIds(allowedAccountIds));
            model.addAttribute("globalTradeStart", closedTradeRepository.findMinCloseTimeByAccountIds(allowedAccountIds));
            model.addAttribute("globalTradeEnd", closedTradeRepository.findMaxCloseTimeByAccountIds(allowedAccountIds));
        } else {
            model.addAttribute("globalTradeCount", 0);
        }

        model.addAttribute("timeoutSeconds", accountManager.getTimeoutSeconds());

        // Live Indicator Config
        model.addAttribute("liveGreenMins", globalConfigService.getLiveGreenMins());
        model.addAttribute("liveYellowMins", globalConfigService.getLiveYellowMins());
        model.addAttribute("liveOrangeMins", globalConfigService.getLiveOrangeMins());
        model.addAttribute("liveColorGreen", globalConfigService.getLiveColorGreen());
        model.addAttribute("liveColorYellow", globalConfigService.getLiveColorYellow());
        model.addAttribute("liveColorOrange", globalConfigService.getLiveColorOrange());
        model.addAttribute("liveColorRed", globalConfigService.getLiveColorRed());

        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUserEntity());
        }

        if (Boolean.TRUE.equals(isMobile)) {
            long tEnd = System.currentTimeMillis();
            LOG.info("[PERF] dashboard(mobile): total=" + (tEnd - t0) + "ms | getAccountsWithStatus=" + (t1 - t0) + "ms | eaLogBatch=" + (System.currentTimeMillis() - t2) + "ms");
            return "mobile-dashboard";
        }
        long tEnd = System.currentTimeMillis();
        LOG.info("[PERF] dashboard: total=" + (tEnd - t0) + "ms | getAccountsWithStatus=" + (t1 - t0) + "ms | eaLogBatch=" + (tEnd - t2) + "ms");
        return "dashboard";
    }

    @GetMapping("/open-trades")
    public String openTradesPage(@AuthenticationPrincipal CustomUserDetails userDetails, 
                                @RequestAttribute(value = "isMobile", required = false) Boolean isMobile,
                                Model model) {
        List<de.trademonitor.model.Account> sortedAccounts = accountManager.getAccountsSortedByPrivilege();

        // Filter by user permissions
        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            sortedAccounts = sortedAccounts.stream()
                    .filter(acc -> allowed.contains(acc.getAccountId()))
                    .collect(Collectors.toList());
        }

        model.addAttribute("accounts", sortedAccounts);

        // Group trades by Magic Number for each account
        Map<Long, Map<Long, List<de.trademonitor.model.Trade>>> accountGroupedTrades = new LinkedHashMap<>();
        Map<Long, Map<Long, Map<String, Object>>> accountGroupSummaries = new LinkedHashMap<>();
        Map<Long, String> magicMappings = magicMappingService.getAllMappings();

        for (de.trademonitor.model.Account acc : sortedAccounts) {
            Map<Long, List<de.trademonitor.model.Trade>> grouped = acc.getOpenTrades().stream()
                    .collect(Collectors.groupingBy(de.trademonitor.model.Trade::getMagicNumber, LinkedHashMap::new, Collectors.toList()));
            accountGroupedTrades.put(acc.getAccountId(), grouped);

            Map<Long, Map<String, Object>> summaries = new LinkedHashMap<>();
            for (Map.Entry<Long, List<de.trademonitor.model.Trade>> entry : grouped.entrySet()) {
                Long magic = entry.getKey();
                List<de.trademonitor.model.Trade> trades = entry.getValue();
                
                double sumLots = trades.stream().mapToDouble(de.trademonitor.model.Trade::getVolume).sum();
                double sumProfit = trades.stream().mapToDouble(t -> t.getProfit() + t.getSwap()).sum();
                double sumDrawdown = trades.stream()
                    .mapToDouble(t -> (t.getMaxDrawdown() != null ? t.getMaxDrawdown() : 0.0))
                    .sum();
                
                Map<String, Object> groupSummary = new HashMap<>();
                groupSummary.put("magic", magic);
                groupSummary.put("name", magicMappings.getOrDefault(magic, "Magic " + magic));
                groupSummary.put("sumLots", sumLots);
                groupSummary.put("sumProfit", sumProfit);
                groupSummary.put("sumDrawdown", sumDrawdown);
                groupSummary.put("sumDrawdownPct", acc.getBalance() > 0 ? (sumDrawdown / acc.getBalance()) * 100.0 : 0.0);
                
                summaries.put(magic, groupSummary);
            }
            accountGroupSummaries.put(acc.getAccountId(), summaries);
        }

        model.addAttribute("accountGroupedTrades", accountGroupedTrades);
        model.addAttribute("accountGroupSummaries", accountGroupSummaries);

        // Calculate Totals
        int totalTradesCount = 0;
        double totalEquity = 0;
        double totalProfit = 0;
        String currency = "EUR"; // Default fallback

        for (de.trademonitor.model.Account acc : sortedAccounts) {
            totalTradesCount += acc.getOpenTrades().size();
            // Only sum online accounts or all? Usually all is safer for "current state"
            // But if account is offline, equity might be stale. Decision: Show all.
            totalEquity += acc.getEquity();
            totalProfit += acc.getTotalProfit();
            if (acc.getCurrency() != null && !acc.getCurrency().isEmpty()) {
                currency = acc.getCurrency();
            }
        }

        model.addAttribute("totalTrades", totalTradesCount);
        model.addAttribute("totalEquity", totalEquity);
        model.addAttribute("totalProfit", totalProfit);
        model.addAttribute("currency", currency);

        model.addAttribute("magicMappings", magicMappings);
        model.addAttribute("timeoutSeconds", accountManager.getTimeoutSeconds());

        // Live Indicator Config
        model.addAttribute("liveGreenMins", globalConfigService.getLiveGreenMins());
        model.addAttribute("liveYellowMins", globalConfigService.getLiveYellowMins());
        model.addAttribute("liveOrangeMins", globalConfigService.getLiveOrangeMins());
        model.addAttribute("liveColorGreen", globalConfigService.getLiveColorGreen());
        model.addAttribute("liveColorYellow", globalConfigService.getLiveColorYellow());
        model.addAttribute("liveColorOrange", globalConfigService.getLiveColorOrange());
        model.addAttribute("liveColorRed", globalConfigService.getLiveColorRed());

        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUserEntity());
        }

        if (Boolean.TRUE.equals(isMobile)) {
            return "mobile-open-trades";
        }
        return "open-trades";
    }

    /**
     * Update magic mapping.
     */
    @PostMapping("/admin/mapping")
    public String updateMapping(
            @RequestParam("magicNumber") Long magicNumber,
            @RequestParam("customComment") String customComment) {
        magicMappingService.saveMapping(magicNumber, customComment);
        return "redirect:/admin";
    }

    /**
     * AJAX Endpoint to update magic mapping.
     */
    @PostMapping("/api/mapping")
    @ResponseBody
    public ResponseEntity<String> updateMappingAjax(
            @RequestParam("magicNumber") Long magicNumber,
            @RequestParam("customComment") String customComment) {
        magicMappingService.saveMapping(magicNumber, customComment);
        return ResponseEntity.ok("Saved");
    }

    /**
     * AJAX Endpoint to update account details (name, type, alarm config).
     */
    @PostMapping("/api/account/update")
    @ResponseBody
    public ResponseEntity<String> updateAccountDetails(
            @RequestParam("accountId") Long accountId,
            @RequestParam("name") String name,
            @RequestParam("type") String type,
            @RequestParam(value = "alarmEnabled", defaultValue = "false") boolean alarmEnabled,
            @RequestParam(value = "alarmAbs", required = false) Double alarmAbs,
            @RequestParam(value = "alarmPct", required = false) Double alarmPct) {
        accountManager.updateAccountDetails(accountId, name, type, alarmEnabled, alarmAbs, alarmPct);
        return ResponseEntity.ok("Saved");
    }

    @PostMapping("/api/account/meta-trader-info")
    @ResponseBody
    public ResponseEntity<String> updateMetaTraderInfo(
            @RequestParam("accountId") Long accountId,
            @RequestParam("metaTraderInfo") String metaTraderInfo) {
        accountManager.updateMetaTraderInfo(accountId, metaTraderInfo);
        return ResponseEntity.ok("Saved");
    }

    @PostMapping("/api/account/timelines")
    @ResponseBody
    public ResponseEntity<String> addTimeline(
            @RequestParam("accountId") Long accountId,
            @RequestParam("timelineDate") String timelineDate) {
        
        de.trademonitor.entity.TimelineEntity timeline = new de.trademonitor.entity.TimelineEntity(accountId, timelineDate);
        timelineRepository.save(timeline);
        
        Account account = accountManager.getAccount(accountId);
        if (account != null) {
            String currentInfo = account.getMetaTraderInfo();
            if (currentInfo == null) {
                currentInfo = "";
            } else if (!currentInfo.trim().isEmpty()) {
                currentInfo += "\n";
            }
            currentInfo += "[Timeline hinzugefügt: " + timelineDate + "]";
            accountManager.updateMetaTraderInfo(accountId, currentInfo);
        }
        
        return ResponseEntity.ok("Saved");
    }

    @DeleteMapping("/api/account/timelines/{id}")
    @ResponseBody
    public ResponseEntity<String> deleteTimeline(@PathVariable("id") Long id) {
        timelineRepository.deleteById(id);
        return ResponseEntity.ok("Deleted");
    }


    /**
     * AJAX Endpoint to update magic number max age for a specific account.
     */
    @PostMapping("/api/account/magic-max-age")
    @ResponseBody
    public ResponseEntity<String> updateMagicMaxAge(
            @RequestParam("accountId") Long accountId,
            @RequestParam("days") int days) {
        accountManager.updateMagicNumberMaxAge(accountId, days);
        return ResponseEntity.ok("Saved");
    }

    /**
     * AJAX Endpoint to update magic min trades for a specific account.
     */
    @PostMapping("/api/account/magic-min-trades")
    @ResponseBody
    public ResponseEntity<String> updateMagicMinTrades(
            @RequestParam("accountId") Long accountId,
            @RequestParam("minTrades") int minTrades) {
        accountManager.updateMagicMinTrades(accountId, minTrades);
        return ResponseEntity.ok("Saved");
    }

    /**
     * AJAX Endpoint to update magic preferences (max age and min trades) for a
     * specific account.
     */
    @PostMapping("/api/account/{accountId}/magic-preferences")
    @ResponseBody
    public ResponseEntity<String> updateMagicPreferences(
            @PathVariable("accountId") Long accountId,
            @RequestParam("magicNumberMaxAge") int magicNumberMaxAge,
            @RequestParam("magicMinTrades") int magicMinTrades) {
        accountManager.updateMagicNumberMaxAge(accountId, magicNumberMaxAge);
        accountManager.updateMagicMinTrades(accountId, magicMinTrades);
        return ResponseEntity.ok("Saved");
    }

    /**
     * AJAX Endpoint to reset all trade data for a specific account.
     * Deletes open trades, closed trades, and equity snapshots.
     * The account entity itself is preserved.
     */
    @PostMapping("/api/account/reset-trades")
    @ResponseBody
    public ResponseEntity<String> resetAccountTrades(
            @RequestParam("accountId") Long accountId) {
        accountManager.resetAccountTrades(accountId);
        return ResponseEntity.ok("Reset complete");
    }

    // --- Section Management API ---

    @PostMapping("/api/section/create")
    @ResponseBody
    public de.trademonitor.entity.DashboardSectionEntity createSection(@RequestParam("name") String name) {
        return accountManager.createSection(name);
    }

    @PostMapping("/api/section/rename")
    @ResponseBody
    public ResponseEntity<String> renameSection(
            @RequestParam("id") Long id,
            @RequestParam("name") String name) {
        accountManager.renameSection(id, name);
        return ResponseEntity.ok("Renamed");
    }

    @PostMapping("/api/section/delete")
    @ResponseBody
    public ResponseEntity<String> deleteSection(@RequestParam("id") Long id) {
        accountManager.deleteSection(id);
        return ResponseEntity.ok("Deleted");
    }

    /**
     * AJAX Endpoint to save dashboard layout.
     * Expects JSON: { "sectionId1": [accId1, accId2], "sectionId2": [accId3] }
     */
    @PostMapping("/api/account/layout")
    @ResponseBody
    public ResponseEntity<String> saveLayout(@RequestBody Map<String, List<Object>> layoutData) {
        LOG.info("=== SAVE LAYOUT called with " + layoutData.size() + " sections ===");
        int totalSaved = 0;
        for (Map.Entry<String, List<Object>> entry : layoutData.entrySet()) {
            try {
                Long sectionId = Long.parseLong(entry.getKey());
                List<Object> rawIds = entry.getValue();
                System.out
                        .println("  Section " + sectionId + ": " + (rawIds != null ? rawIds.size() : 0) + " accounts");

                if (rawIds != null) {
                    for (int i = 0; i < rawIds.size(); i++) {
                        Object idObj = rawIds.get(i);
                        Long accountId = null;
                        if (idObj instanceof Number) {
                            accountId = ((Number) idObj).longValue();
                        } else if (idObj instanceof String) {
                            try {
                                accountId = Long.parseLong((String) idObj);
                            } catch (NumberFormatException nfe) {
                                LOG.info("    WARN: Could not parse accountId: " + idObj);
                            }
                        }

                        if (accountId != null) {
                            LOG.info(
                                    "    Saving account " + accountId + " -> section=" + sectionId + ", order=" + i);
                            accountManager.saveAccountSection(accountId, sectionId, i);
                            totalSaved++;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                LOG.info("  WARN: Could not parse sectionId key: " + entry.getKey());
            }
        }
        LOG.info("=== SAVE LAYOUT complete: " + totalSaved + " accounts saved ===");
        return ResponseEntity.ok("Layout saved");
    }

    /**
     * AJAX Endpoint to get all open trades.
     */
    @org.springframework.web.bind.annotation.GetMapping("/api/trades/open")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.List<java.util.Map<String, Object>> getOpenTrades(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Map<String, Object>> trades = accountManager.getAllOpenTradesSorted();
        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            return trades.stream()
                    .filter(t -> t.get("accountId") != null
                            && allowed.contains(((Number) t.get("accountId")).longValue()))
                    .collect(Collectors.toList());
        }
        return trades;
    }

    /**
     * AJAX Endpoint to get magic number drawdowns.
     */
    @org.springframework.web.bind.annotation.GetMapping("/api/stats/magic-drawdowns")
    @org.springframework.web.bind.annotation.ResponseBody
    public List<de.trademonitor.dto.MagicDrawdownItem> getMagicDrawdowns(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<de.trademonitor.dto.MagicDrawdownItem> drawdowns = accountManager.getMagicDrawdowns();
        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            return drawdowns.stream()
                    .filter(d -> allowed.contains(d.getAccountId()))
                    .collect(Collectors.toList());
        }
        return drawdowns;
    }

    /**
     * AJAX Endpoint to get equity snapshots for the equity curve chart.
     * Returns list of {timestamp, equity, balance} objects ordered by time.
     */
    @GetMapping("/api/equity-history/{accountId}")
    @ResponseBody
    public ResponseEntity<?> getEquityHistory(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body("Access Denied");
        }
        List<de.trademonitor.entity.EquitySnapshotEntity> snapshots = tradeStorage.loadEquitySnapshots(accountId);
        List<Map<String, Object>> result = new ArrayList<>();
        
        // Downsampling if there are too many snapshots (>1500)
        int step = 1;
        if (snapshots.size() > 1500) {
            step = snapshots.size() / 1500;
        }
        
        for (int i = 0; i < snapshots.size(); i++) {
            // Always include first, last, and every step-th item
            if (i == 0 || i == snapshots.size() - 1 || i % step == 0) {
                de.trademonitor.entity.EquitySnapshotEntity snap = snapshots.get(i);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("timestamp", snap.getTimestamp());
                entry.put("equity", snap.getEquity());
                entry.put("balance", snap.getBalance());
                result.add(entry);
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * AJAX Endpoint to fetch all closed trades as compact JSON for the dashboard.
     */
    @GetMapping("/api/account/{accountId}/closed-trades")
    @ResponseBody
    public ResponseEntity<?> getClosedTrades(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body("Access Denied");
        }
        Account account = accountManager.getAccount(accountId);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        // Use the in-memory cache from Account to avoid DB load overhead where possible
        // The frontend JS will handle sorting and filtering
        return ResponseEntity.ok(account.getClosedTrades());
    }

    /**
     * Update global configuration.
     */
    @org.springframework.web.bind.annotation.PostMapping("/admin/config")
    public String updateConfig(
            @org.springframework.web.bind.annotation.RequestParam("tradeSyncInterval") int tradeSyncInterval) {
        globalConfigService.setTradeSyncIntervalSeconds(tradeSyncInterval);
        return "redirect:/admin";
    }

    /**
     * Detail view for a specific account.
     */
    @GetMapping("/account/{accountId}")
    public String accountDetail(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable long accountId,
            Model model) {
        long t0 = System.currentTimeMillis();
        if (!isAllowedAccess(userDetails, accountId)) {
            return "redirect:/";
        }
        Account account = accountManager.getAccount(accountId);
        if (account == null) {
            return "redirect:/";
        }
        // Apply broker-specific commission correction factor
        account.setCommissionFactor(globalConfigService.getBrokerCommissionFactor(account.getBroker()));
        model.addAttribute("account", account);
        model.addAttribute("online", account.isOnline(accountManager.getTimeoutSeconds()));

        int maxAge = account.getMagicNumberMaxAge();
        int minTrades = account.getMagicMinTrades();
        Map<Long, String> mappings = magicMappingService.getAllMappings();
        // Group trades by Magic Number for this account
        Map<Long, List<de.trademonitor.model.Trade>> groupedTrades = account.getOpenTrades().stream()
                .collect(Collectors.groupingBy(de.trademonitor.model.Trade::getMagicNumber, LinkedHashMap::new, Collectors.toList()));
        
        Map<Long, Map<String, Object>> groupSummaries = new LinkedHashMap<>();
        for (Map.Entry<Long, List<de.trademonitor.model.Trade>> entry : groupedTrades.entrySet()) {
            Long magic = entry.getKey();
            List<de.trademonitor.model.Trade> trades = entry.getValue();
            
            double sumLots = trades.stream().mapToDouble(de.trademonitor.model.Trade::getVolume).sum();
            double sumProfit = trades.stream().mapToDouble(t -> t.getProfit() + t.getSwap()).sum();
            double sumDrawdown = trades.stream()
                .mapToDouble(t -> (t.getMaxDrawdown() != null ? t.getMaxDrawdown() : 0.0))
                .sum();
            
            Map<String, Object> groupSummary = new HashMap<>();
            groupSummary.put("magic", magic);
            groupSummary.put("name", mappings.getOrDefault(magic, "Magic " + magic));
            groupSummary.put("sumLots", sumLots);
            groupSummary.put("sumProfit", sumProfit);
            groupSummary.put("sumDrawdown", sumDrawdown);
            groupSummary.put("sumDrawdownPct", account.getBalance() > 0 ? (sumDrawdown / account.getBalance()) * 100.0 : 0.0);
            
            groupSummaries.put(magic, groupSummary);
        }
        
        model.addAttribute("groupedTrades", groupedTrades);
        model.addAttribute("groupSummaries", groupSummaries);
        model.addAttribute("magicMappings", mappings);

        long t1 = System.currentTimeMillis();
        // Pass resolver to getMagicProfitEntries
        model.addAttribute("magicProfits", account.getMagicProfitEntries(maxAge, minTrades, mappings::get));
        long t2 = System.currentTimeMillis();
        model.addAttribute("magicMaxAge", maxAge);
        model.addAttribute("magicMinTrades", minTrades);

        // Build magic curve data as JSON for Chart.js
        model.addAttribute("magicCurveJson", buildMagicCurveJson(account, maxAge, mappings));
        long t3 = System.currentTimeMillis();

        // Provide accountId for AJAX save
        model.addAttribute("accountId", account.getAccountId());

        // Add today's date for highlighting (format matches UI: yyyy.MM.dd)
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
        model.addAttribute("todayDate", java.time.LocalDate.now().format(formatter));

        // Account Export Stats
        model.addAttribute("accountTradeCount", closedTradeRepository.countByAccountId(accountId));
        model.addAttribute("accountTradeStart", closedTradeRepository.findMinCloseTimeByAccountId(accountId));
        model.addAttribute("accountTradeEnd", closedTradeRepository.findMaxCloseTimeByAccountId(accountId));

        // Live Indicator Config
        model.addAttribute("liveGreenMins", globalConfigService.getLiveGreenMins());
        model.addAttribute("liveYellowMins", globalConfigService.getLiveYellowMins());
        model.addAttribute("liveOrangeMins", globalConfigService.getLiveOrangeMins());
        model.addAttribute("liveColorGreen", globalConfigService.getLiveColorGreen());
        model.addAttribute("liveColorYellow", globalConfigService.getLiveColorYellow());
        model.addAttribute("liveColorOrange", globalConfigService.getLiveColorOrange());
        model.addAttribute("liveColorRed", globalConfigService.getLiveColorRed());

        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUserEntity());
        }

        List<de.trademonitor.entity.TimelineEntity> timelines = timelineRepository.findByAccountIdOrderByTimelineDateAsc(accountId);
        model.addAttribute("timelines", timelines);

        LOG.info("[PERF] accountDetail(" + accountId + "): total=" + (System.currentTimeMillis() - t0) + "ms | magicProfits=" + (t2 - t1) + "ms | magicCurves=" + (t3 - t2) + "ms");
        return "account-detail";
    }

    /**
     * Build JSON string with cumulative profit curves per magic number.
     * Format: { "12345": { "labels": ["2026-01-01 10:00", ...], "data": [10.5,
     * 25.3, ...] }, ... }
     */
    private String buildMagicCurveJson(Account account, int maxAgeDays, Map<Long, String> mappings) {
        try {
            // Group closed trades by magic number
            Map<Long, List<ClosedTrade>> byMagic = account.getClosedTrades().stream()
                    .collect(Collectors.groupingBy(ClosedTrade::getMagicNumber));

            java.time.LocalDateTime cutoffDate = maxAgeDays > 0 ? java.time.LocalDateTime.now().minusDays(maxAgeDays)
                    : null;
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy.MM.dd HH:mm:ss");

            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<Long, List<ClosedTrade>> entry : new TreeMap<>(byMagic).entrySet()) {
                Long magic = entry.getKey();

                // Filtering Logic (same as in Account.getMagicProfitEntries)
                boolean hasOpenTrades = account.getOpenTrades().stream().anyMatch(t -> t.getMagicNumber() == magic);

                if (!hasOpenTrades && cutoffDate != null) {
                    List<ClosedTrade> trades = entry.getValue();
                    Optional<String> maxCloseTime = trades.stream()
                            .map(ClosedTrade::getCloseTime)
                            .filter(Objects::nonNull)
                            .max(String::compareTo);

                    if (maxCloseTime.isPresent()) {
                        try {
                            java.time.LocalDateTime closeTime = java.time.LocalDateTime.parse(maxCloseTime.get(),
                                    formatter);
                            if (closeTime.isBefore(cutoffDate)) {
                                continue; // Skip this magic number
                            }
                        } catch (java.time.format.DateTimeParseException e) {
                            // Ignore parse errors
                        }
                    }
                }

                List<ClosedTrade> trades = entry.getValue();
                // Sort by close time
                trades.sort(Comparator.comparing(ClosedTrade::getCloseTime, Comparator.nullsLast(String::compareTo)));

                List<String> labels = new ArrayList<>();
                List<Double> data = new ArrayList<>();
                double cumulative = 0;

                // Find first non-empty comment for this magic: CUSTOM MAPPING FIRST
                String comment = mappings.getOrDefault(entry.getKey(), "");
                if (comment == null || comment.isEmpty()) {
                    comment = trades.stream()
                            .map(ClosedTrade::getComment)
                            .filter(c -> c != null && !c.isEmpty())
                            .findFirst()
                            .orElse("");
                }

                for (ClosedTrade t : trades) {
                    cumulative += t.getProfit();
                    labels.add(t.getCloseTime() != null ? t.getCloseTime() : "");
                    data.add(Math.round(cumulative * 100.0) / 100.0);
                }

                Map<String, Object> curveData = new LinkedHashMap<>();
                curveData.put("labels", labels);
                curveData.put("data", data);
                curveData.put("comment", comment);
                result.put(String.valueOf(entry.getKey()), curveData);
            }
            return new ObjectMapper().writeValueAsString(result);
        } catch (Exception e) {
            return "{}";
        }
    }

    @PostMapping("/admin/mail-config")
    public String updateMailConfig(
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String password,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam int maxPerDay) {

        globalConfigService.saveMailConfig(host, port, user, password, from, to, maxPerDay);
        return "redirect:/admin";
    }

    @PostMapping("/admin/homey-config")
    public String updateHomeyConfig(
            @RequestParam String homeyId,
            @RequestParam String homeyEvent,
            @RequestParam(required = false) boolean triggerSync,
            @RequestParam(required = false) boolean triggerApi,
            @RequestParam(required = false) boolean triggerHealth,
            @RequestParam(required = false) boolean triggerSecurity,
            @RequestParam(required = false) boolean triggerOffline,
            @RequestParam int repeatCount,
            @RequestParam(defaultValue = "5") int syncAlarmDelayMins,
            @RequestParam(defaultValue = "15") int homeyRepeatIntervalMins) {

        globalConfigService.saveHomeyConfig(homeyId, homeyEvent, triggerSync, triggerApi,
                triggerHealth, triggerSecurity, triggerOffline, repeatCount, syncAlarmDelayMins, homeyRepeatIntervalMins);
        return "redirect:/admin";
    }

    @GetMapping("/api/report-details/{period}/{accountId}")
    @ResponseBody
    public ResponseEntity<?> getReportDetails(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String period,
            @PathVariable Long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body("Access Denied");
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate startDt = today;
        java.time.LocalDate endDt = today;
        switch (period.toLowerCase()) {
            case "weekly":
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.of(Locale.getDefault()).dayOfWeek();
                startDt = today.with(fieldISO, 1);
                endDt = today.with(fieldISO, 7);
                break;
            case "monthly":
                startDt = today.withDayOfMonth(1);
                endDt = today.withDayOfMonth(today.lengthOfMonth());
                break;
        }
        java.time.format.DateTimeFormatter tradeDateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String startCloseTime = startDt.format(tradeDateFormatter) + " 00:00:00";
        String endCloseTime = endDt.format(tradeDateFormatter) + " 23:59:59";

        List<de.trademonitor.entity.ClosedTradeEntity> allTrades = closedTradeRepository.findByAccountIdAndDateRange(accountId, startCloseTime, endCloseTime);

        return ResponseEntity.ok(allTrades.stream()
                .sorted((a, b) -> b.getCloseTime().compareTo(a.getCloseTime())) // Newest first
                .collect(Collectors.toList()));
    }

    @GetMapping("/api/report-chart/{period}")
    @ResponseBody
    public Map<String, Object> getReportChartData(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String period) {
        List<Map<String, Object>> accounts = accountManager.getAccountsWithStatus();

        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && allowed.contains(id);
                    })
                    .collect(Collectors.toList());
        }

        List<Double> data;
        List<String> labels;

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter fullFormatter = java.time.format.DateTimeFormatter
                .ofPattern("yyyy.MM.dd HH:mm:ss");

        // Filter valid trades first (Real accounts preferred? No, specific requirement
        // was closed trades)
        // User said: "nur die gewinn die geschlossen wurden" -> Aggregated for ALL
        // accounts?
        // Based on the tiles, it seems to show global performance for that period.

        // 1. Collect all relevant trades
        java.time.LocalDate startDt = today;
        java.time.LocalDate endDt = today;
        switch (period.toLowerCase()) {
            case "weekly":
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.of(Locale.getDefault()).dayOfWeek();
                startDt = today.with(fieldISO, 1);
                endDt = today.with(fieldISO, 7);
                break;
            case "monthly":
                startDt = today.withDayOfMonth(1);
                endDt = today.withDayOfMonth(today.lengthOfMonth());
                break;
        }
        java.time.format.DateTimeFormatter tradeDateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String startCloseTime = startDt.format(tradeDateFormatter) + " 00:00:00";
        String endCloseTime = endDt.format(tradeDateFormatter) + " 23:59:59";

        List<Long> accIds = accounts.stream().map(acc -> (Long) acc.get("accountId")).filter(Objects::nonNull).collect(Collectors.toList());
        List<de.trademonitor.entity.ClosedTradeEntity> allRelevantTrades = new ArrayList<>();
        if (!accIds.isEmpty()) {
            allRelevantTrades = closedTradeRepository.findByAccountIdsAndDateRange(accIds, startCloseTime, endCloseTime);
        }

        // 2. Aggregate based on period
        if ("daily".equalsIgnoreCase(period)) {
            // Hourly aggregation (00-23)
            labels = new ArrayList<>();
            Double[] hourlyProfit = new Double[24];
            Arrays.fill(hourlyProfit, 0.0);

            for (int i = 0; i < 24; i++)
                labels.add(String.format("%02d", i));

            for (de.trademonitor.entity.ClosedTradeEntity trade : allRelevantTrades) {
                try {
                    java.time.LocalDateTime dt = java.time.LocalDateTime.parse(trade.getCloseTime(), fullFormatter);
                    int hour = dt.getHour();
                    hourlyProfit[hour] += trade.getProfit();
                } catch (Exception e) {
                }
            }
            data = Arrays.asList(hourlyProfit);

        } else if ("weekly".equalsIgnoreCase(period)) {
            // Day of week aggregation (Mon-Sun)
            labels = Arrays.asList("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So");
            Double[] dailyProfit = new Double[7];
            Arrays.fill(dailyProfit, 0.0);

            for (de.trademonitor.entity.ClosedTradeEntity trade : allRelevantTrades) {
                try {
                    java.time.LocalDateTime dt = java.time.LocalDateTime.parse(trade.getCloseTime(), fullFormatter);
                    int dayIndex = dt.getDayOfWeek().getValue() - 1; // 1 (Mon) -> 0
                    dailyProfit[dayIndex] += trade.getProfit();
                } catch (Exception e) {
                }
            }
            data = Arrays.asList(dailyProfit);

        } else if ("monthly".equalsIgnoreCase(period)) {
            // Day of month aggregation
            int daysInMonth = today.lengthOfMonth();
            labels = new ArrayList<>();
            Double[] dailyProfit = new Double[daysInMonth];
            Arrays.fill(dailyProfit, 0.0);

            for (int i = 1; i <= daysInMonth; i++)
                labels.add(String.valueOf(i));

            for (de.trademonitor.entity.ClosedTradeEntity trade : allRelevantTrades) {
                try {
                    java.time.LocalDateTime dt = java.time.LocalDateTime.parse(trade.getCloseTime(), fullFormatter);
                    int day = dt.getDayOfMonth();
                    if (day >= 1 && day <= daysInMonth) {
                        dailyProfit[day - 1] += trade.getProfit();
                    }
                } catch (Exception e) {
                }
            }
            data = Arrays.asList(dailyProfit);
        } else {
            return new HashMap<>();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("labels", labels);
        response.put("data", data);
        return response;
    }

    private java.util.function.Predicate<de.trademonitor.entity.EquitySnapshotEntity> getSnapshotDateFilter(
            String period) {
        java.time.LocalDate today = java.time.LocalDate.now();
        switch (period.toLowerCase()) {
            case "daily":
                String todayStr = today.toString(); // yyyy-MM-dd
                return s -> s.getTimestamp() != null && s.getTimestamp().startsWith(todayStr);
            case "monthly":
                String monthStr = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                return s -> s.getTimestamp() != null && s.getTimestamp().startsWith(monthStr);
            case "weekly":
                java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.of(Locale.getDefault());
                int currentWeek = today.get(weekFields.weekOfWeekBasedYear());
                int currentYear = today.getYear();
                return s -> {
                    try {
                        if (s.getTimestamp() == null)
                            return false;
                        java.time.LocalDateTime dt = java.time.LocalDateTime.parse(s.getTimestamp(),
                                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        return dt.get(weekFields.weekOfWeekBasedYear()) == currentWeek && dt.getYear() == currentYear;
                    } catch (Exception e) {
                        return false;
                    }
                };
            default:
                return s -> true;
        }
    }

    private java.util.function.Predicate<String> getDateFilter(String period) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
        java.time.format.DateTimeFormatter monthFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM");
        java.time.format.DateTimeFormatter fullFormatter = java.time.format.DateTimeFormatter
                .ofPattern("yyyy.MM.dd HH:mm:ss");

        switch (period.toLowerCase()) {
            case "daily":
                String todayStr = today.format(dateFormatter);
                return date -> date.startsWith(todayStr);
            case "monthly":
                String monthStr = today.format(monthFormatter);
                return date -> date.startsWith(monthStr);
            case "weekly":
                java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.of(Locale.getDefault());
                int currentWeek = today.get(weekFields.weekOfWeekBasedYear());
                int currentYear = today.getYear();
                return date -> {
                    try {
                        java.time.LocalDateTime tradeDate = java.time.LocalDateTime.parse(date, fullFormatter);
                        return tradeDate.get(weekFields.weekOfWeekBasedYear()) == currentWeek
                                && tradeDate.getYear() == currentYear;
                    } catch (Exception e) {
                        return false;
                    }
                };
            default:
                return date -> true;
        }
    }

    private String[] getIsoRange(String period) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate startDt = today;
        java.time.LocalDate endDt = today;

        switch (period.toLowerCase()) {
            case "daily":
                startDt = today;
                endDt = today;
                break;
            case "weekly":
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.of(Locale.getDefault()).dayOfWeek();
                startDt = today.with(fieldISO, 1);
                endDt = today.with(fieldISO, 7);
                break;
            case "monthly":
                startDt = today.withDayOfMonth(1);
                endDt = today.withDayOfMonth(today.lengthOfMonth());
                break;
        }

        String fromIso = startDt.toString() + "T00:00:00";
        String toIso = endDt.toString() + "T23:59:59";
        return new String[]{fromIso, toIso};
    }

    @GetMapping("/report/{period}")
    public String getReport(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String period,
            Model model, HttpServletRequest request) {
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> accounts = accountManager.getAccountsWithStatus();

        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && allowed.contains(id);
                    })
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> reportData = new ArrayList<>();

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

        java.time.LocalDate startDt = today;
        java.time.LocalDate endDt = today;

        String periodTitle = "";
        switch (period.toLowerCase()) {
            case "daily":
                periodTitle = "Tagesreport";
                startDt = today;
                endDt = today;
                break;
            case "weekly":
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.of(Locale.getDefault()).dayOfWeek();
                startDt = today.with(fieldISO, 1);
                endDt = today.with(fieldISO, 7);
                periodTitle = "Wochenreport";
                break;
            case "monthly":
                startDt = today.withDayOfMonth(1);
                endDt = today.withDayOfMonth(today.lengthOfMonth());
                periodTitle = "Monatsreport";
                break;
            default:
                periodTitle = "Report (" + period + ")";
        }

        model.addAttribute("startDate", startDt.format(dateFormatter));
        model.addAttribute("endDate", endDt.format(dateFormatter));

        java.time.format.DateTimeFormatter tradeDateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String startCloseTime = startDt.format(tradeDateFormatter) + " 00:00:00";
        String endCloseTime = endDt.format(tradeDateFormatter) + " 23:59:59";

        // Batch-aggregate closed trades (much faster than loading all trades)
        Map<Long, long[]> batchAggregation = new HashMap<>(); // [count, sumProfit*100]
        List<Long> accIds = accounts.stream().map(acc -> (Long) acc.get("accountId")).filter(Objects::nonNull).collect(Collectors.toList());
        if (!accIds.isEmpty()) {
            List<Object[]> rows = closedTradeRepository.aggregateByAccountIdsAndDateRange(accIds, startCloseTime, endCloseTime);
            for (Object[] row : rows) {
                Long accId = ((Number) row[0]).longValue();
                long count = ((Number) row[1]).longValue();
                double sum = ((Number) row[2]).doubleValue();
                batchAggregation.put(accId, new long[]{count, Math.round(sum * 100)});
            }
        }

        java.util.function.Predicate<String> dateFilter = getDateFilter(period);

        for (Map<String, Object> acc : accounts) {
            Long accountId = (Long) acc.get("accountId");

            long[] agg = batchAggregation.getOrDefault(accountId, new long[]{0, 0});
            long closedCount = agg[0];
            double closedProfit = agg[1] / 100.0;

            // Get equity snapshots for the period (still needed for sparkline)
            String[] isoRange = getIsoRange(period);
            List<de.trademonitor.entity.EquitySnapshotEntity> snapshots = tradeStorage.loadEquitySnapshotsBetween(accountId, isoRange[0], isoRange[1]);
            List<Double> equityH = new ArrayList<>();
            List<Double> balanceH = new ArrayList<>();

            for (de.trademonitor.entity.EquitySnapshotEntity snap : snapshots) {
                equityH.add(snap.getEquity());
                balanceH.add(snap.getBalance());
            }

            Map<String, Object> row = new HashMap<>(acc);
            row.put("closedCount", closedCount);
            row.put("closedProfit", closedProfit);
            row.put("equityHistory", equityH);
            row.put("balanceHistory", balanceH);
            reportData.add(row);
        }

        // Sort: Real first, then Name
        reportData.sort((a, b) -> {
            String typeA = (String) a.get("type");
            String typeB = (String) b.get("type");
            if (!Objects.equals(typeA, typeB)) {
                return "REAL".equals(typeA) ? -1 : 1;
            }
            String nameA = (String) a.get("name");
            String nameB = (String) b.get("name");
            return ResultComparator.compare(nameA, nameB);
        });

        model.addAttribute("reportData", reportData);
        model.addAttribute("periodTitle", periodTitle);
        model.addAttribute("period", period); // daily, weekly, monthly

        Boolean isMobile = (Boolean) request.getAttribute("isMobile");
        if (isMobile != null && isMobile) {
            LOG.info("[PERF] report(" + period + ",mobile): total=" + (System.currentTimeMillis() - t0) + "ms");
            return "mobile-report";
        }
        LOG.info("[PERF] report(" + period + "): total=" + (System.currentTimeMillis() - t0) + "ms");
        return "report";
    }

    /**
     * API endpoint for scaled performance comparison chart.
     * Returns equity curves for all accounts, scaled to a 10K reference account.
     * Scale factor = 10000 / firstBalanceInPeriod
     */
    @GetMapping("/api/report-scaled-curves/{period}")
    @ResponseBody
    public List<Map<String, Object>> getScaledCurves(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String period) {
        List<Map<String, Object>> accounts = accountManager.getAccountsWithStatus();

        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && allowed.contains(id);
                    })
                    .collect(Collectors.toList());
        }

        String[] isoRange = getIsoRange(period);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> acc : accounts) {
            Long accountId = (Long) acc.get("accountId");
            String name = (String) acc.get("name");
            String type = (String) acc.get("type");
            Double currentBalance = (Double) acc.get("balance");

            List<de.trademonitor.entity.EquitySnapshotEntity> snapshots = tradeStorage.loadEquitySnapshotsBetween(accountId, isoRange[0], isoRange[1]);

            List<String> timestamps = new ArrayList<>();
            List<Double> scaledEquity = new ArrayList<>();
            List<Double> rawEquity = new ArrayList<>();
            double scaleFactor = 1.0;
            boolean scaleFactorSet = false;

            // Downsampling: keep every N-th minute to reduce data volume
            // daily = all points, weekly = every 15min, monthly = every 60min
            int sampleIntervalMinutes = "weekly".equalsIgnoreCase(period) ? 15
                    : "monthly".equalsIgnoreCase(period) ? 60 : 0;
            long lastIncludedMillis = 0;

            for (de.trademonitor.entity.EquitySnapshotEntity snap : snapshots) {
                // Apply downsampling for weekly/monthly
                if (sampleIntervalMinutes > 0 && snap.getTimestamp() != null) {
                    try {
                        long snapMillis = java.time.LocalDateTime
                                .parse(snap.getTimestamp(),
                                        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                        if (lastIncludedMillis > 0
                                && (snapMillis - lastIncludedMillis) < sampleIntervalMinutes * 60000L) {
                            continue; // Skip this snapshot (too close to the last included one)
                        }
                        lastIncludedMillis = snapMillis;
                    } catch (Exception e) {
                        // If parsing fails, include the snapshot
                    }
                }
                if (!scaleFactorSet && snap.getEquity() > 0) {
                    scaleFactor = 10000.0 / snap.getEquity();
                    scaleFactorSet = true;
                }
                timestamps.add(snap.getTimestamp());
                scaledEquity.add(Math.round(snap.getEquity() * scaleFactor * 100.0) / 100.0);
                rawEquity.add(Math.round(snap.getEquity() * 100.0) / 100.0);
            }

            if (!scaledEquity.isEmpty()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("accountId", accountId);
                entry.put("name", name != null ? name : "Account " + accountId);
                entry.put("type", type != null ? type : "DEMO");
                entry.put("balance", currentBalance);
                entry.put("scaleFactor", Math.round(scaleFactor * 10000.0) / 10000.0);
                entry.put("timestamps", timestamps);
                entry.put("scaledEquity", scaledEquity);
                entry.put("rawEquity", rawEquity);
                result.add(entry);
            }
        }

        // Sort: REAL first, then by name
        result.sort((a, b) -> {
            String typeA = (String) a.get("type");
            String typeB = (String) b.get("type");
            if (!Objects.equals(typeA, typeB)) {
                return "REAL".equals(typeA) ? -1 : 1;
            }
            return ResultComparator.compare((String) a.get("name"), (String) b.get("name"));
        });

        return result;
    }

    @GetMapping("/trade-comparison")
    public String tradeComparison(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long accountA,
            @RequestParam(required = false) Long accountB,
            @RequestParam(required = false, defaultValue = "20") int toleranceSec,
            @RequestParam(required = false, defaultValue = "today") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {

        // Access checks
        if (accountA != null && !isAllowedAccess(userDetails, accountA)) {
            return "redirect:/";
        }
        if (accountB != null && !isAllowedAccess(userDetails, accountB)) {
            return "redirect:/";
        }

        // Run comparison if both accounts are selected
        List<de.trademonitor.dto.AccountTradeComparisonDto> comparisons = new ArrayList<>();
        if (accountA != null && accountB != null) {
            // If custom dates are provided, use them instead of the preset period
            if (startDate != null && !startDate.isEmpty()) {
                java.time.LocalDateTime start = null;
                java.time.LocalDateTime end = null;
                try {
                    start = java.time.LocalDate.parse(startDate).atStartOfDay();
                } catch (Exception ignored) {}
                try {
                    if (endDate != null && !endDate.isEmpty()) {
                        end = java.time.LocalDate.parse(endDate).atTime(23, 59, 59);
                    }
                } catch (Exception ignored) {}
                comparisons = tradeComparisonService.compareAccounts(accountA, accountB, toleranceSec, start, end);
                period = "custom";
            } else {
                comparisons = tradeComparisonService.compareAccounts(accountA, accountB, toleranceSec, period);
            }
        }

        // Provide all accounts for selection (not just Real)
        List<Account> allAccounts = accountManager.getAccountsSortedByPrivilege();

        // Filter by user permissions
        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            allAccounts = allAccounts.stream()
                    .filter(a -> allowed.contains(a.getAccountId()))
                    .collect(Collectors.toList());
        }

        // Summary stats
        long matchedCount = comparisons.stream().filter(c -> "MATCHED".equals(c.getMatchStatus())).count();
        long onlyACount = comparisons.stream().filter(c -> "ONLY_A".equals(c.getMatchStatus())).count();
        long onlyBCount = comparisons.stream().filter(c -> "ONLY_B".equals(c.getMatchStatus())).count();
        long totalCount = comparisons.size();

        model.addAttribute("comparisons", comparisons);
        model.addAttribute("allAccounts", allAccounts);
        model.addAttribute("selectedAccountA", accountA);
        model.addAttribute("selectedAccountB", accountB);
        model.addAttribute("selectedToleranceSec", toleranceSec);
        model.addAttribute("selectedPeriod", period);
        model.addAttribute("selectedStartDate", startDate);
        model.addAttribute("selectedEndDate", endDate);
        model.addAttribute("matchedCount", matchedCount);
        model.addAttribute("onlyACount", onlyACount);
        model.addAttribute("onlyBCount", onlyBCount);
        model.addAttribute("totalCount", totalCount);

        return "trade-comparison";
    }



    @GetMapping("/api/stats/system-status")
    @ResponseBody
    public Map<String, Object> getSystemStatus(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        result.put("isAdmin", isAdmin);
        
        List<Map<String, Object>> accounts = accountManager.getAccountsWithStatus();
        if (!isAdmin && userDetails != null) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && allowed.contains(id);
                    })
                    .collect(Collectors.toList());
        }
        
        List<Map<String, Object>> allAccounts = new ArrayList<>();
        boolean[] accountsOkRef = {true};

        for (int pass = 0; pass < 2; pass++) {
            boolean includeReal = (pass == 0);
            for (Map<String, Object> acc : accounts) {
                boolean isReal = "REAL".equals(acc.get("type"));
                if (isReal == includeReal) {
                    Map<String, Object> rAcc = new LinkedHashMap<>();
                    Long accountId = (Long) acc.get("accountId");
                    rAcc.put("accountId", accountId);
                    rAcc.put("name", acc.get("name"));
                    rAcc.put("type", acc.get("type"));
                    
                    boolean isOnline = Boolean.TRUE.equals(acc.get("online"));
                    boolean hasError = Boolean.TRUE.equals(acc.get("errorState"));
                    boolean hasSyncWarning = Boolean.TRUE.equals(acc.get("copierError"));
                    boolean hasAlarm = Boolean.TRUE.equals(acc.get("openProfitAlarmTriggered"));
                    
                    boolean statusOk = isOnline && !hasError && !hasSyncWarning && !hasAlarm;
                    if (isReal && !statusOk) {
                        accountsOkRef[0] = false;
                    }
                    
                    rAcc.put("statusOk", statusOk);
                    rAcc.put("online", isOnline);
                    rAcc.put("error", hasError);
                    rAcc.put("syncWarning", hasSyncWarning);
                    rAcc.put("alarm", hasAlarm);
                    rAcc.put("openTrades", acc.get("trades"));
                    rAcc.put("profit", acc.get("profit"));
                    rAcc.put("currency", acc.get("currency"));
                    
                    double dailyProfit = accountManager.getCachedDailyProfit(accountId);
                    rAcc.put("dailyProfit", dailyProfit);
                    
                    allAccounts.add(rAcc);
                }
            }
        }
        
        boolean accountsOk = accountsOkRef[0];
        
        boolean overallOk = accountsOk;
        
        if (isAdmin) {
            de.trademonitor.dto.SecurityAuditDto audit = securityAuditService.getLatestAudit();
            boolean attackOk = (audit == null || audit.getOverallStatus() == de.trademonitor.dto.SecurityAuditDto.Status.GREEN);
            
            String healthStatus = serverHealthMonitorService.getLastStatus();
            boolean healthOk = "OK".equals(healthStatus);
            
            Map<String, Object> syncMetrics = copierVerificationService.getMetrics();
            boolean syncOk = "OK".equals(syncMetrics.get("status"));
            
            overallOk = accountsOk && attackOk && healthOk && syncOk;
            
            Map<String, Object> serverDetails = new LinkedHashMap<>();
            serverDetails.put("attackOk", attackOk);
            serverDetails.put("healthOk", healthOk);
            serverDetails.put("syncOk", syncOk);
            if (audit != null) {
                serverDetails.put("attackMessage", audit.getStatusMessage());
            }
            serverDetails.put("healthProblems", serverHealthMonitorService.getLastProblems());
            serverDetails.put("syncStatus", syncMetrics.get("status"));
            
            result.put("server", serverDetails);
        }
        
        result.put("overallOk", overallOk);
        result.put("networkStatus", networkStatusService.getCurrentStatus());
        result.put("allAccounts", allAccounts);
        // Keep realAccounts for backwards compatibility just in case other dashboards use it
        result.put("realAccounts", allAccounts.stream().filter(a -> "REAL".equals(a.get("type"))).collect(Collectors.toList()));
        
        return result;
    }

    @GetMapping("/api/login-logs")
    @ResponseBody
    public java.util.List<java.util.Map<String, Object>> getRecentLoginLogs() {
        java.util.List<de.trademonitor.entity.LoginLog> all = loginLogRepository.findAllByOrderByTimestampDesc();
        int limit = Math.min(all.size(), 20);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for(int i=0; i<limit; i++) {
             de.trademonitor.entity.LoginLog log = all.get(i);
             java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
             map.put("time", log.getTimestamp() != null ? log.getTimestamp().toString() : "");
             map.put("user", log.getUsername());
             map.put("ip", log.getIpAddress());
             map.put("status", log.isSuccess() ? "SUCCESS" : "FAILED");
             result.add(map);
        }
        return result;
    }

    /**
     * EA Logs page for a specific account.
     */
    @GetMapping("/account/{accountId}/ea-logs")
    public String eaLogs(@PathVariable Long accountId, Model model) {
        model.addAttribute("accountId", accountId);
        
        // Get account name for display
        var acc = accountManager.getAllAccounts().stream()
                .filter(a -> a.getAccountId() == accountId)
                .findFirst().orElse(null);
        model.addAttribute("accountName", acc != null && acc.getName() != null && !acc.getName().isEmpty() ? acc.getName() : String.valueOf(accountId));
        
        java.util.List<de.trademonitor.entity.EaLogEntry> logs = eaLogEntryRepository.findTop5000ByAccountIdOrderByTimestampDesc(accountId);
        model.addAttribute("logs", logs);
        model.addAttribute("logCount", eaLogEntryRepository.countByAccountId(accountId));

        java.time.LocalDateTime acceptedAt = acc != null ? acc.getEaLogAcceptedAt() : null;
        if (acceptedAt != null) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            model.addAttribute("eaLogAcceptedAtFormatted", acceptedAt.format(formatter));
        } else {
            model.addAttribute("eaLogAcceptedAtFormatted", null);
        }

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        model.addAttribute("isAdmin", isAdmin);
        
        return "ea-logs";
    }

    /**
     * REST API to fetch EA logs for AJAX refreshing.
     */
    @GetMapping("/api/ea-logs/{accountId}")
    @ResponseBody
    public java.util.List<java.util.Map<String, Object>> getEaLogsApi(@PathVariable Long accountId) {
        java.util.List<de.trademonitor.entity.EaLogEntry> logs = eaLogEntryRepository.findTop5000ByAccountIdOrderByTimestampDesc(accountId);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        for (de.trademonitor.entity.EaLogEntry log : logs) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", log.getId());
            item.put("timestamp", log.getTimestamp() != null ? log.getTimestamp().format(fmt) : "");
            item.put("logLine", log.getLogLine());
            result.add(item);
        }
        return result;
    }

    // ==================== ANALYTICS PAGE ====================

    @GetMapping("/analytics")
    public String analyticsPage(Model model) {
        return "analytics";
    }

    // ==================== ANALYTICS API ENDPOINTS ====================

    /**
     * Heatmap: Profit aggregated by Weekday x Hour.
     */
    @GetMapping("/api/stats/heatmap")
    @ResponseBody
    public Map<String, Object> getHeatmapGlobal(@RequestParam(required = false) String type) {
        return strategyAnalyticsService.buildHeatmap(null, type);
    }

    @GetMapping("/api/stats/heatmap/{accountId}")
    @ResponseBody
    public Map<String, Object> getHeatmap(@PathVariable long accountId) {
        return strategyAnalyticsService.buildHeatmap(accountId, null);
    }

    /**
     * Strategy KPIs per magic number for a single account.
     */
    @GetMapping("/api/stats/strategy-kpis/{accountId}")
    @ResponseBody
    public List<Map<String, Object>> getStrategyKpis(@PathVariable long accountId) {
        return strategyAnalyticsService.getStrategyKpis(accountId);
    }

    /**
     * Global strategy leaderboard across all accounts.
     */
    @GetMapping("/api/stats/strategy-leaderboard")
    @ResponseBody
    public List<Map<String, Object>> getStrategyLeaderboard(@RequestParam(required = false) String type) {
        return strategyAnalyticsService.getGlobalLeaderboard(type);
    }

    /**
     * Correlation matrix between account daily returns.
     */
    @GetMapping("/api/stats/correlation-matrix")
    @ResponseBody
    public Map<String, Object> getCorrelationMatrix(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period) {
        return strategyAnalyticsService.getCorrelationMatrix(type, period);
    }

    /**
     * Drawdown curves for all accounts.
     */
    @GetMapping("/api/stats/drawdown-curves")
    @ResponseBody
    public List<Map<String, Object>> getDrawdownCurves(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period) {
        return strategyAnalyticsService.getDrawdownCurves(type, period);
    }

    /**
     * Equity overlay curves for portfolio analytics.
     */
    @GetMapping("/api/stats/equity-overlay")
    @ResponseBody
    public List<Map<String, Object>> getEquityOverlay(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period) {
        List<Account> accounts = accountManager.getAccountsSortedByPrivilege().stream()
                .filter(a -> type == null || type.isEmpty() || type.equalsIgnoreCase(a.getType()))
                .collect(Collectors.toList());

        java.util.function.Predicate<String> dateFilter = s -> true;
        if ("monthly".equalsIgnoreCase(period)) {
            String monthStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            dateFilter = s -> s.startsWith(monthStr);
        } else if ("weekly".equalsIgnoreCase(period)) {
            String weekStr = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString();
            dateFilter = s -> s.compareTo(weekStr) >= 0;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Account acc : accounts) {
            var snapshots = tradeStorage.loadEquitySnapshots(acc.getAccountId());
            if (snapshots.isEmpty()) continue;

            // Downsample to daily for normalization
            Map<String, Double> dailyEquity = new java.util.LinkedHashMap<>();
            final java.util.function.Predicate<String> filter = dateFilter;
            for (var snap : snapshots) {
                if (snap.getTimestamp() == null || !filter.test(snap.getTimestamp())) continue;
                String day = snap.getTimestamp().substring(0, 10);
                dailyEquity.put(day, snap.getEquity());
            }

            if (dailyEquity.size() < 2) continue;

            // Normalize to 10000 start
            double firstVal = dailyEquity.values().iterator().next();
            List<String> timestamps = new ArrayList<>(dailyEquity.keySet());
            List<Double> normalized = new ArrayList<>();
            List<Double> raw = new ArrayList<>();
            for (double eq : dailyEquity.values()) {
                normalized.add(Math.round(eq / firstVal * 10000.0 * 100.0) / 100.0);
                raw.add(Math.round(eq * 100.0) / 100.0);
            }

            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("accountId", acc.getAccountId());
            entry.put("name", acc.getName() != null ? acc.getName() : "Account " + acc.getAccountId());
            entry.put("type", acc.getType());
            entry.put("timestamps", timestamps);
            entry.put("normalized", normalized);
            entry.put("raw", raw);
            result.add(entry);
        }

        return result;
    }

    /**
     * Performance test endpoint — measures key operations and returns timing as JSON.
     */
    @GetMapping("/api/perf-test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> perfTest() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. getAccountsWithStatus (cache-based, should be fast)
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> accs = accountManager.getAccountsWithStatus();
        long t1 = System.currentTimeMillis();
        result.put("getAccountsWithStatus_ms", t1 - t0);
        result.put("accountCount", accs.size());

        // 2. EA Log Batch Query
        long t2 = System.currentTimeMillis();
        try {
            java.time.LocalDateTime logCutoff = java.time.LocalDateTime.now().minusHours(24);
            List<Object[]> severityRows = eaLogEntryRepository.getLogSeverityForAllAccountsSince(logCutoff);
            result.put("eaLogBatchQuery_ms", System.currentTimeMillis() - t2);
            result.put("eaLogBatchRows", severityRows != null ? severityRows.size() : 0);
        } catch (Exception e) {
            result.put("eaLogBatchQuery_ms", System.currentTimeMillis() - t2);
            result.put("eaLogBatchError", e.getMessage());
        }

        // 3. Global Trade Count
        long t3 = System.currentTimeMillis();
        List<Long> ids = accs.stream().map(a -> (Long) a.get("accountId")).filter(Objects::nonNull).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            long tradeCount = closedTradeRepository.countByAccountIds(ids);
            result.put("globalTradeCount_ms", System.currentTimeMillis() - t3);
            result.put("globalTradeCount", tradeCount);
        }

        // 4. Report Aggregation (daily, batch)
        long t4 = System.currentTimeMillis();
        String todayPrefix = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        if (!ids.isEmpty()) {
            List<Object[]> agg = closedTradeRepository.aggregateByAccountIdsAndPrefix(ids, todayPrefix);
            result.put("reportDailyAgg_ms", System.currentTimeMillis() - t4);
            result.put("reportDailyAggRows", agg != null ? agg.size() : 0);
        }

        // 5. Cached daily profit (should be instant)
        long t5 = System.currentTimeMillis();
        double totalDailyProfit = 0;
        for (Long accId : ids) {
            totalDailyProfit += accountManager.getCachedDailyProfit(accId);
        }
        result.put("cachedDailyProfit_ms", System.currentTimeMillis() - t5);
        result.put("totalDailyProfit", totalDailyProfit);

        // 6. Single account equity snapshots (biggest previous bottleneck)
        if (!ids.isEmpty()) {
            long t6 = System.currentTimeMillis();
            List<de.trademonitor.entity.EquitySnapshotEntity> snaps = tradeStorage.loadEquitySnapshots(ids.get(0));
            result.put("equitySnapshotLoad_ms", System.currentTimeMillis() - t6);
            result.put("equitySnapshotCount", snaps != null ? snaps.size() : 0);
            result.put("equitySnapshotAccountId", ids.get(0));
        }

        result.put("totalEndpoint_ms", System.currentTimeMillis() - t0);
        return ResponseEntity.ok(result);
    }

    private static class ResultComparator {
        static int compare(String s1, String s2) {
            if (s1 == null && s2 == null)
                return 0;
            if (s1 == null)
                return 1;
            if (s2 == null)
                return -1;
            return s1.compareToIgnoreCase(s2);
        }
    }

}
