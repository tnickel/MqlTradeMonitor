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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import de.trademonitor.security.CustomUserDetails;
import de.trademonitor.entity.UserEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller for web dashboard views.
 */
@Controller
public class DashboardController {

    private static final Logger LOG = Logger.getLogger(DashboardController.class.getName());
    private static final long MAX_DOCUMENT_SIZE = 10L * 1024L * 1024L;
    private static final long MAX_CSV_SIZE = 10L * 1024L * 1024L;

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
    }

    private String sanitizeFileName(String originalName) {
        String name = originalName == null ? "document" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\p{Cntrl}\\\"]", "_").trim();
        if (name.isEmpty()) name = "document";
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    /** Returns a trusted content type after extension, signature and text checks. */
    private String detectAllowedDocumentType(String fileName, byte[] data) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && startsWith(data, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return "application/pdf";
        }
        if (lower.endsWith(".png") && data.length >= 8
                && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47) {
            return "image/png";
        }
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && data.length >= 3
                && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif") && data.length >= 6
                && (startsWith(data, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(data, "GIF89a".getBytes(StandardCharsets.US_ASCII)))) {
            return "image/gif";
        }
        if (lower.endsWith(".webp") && data.length >= 12
                && startsWith(data, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            try {
                StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data));
                return lower.endsWith(".txt") ? "text/plain" : "text/markdown";
            } catch (CharacterCodingException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private boolean isSafeInlineDocumentType(String contentType) {
        return contentType != null && Set.of("application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp",
                "text/plain", "text/markdown").contains(contentType);
    }

    private boolean isAllowedAccess(CustomUserDetails userDetails, long accountId) {
        if (userDetails == null)
            return false;
        UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                .orElse(userDetails.getUserEntity());
        return accountAccessService.canAccess(user, accountId);
    }

    /**
     * Returns the set of account IDs a user may see in cross-account (aggregate)
     * analytics, or {@code null} for admins (= unrestricted / all accounts). Used
     * by the analytics endpoints so a restricted or demo user never receives data
     * for accounts they may not access. An unauthenticated principal gets an empty
     * set (no access at all).
     */
    private Set<Long> analyticsAllowedIds(CustomUserDetails userDetails) {
        if (userDetails == null)
            return Collections.emptySet();
        if ("ROLE_ADMIN".equals(userDetails.getUserEntity().getRole()))
            return null;
        UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                .orElse(userDetails.getUserEntity());
        return accountAccessService.getAccessibleAccountIds(user);
    }

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private de.trademonitor.service.AccountAccessService accountAccessService;

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
    private de.trademonitor.service.HistoricalRatesService historicalRatesService;

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

    @Autowired
    private de.trademonitor.service.LlmService llmService;

    @Autowired
    private de.trademonitor.repository.LlmAnalysisLogRepository llmAnalysisLogRepository;

    @Autowired
    private de.trademonitor.service.CsvImportService csvImportService;

    @Autowired
    private de.trademonitor.service.UserService userService;

    @Autowired
    private de.trademonitor.repository.AccountDocumentRepository accountDocumentRepository;

    @Autowired
    private de.trademonitor.repository.AccountLinkRepository accountLinkRepository;

    @Autowired
    private de.trademonitor.repository.AccountRepository accountRepository;

    @ModelAttribute
    public void addCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails != null) {
            model.addAttribute("currentUser", userDetails.getUserEntity());
        }
    }

    @PostMapping("/api/test-siren")
    @ResponseBody
    public ResponseEntity<String> testSiren(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Nur der Admin darf die Sirene testen.");
        }
        homeyService.triggerSiren(true);
        return ResponseEntity.ok("Siren trigger sent");
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
        allAccounts = allAccounts.stream()
                .filter(acc -> !"CSV".equalsIgnoreCase((String) acc.get("type")))
                .collect(Collectors.toList());
        long t1 = System.currentTimeMillis();

        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        model.addAttribute("isAdmin", isAdmin);

        // Filter by user permissions
        if (userDetails != null && !isAdmin) {
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            allAccounts = allAccounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && accountAccessService.canAccess(user, id);
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
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            sortedAccounts = sortedAccounts.stream()
                    .filter(acc -> accountAccessService.canAccess(user, acc.getAccountId()))
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
                groupSummary.put("name", magicMappingService.resolveComment(magic,
                        trades.stream().map(de.trademonitor.model.Trade::getComment)
                                .filter(c -> c != null && !c.isBlank()).findFirst().orElse(null),
                        magicMappings));
                if (((String) groupSummary.get("name")).isBlank()) {
                    groupSummary.put("name", "Magic " + magic);
                }
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("magicNumber") Long magicNumber,
            @RequestParam("customComment") String customComment) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Admin is allowed to modify mappings.");
        }
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
            @RequestParam(value = "alarmPct", required = false) Double alarmPct,
            @RequestParam(value = "monitored", defaultValue = "true") boolean monitored,
            @RequestParam(value = "telegramTradesEnabled", defaultValue = "false") boolean telegramTradesEnabled,
            @RequestParam(value = "icon", required = false) MultipartFile icon,
            @RequestParam(value = "removeIcon", defaultValue = "false") boolean removeIcon,
            @org.springframework.security.core.annotation.AuthenticationPrincipal de.trademonitor.security.CustomUserDetails userDetails) {
        try {
            boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
            if (!isAdmin) {
                return ResponseEntity.status(403).body("Only Admin is allowed to modify accounts.");
            }
            accountManager.updateAccountDetails(accountId, name, type, alarmEnabled, alarmAbs, alarmPct, monitored, telegramTradesEnabled);
            if (removeIcon) {
                accountManager.updateAccountIcon(accountId, null);
            } else if (icon != null && !icon.isEmpty()) {
                byte[] bytes = icon.getBytes();
                String contentType = icon.getContentType();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String dataUrl = "data:" + contentType + ";base64," + base64;
                accountManager.updateAccountIcon(accountId, dataUrl);
            }
            return ResponseEntity.ok("Saved");
        } catch (Exception e) {
            LOG.severe("Failed to update account details or icon: " + e.getMessage());
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/api/account/meta-trader-info")
    @ResponseBody
    public ResponseEntity<String> updateMetaTraderInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountId") Long accountId,
            @RequestParam("metaTraderInfo") String metaTraderInfo) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        accountManager.updateMetaTraderInfo(accountId, metaTraderInfo);
        return ResponseEntity.ok("Saved");
    }

    @PostMapping("/api/account/prompt-analysis-config")
    @ResponseBody
    public ResponseEntity<String> updatePromptAnalysisConfig(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountId") Long accountId,
            @RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
            @RequestParam("customPrompt") String customPrompt) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body("Access Denied");
        }
        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        if (!isAdmin) {
            return ResponseEntity.status(403).body("Only Admin is allowed to change prompt analysis configuration.");
        }
        accountManager.updatePromptAnalysisConfig(accountId, enabled, customPrompt);
        return ResponseEntity.ok("Saved");
    }

    @PostMapping("/api/account/{accountId}/analyze-trades")
    @ResponseBody
    public ResponseEntity<?> analyzeTrades(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("accountId") Long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Access Denied"));
        }
        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        if (!isAdmin) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Nur der Admin darf die Analyse anstossen."));
        }
        try {
            String result = llmService.analyzeOpenTrades(accountId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("result", result);
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/api/account/{accountId}/analysis-logs")
    @ResponseBody
    public ResponseEntity<?> getAnalysisLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("accountId") Long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Access Denied"));
        }
        try {
            List<de.trademonitor.entity.LlmAnalysisLogEntity> logs = llmAnalysisLogRepository.findByAccountIdOrderByTimestampDesc(accountId);
            List<Map<String, Object>> result = new ArrayList<>();
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            for (de.trademonitor.entity.LlmAnalysisLogEntity log : logs) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", log.getId());
                map.put("timestamp", log.getTimestamp().toString());
                map.put("formattedTime", log.getTimestamp().format(formatter));
                map.put("result", log.getResult());
                result.add(map);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return ResponseEntity.status(500).body(Map.of("success", false, "error", msg));
        }
    }

    @PostMapping("/api/account/timelines")
    @ResponseBody
    public ResponseEntity<String> addTimeline(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountId") Long accountId,
            @RequestParam("timelineDate") String timelineDate) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        
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
    public ResponseEntity<String> deleteTimeline(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("id") Long id) {
        de.trademonitor.entity.TimelineEntity timeline = timelineRepository.findById(id).orElse(null);
        if (timeline == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAllowedAccess(userDetails, timeline.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        timelineRepository.delete(timeline);
        return ResponseEntity.ok("Deleted");
    }


    /**
     * AJAX Endpoint to update magic number max age for a specific account.
     */
    @PostMapping("/api/account/magic-max-age")
    @ResponseBody
    public ResponseEntity<String> updateMagicMaxAge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountId") Long accountId,
            @RequestParam("days") int days) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        accountManager.updateMagicNumberMaxAge(accountId, days);
        return ResponseEntity.ok("Saved");
    }

    /**
     * AJAX Endpoint to update magic min trades for a specific account.
     */
    @PostMapping("/api/account/magic-min-trades")
    @ResponseBody
    public ResponseEntity<String> updateMagicMinTrades(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountId") Long accountId,
            @RequestParam("minTrades") int minTrades) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("accountId") Long accountId,
            @RequestParam("magicNumberMaxAge") int magicNumberMaxAge,
            @RequestParam("magicMinTrades") int magicMinTrades) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountId") Long accountId) {
        // Reset is destructive (deletes all trades + equity snapshots) and must be
        // admin-only. Never rely on the button being hidden in the template — the
        // endpoint can be called directly, so enforce the role here as well.
        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        if (!isAdmin) {
            return ResponseEntity.status(403).body("Nur der Admin darf die Trades-Datenbank zurücksetzen.");
        }
        accountManager.resetAccountTrades(accountId);
        return ResponseEntity.ok("Reset complete");
    }

    /**
     * AJAX Endpoint to permanently delete an account and all its data
     * ("Delete Robot"). Only allowed for accounts that are no longer monitored,
     * so an actively watched MetaTrader cannot be removed by accident.
     */
    @PostMapping("/api/account/delete")
    @ResponseBody
    public ResponseEntity<String> deleteAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountId") Long accountId) {
        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Nur der Admin darf ein Konto löschen.");
        }
        de.trademonitor.model.Account account = accountManager.getAccount(accountId);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found");
        }
        if (account.isMonitored()) {
            return ResponseEntity.badRequest()
                    .body("Account is still monitored. Disable monitoring before deleting.");
        }
        accountManager.deleteAccount(accountId);
        return ResponseEntity.ok("Deleted");
    }

    // --- Section Management API ---

    @PostMapping("/api/section/create")
    @ResponseBody
    public ResponseEntity<?> createSection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("name") String name) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Admin is allowed to modify sections.");
        }
        return ResponseEntity.ok(accountManager.createSection(name));
    }

    @PostMapping("/api/section/rename")
    @ResponseBody
    public ResponseEntity<String> renameSection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("id") Long id,
            @RequestParam("name") String name) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Admin is allowed to modify sections.");
        }
        accountManager.renameSection(id, name);
        return ResponseEntity.ok("Renamed");
    }

    @PostMapping("/api/section/delete")
    @ResponseBody
    public ResponseEntity<String> deleteSection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("id") Long id) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Admin is allowed to modify sections.");
        }
        accountManager.deleteSection(id);
        return ResponseEntity.ok("Deleted");
    }

    /**
     * AJAX Endpoint to save dashboard layout.
     * Expects JSON: { "sectionId1": [accId1, accId2], "sectionId2": [accId3] }
     */
    @PostMapping("/api/account/layout")
    @ResponseBody
    public ResponseEntity<String> saveLayout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, List<Object>> layoutData) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Admin is allowed to modify the layout.");
        }
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
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            return trades.stream()
                    .filter(t -> t.get("accountId") != null
                            && accountAccessService.canAccess(
                                    user, ((Number) t.get("accountId")).longValue()))
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
        if (userDetails != null) {
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            
            boolean hasConfig = !user.getRealAccountIds().isEmpty() || !user.getDemoAccountIds().isEmpty();
            
            if ("ROLE_ADMIN".equals(user.getRole())) {
                return drawdowns.stream()
                        .filter(d -> {
                            long physicalId = accountAccessService.getPhysicalAccountId(d.getAccountId());
                            return !hasConfig
                                    || user.getRealAccountIds().contains(d.getAccountId())
                                    || user.getRealAccountIds().contains(physicalId)
                                    || user.getDemoAccountIds().contains(d.getAccountId())
                                    || user.getDemoAccountIds().contains(physicalId);
                        })
                        .map(d -> {
                            long physicalId = accountAccessService.getPhysicalAccountId(d.getAccountId());
                            if (user.getRealAccountIds().contains(d.getAccountId())
                                    || user.getRealAccountIds().contains(physicalId)) {
                                d.setAccountType("REAL");
                                d.setReal(true);
                            } else if (user.getDemoAccountIds().contains(d.getAccountId())
                                    || user.getDemoAccountIds().contains(physicalId)) {
                                d.setAccountType("DEMO");
                                d.setReal(false);
                            }
                            return d;
                        })
                        .collect(Collectors.toList());
            } else {
                return drawdowns.stream()
                        .filter(d -> {
                            boolean isAllowed = accountAccessService.canAccess(user, d.getAccountId());
                            if (!hasConfig) return isAllowed;
                            long physicalId = accountAccessService.getPhysicalAccountId(d.getAccountId());
                            boolean isExplicit = user.getRealAccountIds().contains(d.getAccountId())
                                    || user.getRealAccountIds().contains(physicalId)
                                    || user.getDemoAccountIds().contains(d.getAccountId())
                                    || user.getDemoAccountIds().contains(physicalId);
                            return isAllowed && isExplicit;
                        })
                        .map(d -> {
                            long physicalId = accountAccessService.getPhysicalAccountId(d.getAccountId());
                            if (user.getRealAccountIds().contains(d.getAccountId())
                                    || user.getRealAccountIds().contains(physicalId)) {
                                d.setAccountType("REAL");
                                d.setReal(true);
                            } else if (user.getDemoAccountIds().contains(d.getAccountId())
                                    || user.getDemoAccountIds().contains(physicalId)) {
                                d.setAccountType("DEMO");
                                d.setReal(false);
                            }
                            return d;
                        })
                        .collect(Collectors.toList());
            }
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
        Map<Long, String> mappings = magicMappingService.getAllMappings();
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ClosedTrade ct : account.getClosedTrades()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ticket", ct.getTicket());
            row.put("symbol", ct.getSymbol());
            row.put("type", ct.getType());
            row.put("volume", ct.getVolume());
            row.put("openPrice", ct.getOpenPrice());
            row.put("closePrice", ct.getClosePrice());
            row.put("openTime", ct.getOpenTime());
            row.put("closeTime", ct.getCloseTime());
            row.put("profit", ct.getProfit());
            row.put("swap", ct.getSwap());
            row.put("commission", ct.getCommission());
            row.put("magicNumber", ct.getMagicNumber());
            row.put("comment", magicMappingService.resolveComment(ct.getMagicNumber(), ct.getComment(), mappings));
            row.put("sl", ct.getSl());
            row.put("openTimeMsc", ct.getOpenTimeMsc());
            row.put("closeTimeMsc", ct.getCloseTimeMsc());
            row.put("openAsk", ct.getOpenAsk());
            row.put("openBid", ct.getOpenBid());
            row.put("closeAsk", ct.getCloseAsk());
            row.put("closeBid", ct.getCloseBid());
            row.put("openOrderSetupTimeMsc", ct.getOpenOrderSetupTimeMsc());
            row.put("closeOrderSetupTimeMsc", ct.getCloseOrderSetupTimeMsc());
            row.put("openTicks", ct.getOpenTicks());
            row.put("closeTicks", ct.getCloseTicks());
            payload.add(row);
        }
        return ResponseEntity.ok(payload);
    }

    /**
     * Report generator: returns all closed trades for the selected accounts within
     * the given period (day/week/month), grouped per account with summary totals.
     * Used by the "Report Generator" dashboard tile.
     */
    @GetMapping("/api/report/closed-trades")
    @ResponseBody
    public ResponseEntity<?> getReportClosedTrades(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("accountIds") String accountIds,
            @RequestParam(value = "period", defaultValue = "day") String period,
            @RequestParam(value = "start", required = false) String start,
            @RequestParam(value = "end", required = false) String end) {

        // Determine period start/end (inclusive). closeTime format is "yyyy.MM.dd HH:mm:ss"
        // which sorts lexicographically, so a string comparison is sufficient.
        String fromStr;
        String toStr = null;
        String periodLabel;

        if ("custom".equalsIgnoreCase(period) && start != null && !start.isEmpty()) {
            fromStr = start.replace("-", ".") + " 00:00:00";
            if (end != null && !end.isEmpty()) {
                toStr = end.replace("-", ".") + " 23:59:59";
                periodLabel = "Benutzerdefinierter Report (" + start + " bis " + end + ")";
            } else {
                periodLabel = "Benutzerdefinierter Report (ab " + start + ")";
            }
        } else {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate startDate;
            switch (period == null ? "day" : period.toLowerCase()) {
                case "week":
                    startDate = today.with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1);
                    periodLabel = "Wochenreport";
                    break;
                case "month":
                    startDate = today.withDayOfMonth(1);
                    periodLabel = "Monatsreport";
                    break;
                case "year":
                    startDate = today.withDayOfYear(1);
                    periodLabel = "Jahresreport";
                    break;
                case "day":
                default:
                    startDate = today;
                    periodLabel = "Tagesreport";
                    break;
            }
            fromStr = startDate.toString().replace("-", ".") + " 00:00:00";
        }

        List<Long> requestedIds = new ArrayList<>();
        for (String part : accountIds.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try { requestedIds.add(Long.parseLong(trimmed)); } catch (NumberFormatException ignored) { }
            }
        }

        List<Map<String, Object>> accountReports = new ArrayList<>();
        double grandNet = 0.0;
        int grandCount = 0;

        for (Long accountId : requestedIds) {
            if (!isAllowedAccess(userDetails, accountId)) {
                continue;
            }
            Account account = accountManager.getAccount(accountId);
            if (account == null || account.getClosedTrades() == null) {
                continue;
            }
            double commissionFactor = account.getCommissionFactor();

            List<Map<String, Object>> trades = new ArrayList<>();
            double sumProfit = 0.0, sumCommission = 0.0, sumSwap = 0.0, sumNet = 0.0;

            final String finalFromStr = fromStr;
            final String finalToStr = toStr;
            List<ClosedTrade> sorted = account.getClosedTrades().stream()
                    .filter(ct -> ct.getCloseTime() != null 
                            && ct.getCloseTime().compareTo(finalFromStr) >= 0
                            && (finalToStr == null || ct.getCloseTime().compareTo(finalToStr) <= 0))
                    .sorted(Comparator.comparing(ClosedTrade::getCloseTime))
                    .collect(Collectors.toList());

            for (ClosedTrade ct : sorted) {
                double commission = ct.getCommission() * commissionFactor;
                double net = ct.getProfit() + ct.getSwap() + commission;
                sumProfit += ct.getProfit();
                sumCommission += commission;
                sumSwap += ct.getSwap();
                sumNet += net;

                Map<String, Object> t = new LinkedHashMap<>();
                t.put("ticket", ct.getTicket());
                t.put("symbol", ct.getSymbol());
                t.put("type", ct.getType());
                t.put("volume", ct.getVolume());
                t.put("openPrice", ct.getOpenPrice());
                t.put("closePrice", ct.getClosePrice());
                t.put("openTime", ct.getOpenTime());
                t.put("closeTime", ct.getCloseTime());
                t.put("profit", ct.getProfit());
                t.put("commission", commission);
                t.put("swap", ct.getSwap());
                t.put("net", net);
                t.put("magicNumber", ct.getMagicNumber());
                trades.add(t);
            }

            Map<String, Object> accReport = new LinkedHashMap<>();
            accReport.put("accountId", accountId);
            accReport.put("name", account.getName() != null && !account.getName().isEmpty()
                    ? account.getName() : String.valueOf(accountId));
            accReport.put("broker", account.getBroker() != null ? account.getBroker() : "");
            accReport.put("type", account.getType());
            accReport.put("currency", account.getCurrency());
            accReport.put("tradeCount", trades.size());
            accReport.put("totalProfit", sumProfit);
            accReport.put("totalCommission", sumCommission);
            accReport.put("totalSwap", sumSwap);
            accReport.put("netProfit", sumNet);
            accReport.put("trades", trades);
            accountReports.add(accReport);

            grandNet += sumNet;
            grandCount += trades.size();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodLabel", periodLabel);
        result.put("period", period);
        result.put("from", fromStr);
        if (toStr != null) {
            result.put("to", toStr);
        }
        result.put("generatedAt", java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        result.put("accounts", accountReports);
        result.put("grandNet", grandNet);
        result.put("grandCount", grandCount);
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
            String groupName = magicMappingService.resolveComment(magic,
                    trades.stream().map(de.trademonitor.model.Trade::getComment)
                            .filter(c -> c != null && !c.isBlank()).findFirst().orElse(null),
                    mappings);
            if (groupName.isBlank()) {
                groupName = "Magic " + magic;
            }
            groupSummary.put("name", groupName);
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

        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        model.addAttribute("isAdmin", isAdmin);

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
            @RequestParam(required = false) boolean triggerProfit,
            @RequestParam int repeatCount,
            @RequestParam(defaultValue = "5") int syncAlarmDelayMins,
            @RequestParam(defaultValue = "15") int homeyRepeatIntervalMins) {

        globalConfigService.saveHomeyConfig(homeyId, homeyEvent, triggerSync, triggerApi,
                triggerHealth, triggerSecurity, triggerOffline, triggerProfit, repeatCount, syncAlarmDelayMins, homeyRepeatIntervalMins);
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
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.ISO.dayOfWeek();
                startDt = today.with(fieldISO, 1);
                endDt = today.with(fieldISO, 7);
                break;
            case "monthly":
                startDt = today.withDayOfMonth(1);
                endDt = today.withDayOfMonth(today.lengthOfMonth());
                break;
            case "yearly":
                startDt = today.withDayOfYear(1);
                endDt = today.withDayOfYear(today.lengthOfYear());
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
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && accountAccessService.canAccess(user, id);
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
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.ISO.dayOfWeek();
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

        Map<Long, Double> commFactors = new HashMap<>();

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
                    double commissionFactor = commFactors.computeIfAbsent(trade.getAccountId(), id -> {
                        de.trademonitor.model.Account accountModel = accountManager.getAccount(id);
                        return accountModel != null ? accountModel.getCommissionFactor() : 1.0;
                    });
                    double netProfit = trade.getProfit() + trade.getSwap() + (trade.getCommission() * commissionFactor);
                    hourlyProfit[hour] += netProfit;
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
                    double commissionFactor = commFactors.computeIfAbsent(trade.getAccountId(), id -> {
                        de.trademonitor.model.Account accountModel = accountManager.getAccount(id);
                        return accountModel != null ? accountModel.getCommissionFactor() : 1.0;
                    });
                    double netProfit = trade.getProfit() + trade.getSwap() + (trade.getCommission() * commissionFactor);
                    dailyProfit[dayIndex] += netProfit;
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
                        double commissionFactor = commFactors.computeIfAbsent(trade.getAccountId(), id -> {
                            de.trademonitor.model.Account accountModel = accountManager.getAccount(id);
                            return accountModel != null ? accountModel.getCommissionFactor() : 1.0;
                        });
                        double netProfit = trade.getProfit() + trade.getSwap() + (trade.getCommission() * commissionFactor);
                        dailyProfit[day - 1] += netProfit;
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
                java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
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
            case "yearly":
                String yearStr = String.valueOf(today.getYear());
                return s -> s.getTimestamp() != null && s.getTimestamp().startsWith(yearStr);
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
                java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
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
            case "yearly":
                String yearStr = String.valueOf(today.getYear()) + ".";
                return date -> date.startsWith(yearStr);
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
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.ISO.dayOfWeek();
                startDt = today.with(fieldISO, 1);
                endDt = today.with(fieldISO, 7);
                break;
            case "monthly":
                startDt = today.withDayOfMonth(1);
                endDt = today.withDayOfMonth(today.lengthOfMonth());
                break;
            case "yearly":
                startDt = today.withDayOfYear(1);
                endDt = today.withDayOfYear(today.lengthOfYear());
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
        accounts = accounts.stream()
                .filter(acc -> !"CSV".equalsIgnoreCase((String) acc.get("type")))
                .collect(Collectors.toList());

        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && accountAccessService.canAccess(user, id);
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
                java.time.temporal.TemporalField fieldISO = java.time.temporal.WeekFields.ISO.dayOfWeek();
                startDt = today.with(fieldISO, 1);
                endDt = today.with(fieldISO, 7);
                periodTitle = "Wochenreport";
                break;
            case "monthly":
                startDt = today.withDayOfMonth(1);
                endDt = today.withDayOfMonth(today.lengthOfMonth());
                periodTitle = "Monatsreport";
                break;
            case "yearly":
                startDt = today.withDayOfYear(1);
                endDt = today.withDayOfYear(today.lengthOfYear());
                periodTitle = "Jahresreport";
                break;
            default:
                periodTitle = "Report (" + period + ")";
        }

        model.addAttribute("startDate", startDt.format(dateFormatter));
        model.addAttribute("endDate", endDt.format(dateFormatter));

        java.time.format.DateTimeFormatter tradeDateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd");
        String startCloseTime = startDt.format(tradeDateFormatter) + " 00:00:00";
        String endCloseTime = endDt.format(tradeDateFormatter) + " 23:59:59";

        java.util.function.Predicate<String> dateFilter = getDateFilter(period);

        for (Map<String, Object> acc : accounts) {
            Long accountId = (Long) acc.get("accountId");

            double closedProfit = 0.0;
            long closedCount = 0;
            de.trademonitor.model.Account accountModel = accountManager.getAccount(accountId);
            double commissionFactor = accountModel != null ? accountModel.getCommissionFactor() : 1.0;
            List<de.trademonitor.entity.ClosedTradeEntity> trades = closedTradeRepository.findByAccountIdAndDateRange(accountId, startCloseTime, endCloseTime);
            for (de.trademonitor.entity.ClosedTradeEntity ct : trades) {
                closedCount++;
                closedProfit += ct.getProfit() + ct.getSwap() + (ct.getCommission() * commissionFactor);
            }

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
            row.put("closedProfit", Math.round(closedProfit * 100.0) / 100.0);
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
        accounts = accounts.stream()
                .filter(acc -> !"CSV".equalsIgnoreCase((String) acc.get("type")))
                .collect(Collectors.toList());

        if (userDetails != null && !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && accountAccessService.canAccess(user, id);
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
            UserEntity currentUser = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            allAccounts = allAccounts.stream()
                    .filter(a -> accountAccessService.canAccess(currentUser, a.getAccountId()))
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
        model.addAttribute("isAdmin", isAdmin(userDetails));
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
        accounts = accounts.stream()
                .filter(acc -> !"CSV".equalsIgnoreCase((String) acc.get("type")))
                .collect(Collectors.toList());
        if (!isAdmin && userDetails != null) {
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            accounts = accounts.stream()
                    .filter(acc -> {
                        Long id = (Long) acc.get("accountId");
                        return id != null && accountAccessService.canAccess(user, id);
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
                    boolean monitored = Boolean.TRUE.equals(acc.get("monitored"));
                    
                    boolean statusOk = isOnline && !hasError && !hasSyncWarning && !hasAlarm;
                    // Unmonitored accounts are intentionally not watched: they must never
                    // contribute to the overall "Warnung / Fehler" state nor raise alarms.
                    if (isReal && monitored && !statusOk) {
                        accountsOkRef[0] = false;
                    }
                    
                    rAcc.put("statusOk", statusOk);
                    rAcc.put("monitored", monitored);
                    rAcc.put("online", isOnline);
                    rAcc.put("error", hasError);
                    rAcc.put("syncWarning", hasSyncWarning);
                    rAcc.put("alarm", hasAlarm);
                    rAcc.put("openTrades", acc.get("trades"));
                    rAcc.put("profit", acc.get("profit"));
                    rAcc.put("currency", acc.get("currency"));
                    rAcc.put("computerName", acc.get("computerName"));
                    rAcc.put("loginName", acc.get("loginName"));
                    rAcc.put("eaVersion", acc.get("eaVersion"));
                    rAcc.put("realAccountId", acc.get("realAccountId"));
                    
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
    public java.util.List<java.util.Map<String, Object>> getRecentLoginLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || !"ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            return java.util.Collections.emptyList();
        }
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
    public String eaLogs(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long accountId, Model model) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return "redirect:/";
        }
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
    public ResponseEntity<?> getEaLogsApi(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }
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
        return ResponseEntity.ok(result);
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
    public Map<String, Object> getHeatmapGlobal(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type) {
        return strategyAnalyticsService.buildHeatmap(null, type, analyticsAllowedIds(userDetails));
    }

    @GetMapping("/api/stats/heatmap/{accountId}")
    @ResponseBody
    public ResponseEntity<?> getHeatmap(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }
        return ResponseEntity.ok(strategyAnalyticsService.buildHeatmap(accountId, null));
    }

    /**
     * Strategy KPIs per magic number for a single account.
     */
    @GetMapping("/api/stats/strategy-kpis/{accountId}")
    @ResponseBody
    public ResponseEntity<?> getStrategyKpis(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }
        return ResponseEntity.ok(strategyAnalyticsService.getStrategyKpis(accountId));
    }

    /**
     * Global strategy leaderboard across all accounts.
     */
    @GetMapping("/api/stats/strategy-leaderboard")
    @ResponseBody
    public List<Map<String, Object>> getStrategyLeaderboard(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type) {
        return strategyAnalyticsService.getGlobalLeaderboard(type, analyticsAllowedIds(userDetails));
    }

    /**
     * Correlation matrix between account daily returns.
     */
    @GetMapping("/api/stats/correlation-matrix")
    @ResponseBody
    public Map<String, Object> getCorrelationMatrix(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period) {
        return strategyAnalyticsService.getCorrelationMatrix(type, period, analyticsAllowedIds(userDetails));
    }

    /**
     * Drawdown curves for all accounts.
     */
    @GetMapping("/api/stats/drawdown-curves")
    @ResponseBody
    public List<Map<String, Object>> getDrawdownCurves(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period) {
        return strategyAnalyticsService.getDrawdownCurves(type, period, analyticsAllowedIds(userDetails));
    }

    /**
     * Equity overlay curves for portfolio analytics.
     */
    @GetMapping("/api/stats/equity-overlay")
    @ResponseBody
    public List<Map<String, Object>> getEquityOverlay(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String period) {
        final Set<Long> allowedIds = analyticsAllowedIds(userDetails);
        List<Account> accounts = accountManager.getAccountsSortedByPrivilege().stream()
                .filter(a -> allowedIds == null || "CSV".equalsIgnoreCase(a.getType())
                        || allowedIds.contains(a.getAccountId()))
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

    @PostMapping("/api/trades/upload-csv")
    @ResponseBody
    public ResponseEntity<?> uploadCsvTradeList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file) {
        try {
            if (!isAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Nur der Admin darf CSV-Konten importieren."));
            }
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "CSV-Datei ist leer."));
            }
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Name darf nicht leer sein."));
            }
            if (file.getSize() > MAX_CSV_SIZE) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "CSV-Datei ist größer als 10 MB."));
            }
            String csvName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (!csvName.endsWith(".csv")) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Es sind nur CSV-Dateien erlaubt."));
            }

            // Parse trades
            List<ClosedTrade> trades = csvImportService.parseTradesCsv(file);
            if (trades.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Keine gültigen Trades in der CSV gefunden. Bitte Spalten prüfen."));
            }

            // Register account (or update existing one with same name)
            Long existingId = accountManager.findCsvAccountIdByName(name.trim());
            long accountId = (existingId != null) ? existingId : System.currentTimeMillis();
            accountManager.addCsvAccount(accountId, name.trim(), trades);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "accountId", accountId,
                "name", name.trim()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Fehler beim Import: " + e.getMessage()));
        }
    }

    @PostMapping("/api/user/news-accounts")
    @ResponseBody
    public ResponseEntity<String> saveUserNewsAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "accountIds", required = false, defaultValue = "") String accountIdsStr,
            @RequestParam(value = "colors", required = false, defaultValue = "") String colorsStr) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        Set<Long> accountIds = new HashSet<>();
        if (!accountIdsStr.trim().isEmpty()) {
            for (String part : accountIdsStr.split(",")) {
                try {
                    accountIds.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<Long, String> colors = new HashMap<>();
        if (!colorsStr.trim().isEmpty()) {
            for (String part : colorsStr.split(",")) {
                String[] split = part.split(":", 2);
                if (split.length == 2) {
                    try {
                        Long accId = Long.parseLong(split[0].trim());
                        String color = split[1].trim();
                        colors.put(accId, color);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        userService.updateNewsAccounts(userDetails.getUserEntity().getId(), accountIds, colors);
        userDetails.getUserEntity().setNewsAccountIds(accountIds);
        userDetails.getUserEntity().setNewsAccountColors(colors);
        return ResponseEntity.ok("Saved");
    }

    @GetMapping("/api/stats/news-today")
    @ResponseBody
    public ResponseEntity<?> getNewsToday(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                .orElse(userDetails.getUserEntity());

        Set<Long> selectedIds = user.getNewsAccountIds();
        boolean isAdmin = "ROLE_ADMIN".equals(user.getRole());
        List<Long> targetAccountIds = new ArrayList<>();
        if (selectedIds == null || selectedIds.isEmpty()) {
            for (de.trademonitor.model.Account acc : accountManager.getAllAccounts()) {
                if (acc.isMonitored() && (isAdmin || accountAccessService.canAccess(user, acc.getAccountId()))) {
                    targetAccountIds.add(acc.getAccountId());
                }
            }
        } else {
            for (Long id : selectedIds) {
                if (isAdmin || accountAccessService.canAccess(user, id)) {
                    targetAccountIds.add(id);
                }
            }
        }

        List<Map<String, Object>> openTrades = new ArrayList<>();
        List<Map<String, Object>> closedTradesToday = new ArrayList<>();
        double openProfit = 0.0;
        double closedProfitToday = 0.0;

        String todayStr = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));

        Map<Long, String> magicMappings = magicMappingService.getAllMappings();

        for (Long accId : targetAccountIds) {
            Account acc = accountManager.getAccount(accId);
            if (acc == null) continue;

            String accName = acc.getName() != null && !acc.getName().isEmpty() ? acc.getName() : String.valueOf(accId);

            // Open trades
            for (de.trademonitor.model.Trade t : acc.getOpenTrades()) {
                openProfit += t.getProfit();
                
                Map<String, Object> tMap = new LinkedHashMap<>();
                tMap.put("accountId", accId);
                tMap.put("accountName", accName);
                tMap.put("ticket", t.getTicket());
                tMap.put("symbol", t.getSymbol());
                tMap.put("type", t.getType());
                tMap.put("volume", t.getVolume());
                tMap.put("openPrice", t.getOpenPrice());
                tMap.put("openTime", t.getOpenTime());
                tMap.put("profit", t.getProfit());
                tMap.put("magicNumber", t.getMagicNumber());
                tMap.put("comment", magicMappingService.resolveComment(t.getMagicNumber(), t.getComment(), magicMappings));
                openTrades.add(tMap);
            }

            // Closed trades today
            double commissionFactor = acc.getCommissionFactor();
            for (ClosedTrade ct : acc.getClosedTrades()) {
                if (ct.getCloseTime() != null && ct.getCloseTime().startsWith(todayStr)) {
                    double tradeProfit = ct.getProfit() + ct.getSwap() + (ct.getCommission() * commissionFactor);
                    closedProfitToday += tradeProfit;

                    Map<String, Object> tMap = new LinkedHashMap<>();
                    tMap.put("accountId", accId);
                    tMap.put("accountName", accName);
                    tMap.put("ticket", ct.getTicket());
                    tMap.put("symbol", ct.getSymbol());
                    tMap.put("type", ct.getType());
                    tMap.put("volume", ct.getVolume());
                    tMap.put("openPrice", ct.getOpenPrice());
                    tMap.put("closePrice", ct.getClosePrice());
                    tMap.put("openTime", ct.getOpenTime());
                    tMap.put("closeTime", ct.getCloseTime());
                    tMap.put("profit", ct.getProfit());
                    tMap.put("swap", ct.getSwap());
                    tMap.put("commission", ct.getCommission());
                    tMap.put("netProfit", tradeProfit);
                    tMap.put("magicNumber", ct.getMagicNumber());
                    tMap.put("comment", magicMappingService.resolveComment(ct.getMagicNumber(), ct.getComment(), magicMappings));
                    closedTradesToday.add(tMap);
                }
            }
        }

        openTrades.sort((a, b) -> Long.compare((Long) b.get("ticket"), (Long) a.get("ticket")));
        closedTradesToday.sort((a, b) -> ((String) b.get("closeTime")).compareTo((String) a.get("closeTime")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("openTradesCount", openTrades.size());
        response.put("closedTradesTodayCount", closedTradesToday.size());
        response.put("openProfit", openProfit);
        response.put("closedProfitToday", closedProfitToday);
        response.put("openTrades", openTrades);
        response.put("closedTradesToday", closedTradesToday);
        response.put("selectedAccountIds", targetAccountIds);
        response.put("accountColors", user.getNewsAccountColors());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/trades/{ticket}/macro-history")
    @ResponseBody
    public ResponseEntity<?> getMacroHistory(
            @PathVariable("ticket") long ticket,
            @RequestParam("accountId") long accountId,
            @RequestParam(value = "range", defaultValue = "trade") String range,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }
        
        de.trademonitor.entity.ClosedTradeEntity trade = closedTradeRepository.findByAccountIdAndTicket(accountId, ticket).orElse(null);
        if (trade == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Trade not found"));
        }
        
        try {
            List<de.trademonitor.service.HistoricalRatesService.Candle> candles = 
                    historicalRatesService.getHistoricalRates(accountId, trade.getSymbol(), trade.getOpenTimeMsc(), trade.getCloseTimeMsc(), range, trade);
            return ResponseEntity.ok(candles);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
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

    @GetMapping("/api/trades/{ticket}/compare-ticks")
    @ResponseBody
    public ResponseEntity<?> getCompareTicks(
            @PathVariable("ticket") long ticket,
            @RequestParam("accountId") long accountId,
            @RequestParam("isOpen") boolean isOpen,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }
        
        de.trademonitor.entity.ClosedTradeEntity refTrade = closedTradeRepository.findByAccountIdAndTicket(accountId, ticket).orElse(null);
        if (refTrade == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Reference trade not found"));
        }
        
        long timeMsc = isOpen ? refTrade.getOpenTimeMsc() : refTrade.getCloseTimeMsc();
        String symbol = refTrade.getSymbol();
        
        // Find trades executed on the same symbol within +/- 2 minutes (120,000 ms)
        List<de.trademonitor.entity.ClosedTradeEntity> matchingTrades = closedTradeRepository.findTradesForComparison(
                symbol, isOpen, timeMsc - 120000, timeMsc + 120000);
        
        // Group by accountId and pick the one closest to timeMsc
        Map<Long, de.trademonitor.entity.ClosedTradeEntity> closestTradesByAccount = new HashMap<>();
        for (de.trademonitor.entity.ClosedTradeEntity t : matchingTrades) {
            if (!isAllowedAccess(userDetails, t.getAccountId())) {
                continue;
            }
            long tTime = isOpen ? t.getOpenTimeMsc() : t.getCloseTimeMsc();
            long diff = Math.abs(tTime - timeMsc);
            
            de.trademonitor.entity.ClosedTradeEntity existing = closestTradesByAccount.get(t.getAccountId());
            if (existing == null) {
                closestTradesByAccount.put(t.getAccountId(), t);
            } else {
                long existingTime = isOpen ? existing.getOpenTimeMsc() : existing.getCloseTimeMsc();
                if (diff < Math.abs(existingTime - timeMsc)) {
                    closestTradesByAccount.put(t.getAccountId(), t);
                }
            }
        }
        
        // Always include reference account in the DB trade mapping
        if (!closestTradesByAccount.containsKey(accountId)) {
            closestTradesByAccount.put(accountId, refTrade);
        }
        
        boolean loading = false;
        
        // Find all allowed account IDs for the user
        Collection<Account> allAllowedAccounts = new ArrayList<>();
        if ("ROLE_ADMIN".equals(userDetails.getUserEntity().getRole())) {
            allAllowedAccounts = accountManager.getAllAccounts();
        } else {
            UserEntity user = userService.getUserById(userDetails.getUserEntity().getId())
                    .orElse(userDetails.getUserEntity());
            allAllowedAccounts = accountManager.getAllAccounts().stream()
                    .filter(acc -> accountAccessService.canAccess(user, acc.getAccountId()))
                    .collect(Collectors.toList());
        }
        
        Account refAcc = accountManager.getAccount(accountId);
        long refOffset = refAcc != null && refAcc.getServerTimeOffsetSeconds() != null ? refAcc.getServerTimeOffsetSeconds() : 0L;
        
        // Window size is 2 seconds (2000 ms) before and after, centered around timeMsc
        long minTime = timeMsc - 2000;
        long maxTime = timeMsc + 2000;

        List<Map<String, Object>> brokersData = new ArrayList<>();
        
        for (Account acc : allAllowedAccounts) {
            long accId = acc.getAccountId();
            
            // Check if we have a trade in DB for this account
            de.trademonitor.entity.ClosedTradeEntity trade = closestTradesByAccount.get(accId);
            
            String ticksJson = "[]";
            double execPrice = 0.0;
            String tradeType = refTrade.getType();
            long execTime = timeMsc;
            boolean brokerLoading = false;
            long bOffset = acc.getServerTimeOffsetSeconds() != null ? acc.getServerTimeOffsetSeconds() : 0L;
            
            if (trade != null) {
                ticksJson = isOpen ? trade.getOpenTicks() : trade.getCloseTicks();
                if (ticksJson == null || ticksJson.trim().isEmpty()) {
                    ticksJson = "[]";
                }
                execPrice = isOpen ? trade.getOpenPrice() : trade.getClosePrice();
                tradeType = trade.getType();
                execTime = isOpen ? trade.getOpenTimeMsc() : trade.getCloseTimeMsc();
            } else {
                // No trade executed on this account around that time.
                // Is this account active? (lastSeen within last 60 seconds)
                boolean isActive = acc.getLastSeen() != null && 
                                   acc.getLastSeen().isAfter(java.time.LocalDateTime.now().minusSeconds(60));
                
                // Filter out accounts of type "CSV" (not real terminals)
                if ("CSV".equalsIgnoreCase(acc.getType())) {
                    isActive = false;
                }
                
                if (!isActive) {
                    continue; // Skip inactive brokers that didn't trade
                }
                
                // Active broker that didn't trade. Do we have ticks in cache?
                if (accountManager.isRequestedTicksCached(ticket, accId, isOpen)) {
                    ticksJson = accountManager.getCachedRequestedTicks(ticket, accId, isOpen);
                } else {
                    // Track when we first requested this
                    accountManager.trackRequestStart(ticket, accId, isOpen);
                    Long startTime = accountManager.getRequestStartTime(ticket, accId, isOpen);
                    
                    if (startTime != null && System.currentTimeMillis() - startTime > 15000) {
                        // Request timed out! Cache empty ticks so we stop requesting/loading
                        accountManager.cacheRequestedTicks(ticket, accId, isOpen, "[]");
                        accountManager.removePendingTickRequest(accId, ticket, isOpen);
                        accountManager.clearRequestTracking(ticket, accId, isOpen);
                        ticksJson = "[]";
                    } else {
                        // Still within timeout window. Check if it's already in the queue
                        de.trademonitor.service.AccountManager.PendingTickRequest existingReq = 
                                accountManager.getPendingTickRequest(accId, ticket, isOpen);
                        if (existingReq == null) {
                            // Queue a new request
                            accountManager.addPendingTickRequest(accId, new de.trademonitor.service.AccountManager.PendingTickRequest(
                                    ticket, symbol, minTime, maxTime, isOpen
                            ));
                        }
                        brokerLoading = true;
                        loading = true;
                    }
                }
                
                // No trade: set execTime to reference execution time adjusted by GMT offset difference
                long offsetDiffMs = (bOffset - refOffset) * 1000;
                execTime = timeMsc + offsetDiffMs;
                execPrice = refTrade.getOpenPrice(); // Use reference price as placeholder
            }
            
            Map<String, Object> brokerMap = new HashMap<>();
            brokerMap.put("accountId", accId);
            brokerMap.put("broker", acc.getBroker() != null ? acc.getBroker() : "Unknown");
            brokerMap.put("accountName", acc.getName() != null ? acc.getName() : "Acc #" + accId);
            brokerMap.put("type", acc.getType() != null ? acc.getType() : "DEMO");
            brokerMap.put("execTimeMsc", execTime);
            brokerMap.put("execPrice", execPrice);
            brokerMap.put("tradeType", tradeType);
            brokerMap.put("ticksJson", ticksJson);
            brokerMap.put("loading", brokerLoading);
            brokerMap.put("gmtOffset", bOffset);
            
            brokersData.add(brokerMap);
        }
        
        brokersData.sort((a, b) -> {
            long aId = (Long) a.get("accountId");
            long bId = (Long) b.get("accountId");
            if (aId == accountId) return -1;
            if (bId == accountId) return 1;
            int brokerCmp = ((String) a.get("broker")).compareToIgnoreCase((String) b.get("broker"));
            if (brokerCmp != 0) return brokerCmp;
            return ((String) a.get("accountName")).compareToIgnoreCase((String) b.get("accountName"));
        });
        
        Map<String, Object> response = new HashMap<>();
        
        Map<String, Object> refMap = new HashMap<>();
        refMap.put("ticket", ticket);
        refMap.put("accountId", accountId);
        refMap.put("broker", (refAcc != null) ? refAcc.getBroker() : "Unknown");
        refMap.put("accountName", (refAcc != null) ? refAcc.getName() : "Acc #" + accountId);
        refMap.put("symbol", symbol);
        refMap.put("execTimeMsc", timeMsc);
        refMap.put("execPrice", isOpen ? refTrade.getOpenPrice() : refTrade.getClosePrice());
        refMap.put("tradeType", refTrade.getType());
        refMap.put("isOpen", isOpen);
        refMap.put("ticksJson", isOpen ? refTrade.getOpenTicks() : refTrade.getCloseTicks());
        refMap.put("gmtOffset", refOffset);
        
        response.put("referenceTrade", refMap);
        response.put("brokers", brokersData);
        response.put("loading", loading);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/siren-log")
    @ResponseBody
    public ResponseEntity<String> getSirenLog(@AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Zugriff verweigert: Nur Administratoren dürfen das Sirenen-Log einsehen.");
        }
        
        try {
            java.nio.file.Path logPath = java.nio.file.Path.of("/opt/wildfly/trademonitor_data/notifications.log");
            if (!java.nio.file.Files.exists(logPath)) {
                return ResponseEntity.ok("Keine Log-Einträge vorhanden.");
            }
            String content = java.nio.file.Files.readString(logPath);
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Fehler beim Lesen der Log-Datei: " + e.getMessage());
        }
    }

    @PostMapping("/api/siren-log/clear")
    @ResponseBody
    public ResponseEntity<String> clearSirenLog(@AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdmin = userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Zugriff verweigert.");
        }
        
        try {
            java.nio.file.Path logPath = java.nio.file.Path.of("/opt/wildfly/trademonitor_data/notifications.log");
            if (java.nio.file.Files.exists(logPath)) {
                java.nio.file.Files.delete(logPath);
            }
            return ResponseEntity.ok("Log-Datei erfolgreich gelöscht.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Fehler beim Löschen der Log-Datei: " + e.getMessage());
        }
    }

    @GetMapping("/account/{accountId}/info-area")
    public String getAccountInfo(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId, Model model) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return "redirect:/";
        }
        Account account = accountManager.getAccount(accountId);
        if (account == null) {
            return "redirect:/";
        }
        model.addAttribute("account", account);
        model.addAttribute("documents", accountDocumentRepository.findAllProjectedByAccountId(accountId));
        model.addAttribute("links", accountLinkRepository.findAllByAccountId(accountId));
        return "account-info";
    }

    @PostMapping("/api/account/{accountId}/info-text")
    @ResponseBody
    public ResponseEntity<?> saveAccountInfoText(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId, @RequestParam("infoText") String infoText) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        de.trademonitor.entity.AccountEntity ae = accountRepository.findById(accountId).orElse(null);
        if (ae == null) {
            return ResponseEntity.notFound().build();
        }
        ae.setInfoText(infoText);
        accountRepository.save(ae);
        
        // Also update runtime domain model in AccountManager
        Account account = accountManager.getAccount(accountId);
        if (account != null) {
            account.setInfoText(infoText);
        }
        return ResponseEntity.ok("Saved successfully");
    }

    @PostMapping("/api/account/{accountId}/resource-order")
    @ResponseBody
    public ResponseEntity<?> saveAccountResourceOrder(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId, @RequestParam("resourceOrder") String resourceOrder) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        de.trademonitor.entity.AccountEntity ae = accountRepository.findById(accountId).orElse(null);
        if (ae == null) {
            return ResponseEntity.notFound().build();
        }
        ae.setResourceOrder(resourceOrder);
        accountRepository.save(ae);
        
        // Also update runtime domain model in AccountManager
        Account account = accountManager.getAccount(accountId);
        if (account != null) {
            account.setResourceOrder(resourceOrder);
        }
        return ResponseEntity.ok("Resource order saved successfully");
    }

    @PostMapping("/api/account/{accountId}/documents")
    @ResponseBody
    public ResponseEntity<?> uploadDocument(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("minText") String minText) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Datei ist leer");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            return ResponseEntity.badRequest().body("Datei ist größer als 10 MB");
        }
        try {
            byte[] fileData = file.getBytes();
            String fileName = sanitizeFileName(file.getOriginalFilename());
            String contentType = detectAllowedDocumentType(fileName, fileData);
            if (contentType == null) {
                return ResponseEntity.badRequest().body(
                        "Nicht erlaubter Dateityp. Erlaubt sind PDF, TXT, Markdown, PNG, JPEG, GIF und WebP.");
            }
            de.trademonitor.entity.AccountDocumentEntity doc = new de.trademonitor.entity.AccountDocumentEntity(
                accountId, fileName, minText, contentType, fileData.length, fileData
            );
            accountDocumentRepository.save(doc);
            return ResponseEntity.ok("Erfolgreich hochgeladen");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload fehlgeschlagen: " + e.getMessage());
        }
    }

    @DeleteMapping("/api/account/documents/{documentId}")
    @ResponseBody
    public ResponseEntity<?> deleteDocument(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long documentId) {
        de.trademonitor.entity.AccountDocumentEntity doc = accountDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAllowedAccess(userDetails, doc.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        accountDocumentRepository.delete(doc);
        return ResponseEntity.ok("Erfolgreich gelöscht");
    }

    @GetMapping("/api/account/documents/{documentId}/view")
    public ResponseEntity<?> viewDocument(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long documentId) {
        de.trademonitor.entity.AccountDocumentEntity doc = accountDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAllowedAccess(userDetails, doc.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        String storedType = doc.getContentType();
        boolean inline = isSafeInlineDocumentType(storedType);
        String responseType = inline ? storedType : "application/octet-stream";
        org.springframework.http.ContentDisposition disposition = (inline
                ? org.springframework.http.ContentDisposition.inline()
                : org.springframework.http.ContentDisposition.attachment())
                .filename(sanitizeFileName(doc.getFileName()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, responseType)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(doc.getFileData());
    }

    @PostMapping("/api/account/{accountId}/links")
    @ResponseBody
    public ResponseEntity<?> addLink(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long accountId, @RequestParam("url") String url, @RequestParam("minText") String minText) {
        if (!isAllowedAccess(userDetails, accountId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        if (url == null || url.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("URL darf nicht leer sein");
        }
        String normalizedUrl = url.trim();
        if (!normalizedUrl.matches("(?i)^https?://.*")) normalizedUrl = "https://" + normalizedUrl;
        try {
            java.net.URI parsed = java.net.URI.create(normalizedUrl);
            if (parsed.getHost() == null || !("http".equalsIgnoreCase(parsed.getScheme())
                    || "https".equalsIgnoreCase(parsed.getScheme()))) {
                return ResponseEntity.badRequest().body("Es sind nur gültige HTTP- oder HTTPS-URLs erlaubt");
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Ungültige URL");
        }
        de.trademonitor.entity.AccountLinkEntity link = new de.trademonitor.entity.AccountLinkEntity(accountId, normalizedUrl, minText);
        accountLinkRepository.save(link);
        return ResponseEntity.ok("Link erfolgreich hinzugefügt");
    }

    @DeleteMapping("/api/account/links/{linkId}")
    @ResponseBody
    public ResponseEntity<?> deleteLink(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long linkId) {
        de.trademonitor.entity.AccountLinkEntity link = accountLinkRepository.findById(linkId).orElse(null);
        if (link == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAllowedAccess(userDetails, link.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        accountLinkRepository.delete(link);
        return ResponseEntity.ok("Link erfolgreich gelöscht");
    }

    @PostMapping("/api/account/documents/{documentId}/description")
    @ResponseBody
    public ResponseEntity<?> updateDocumentDescription(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long documentId, @RequestParam("description") String description) {
        de.trademonitor.entity.AccountDocumentEntity doc = accountDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAllowedAccess(userDetails, doc.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        doc.setMinText(description);
        accountDocumentRepository.save(doc);
        return ResponseEntity.ok("Beschreibung aktualisiert");
    }

    @PostMapping("/api/account/links/{linkId}/description")
    @ResponseBody
    public ResponseEntity<?> updateLinkDescription(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable long linkId, @RequestParam("description") String description) {
        de.trademonitor.entity.AccountLinkEntity link = accountLinkRepository.findById(linkId).orElse(null);
        if (link == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isAllowedAccess(userDetails, link.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
        }
        link.setMinText(description);
        accountLinkRepository.save(link);
        return ResponseEntity.ok("Beschreibung aktualisiert");
    }

}

