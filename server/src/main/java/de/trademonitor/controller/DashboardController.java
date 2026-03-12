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
import de.trademonitor.security.CustomUserDetails;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for web dashboard views.
 */
@Controller
public class DashboardController {

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
    private de.trademonitor.service.TradeSyncService tradeSyncService;

    @Autowired
    private de.trademonitor.service.MagicMappingService magicMappingService;

    @Autowired
    private de.trademonitor.service.HomeyService homeyService;

    @Autowired
    private de.trademonitor.service.TradeStorage tradeStorage;

    @Autowired
    private de.trademonitor.service.TradeComparisonService tradeComparisonService;

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
        List<java.util.Map<String, Object>> allAccounts = accountManager.getAccountsWithStatus();

        // Filter by user permissions
        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
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
        for (java.util.Map<String, Object> acc : allAccounts) {
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

        model.addAttribute("syncMetrics", tradeSyncService.getMetrics());

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
            return "mobile-dashboard";
        }
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

        model.addAttribute("magicMappings", magicMappingService.getAllMappings());
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
        System.out.println("=== SAVE LAYOUT called with " + layoutData.size() + " sections ===");
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
                                System.out.println("    WARN: Could not parse accountId: " + idObj);
                            }
                        }

                        if (accountId != null) {
                            System.out.println(
                                    "    Saving account " + accountId + " -> section=" + sectionId + ", order=" + i);
                            accountManager.saveAccountSection(accountId, sectionId, i);
                            totalSaved++;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("  WARN: Could not parse sectionId key: " + entry.getKey());
            }
        }
        System.out.println("=== SAVE LAYOUT complete: " + totalSaved + " accounts saved ===");
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
        for (de.trademonitor.entity.EquitySnapshotEntity snap : snapshots) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", snap.getTimestamp());
            entry.put("equity", snap.getEquity());
            entry.put("balance", snap.getBalance());
            result.add(entry);
        }
        return ResponseEntity.ok(result);
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
        if (!isAllowedAccess(userDetails, accountId)) {
            return "redirect:/";
        }
        Account account = accountManager.getAccount(accountId);
        if (account == null) {
            return "redirect:/";
        }
        model.addAttribute("account", account);
        model.addAttribute("online", account.isOnline(accountManager.getTimeoutSeconds()));

        int maxAge = account.getMagicNumberMaxAge();
        int minTrades = account.getMagicMinTrades();
        Map<Long, String> mappings = magicMappingService.getAllMappings();

        // Pass resolver to getMagicProfitEntries
        model.addAttribute("magicProfits", account.getMagicProfitEntries(maxAge, minTrades, mappings::get));
        model.addAttribute("magicMaxAge", maxAge);
        model.addAttribute("magicMinTrades", minTrades);

        // Build magic curve data as JSON for Chart.js
        model.addAttribute("magicCurveJson", buildMagicCurveJson(account, maxAge, mappings));

        // Provide accountId for AJAX save
        model.addAttribute("accountId", account.getAccountId());

        // Add today's date for highlighting (format matches UI: yyyy.MM.dd)
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
        model.addAttribute("todayDate", java.time.LocalDate.now().format(formatter));

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
            @RequestParam int repeatCount,
            @RequestParam(defaultValue = "5") int syncAlarmDelayMins) {

        globalConfigService.saveHomeyConfig(homeyId, homeyEvent, triggerSync, triggerApi, repeatCount,
                syncAlarmDelayMins);
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
        List<de.trademonitor.entity.ClosedTradeEntity> allTrades = closedTradeRepository.findByAccountId(accountId);
        java.util.function.Predicate<String> dateFilter = getDateFilter(period);

        return ResponseEntity.ok(allTrades.stream()
                .filter(t -> t.getCloseTime() != null && dateFilter.test(t.getCloseTime()))
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
        java.util.function.Predicate<String> dateFilter = getDateFilter(period);
        List<de.trademonitor.entity.ClosedTradeEntity> allRelevantTrades = new ArrayList<>();

        for (Map<String, Object> acc : accounts) {
            Long accountId = (Long) acc.get("accountId");
            List<de.trademonitor.entity.ClosedTradeEntity> accountTrades = closedTradeRepository
                    .findByAccountId(accountId);
            allRelevantTrades.addAll(accountTrades.stream()
                    .filter(t -> t.getCloseTime() != null && dateFilter.test(t.getCloseTime()))
                    .collect(Collectors.toList()));
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

    @GetMapping("/report/{period}")
    public String getReport(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String period,
            Model model) {
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
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");

        // Determine period title (filter logic moved to helper)
        String periodTitle = "";
        switch (period.toLowerCase()) {
            case "daily":
                periodTitle = "Tagesreport (" + today.format(dateFormatter) + ")";
                break;
            case "monthly":
                periodTitle = "Monatsreport (" + today.getMonth() + " " + today.getYear() + ")";
                break;
            case "weekly":
                java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.of(Locale.getDefault());
                int currentWeek = today.get(weekFields.weekOfWeekBasedYear());
                periodTitle = "Wochenreport (KW " + currentWeek + ")";
                break;
            default:
                periodTitle = "Report (" + period + ")";
        }

        java.util.function.Predicate<String> dateFilter = getDateFilter(period);

        for (Map<String, Object> acc : accounts) {
            Long accountId = (Long) acc.get("accountId");

            // Get closed trades
            List<de.trademonitor.entity.ClosedTradeEntity> closedTrades = closedTradeRepository
                    .findByAccountId(accountId);

            // Filter and sum
            java.util.DoubleSummaryStatistics closedStats = closedTrades.stream()
                    .filter(t -> t.getCloseTime() != null && dateFilter.test(t.getCloseTime()))
                    .collect(java.util.DoubleSummaryStatistics::new,
                            (stats, t) -> stats.accept(t.getProfit()),
                            java.util.DoubleSummaryStatistics::combine);

            // Get equity snapshots for the period
            java.util.function.Predicate<de.trademonitor.entity.EquitySnapshotEntity> snapshotFilter = getSnapshotDateFilter(
                    period);
            List<de.trademonitor.entity.EquitySnapshotEntity> snapshots = tradeStorage.loadEquitySnapshots(accountId);
            List<Double> equityH = new ArrayList<>();
            List<Double> balanceH = new ArrayList<>();

            for (de.trademonitor.entity.EquitySnapshotEntity snap : snapshots) {
                if (snapshotFilter.test(snap)) {
                    equityH.add(snap.getEquity());
                    balanceH.add(snap.getBalance());
                }
            }

            Map<String, Object> row = new HashMap<>(acc);
            row.put("closedCount", closedStats.getCount());
            row.put("closedProfit", closedStats.getSum());
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

        java.util.function.Predicate<de.trademonitor.entity.EquitySnapshotEntity> snapshotFilter = getSnapshotDateFilter(
                period);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> acc : accounts) {
            Long accountId = (Long) acc.get("accountId");
            String name = (String) acc.get("name");
            String type = (String) acc.get("type");
            Double currentBalance = (Double) acc.get("balance");

            List<de.trademonitor.entity.EquitySnapshotEntity> snapshots = tradeStorage.loadEquitySnapshots(accountId);

            List<String> timestamps = new ArrayList<>();
            List<Double> scaledEquity = new ArrayList<>();
            double scaleFactor = 1.0;
            boolean scaleFactorSet = false;

            for (de.trademonitor.entity.EquitySnapshotEntity snap : snapshots) {
                if (snapshotFilter.test(snap)) {
                    if (!scaleFactorSet && snap.getBalance() > 0) {
                        scaleFactor = 10000.0 / snap.getBalance();
                        scaleFactorSet = true;
                    }
                    timestamps.add(snap.getTimestamp());
                    scaledEquity.add(Math.round(snap.getEquity() * scaleFactor * 100.0) / 100.0);
                }
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
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false, defaultValue = "today") String period,
            Model model) {

        // If user is restricted and requests an account they don't have access to
        if (accountId != null && !isAllowedAccess(userDetails, accountId)) {
            return "redirect:/";
        }

        List<de.trademonitor.dto.TradeComparisonDto> comparisons = tradeComparisonService.compareTrades(accountId,
                period);

        List<Account> realAccounts = accountManager.getAccountsSortedByPrivilege().stream()
                .filter(a -> "REAL".equalsIgnoreCase(a.getType()))
                .collect(Collectors.toList());

        // Filter the output list if no accountId was specified
        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            Set<Long> allowed = userDetails.getUserEntity().getAllowedAccountIds();
            if (accountId == null) {
                comparisons = comparisons.stream()
                        .filter(c -> c.getRealTrade() != null && allowed.contains(c.getRealTrade().getAccountId()))
                        .collect(Collectors.toList());
            }
            realAccounts = realAccounts.stream()
                    .filter(a -> allowed.contains(a.getAccountId()))
                    .collect(Collectors.toList());
        }

        model.addAttribute("comparisons", comparisons);
        model.addAttribute("realAccounts", realAccounts);
        model.addAttribute("selectedAccountId", accountId);
        model.addAttribute("selectedPeriod", period);

        return "trade-comparison";
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
