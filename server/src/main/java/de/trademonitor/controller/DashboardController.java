package de.trademonitor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.trademonitor.dto.AccountDbStats;
import de.trademonitor.entity.AccountEntity;
import de.trademonitor.model.Account;
import de.trademonitor.model.ClosedTrade;
import de.trademonitor.repository.AccountRepository;
import de.trademonitor.repository.ClosedTradeRepository;
import de.trademonitor.repository.OpenTradeRepository;
import de.trademonitor.service.AccountManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for web dashboard views.
 */
@Controller
public class DashboardController {

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClosedTradeRepository closedTradeRepository;

    @Autowired
    private OpenTradeRepository openTradeRepository;

    @Autowired
    private de.trademonitor.service.GlobalConfigService globalConfigService;

    @Autowired
    private de.trademonitor.service.TradeSyncService tradeSyncService;

    @Autowired
    private de.trademonitor.service.MagicMappingService magicMappingService;

    /**
     * Main dashboard showing all accounts.
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        List<java.util.Map<String, Object>> allAccounts = accountManager.getAccountsWithStatus();
        List<de.trademonitor.entity.DashboardSectionEntity> sections = accountManager.getAllSections();

        // Organize accounts by sectionId
        Map<Long, List<java.util.Map<String, Object>>> accountsBySection = new LinkedHashMap<>();

        // Initialize map with empty lists for all sections to ensure they exist
        for (de.trademonitor.entity.DashboardSectionEntity sec : sections) {
            accountsBySection.put(sec.getId(), new ArrayList<>());
        }

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
        // Keep "accounts" for backward compatibility if needed for totals
        model.addAttribute("accounts", allAccounts);

        model.addAttribute("syncMetrics", tradeSyncService.getMetrics());

        model.addAttribute("timeoutSeconds", accountManager.getTimeoutSeconds());
        return "dashboard";
    }

    @GetMapping("/open-trades")
    public String openTradesPage(Model model) {
        List<de.trademonitor.model.Account> sortedAccounts = accountManager.getAccountsSortedByPrivilege();
        model.addAttribute("accounts", sortedAccounts);

        // Calculate Totals
        int totalTrades = 0;
        double totalEquity = 0;
        double totalProfit = 0;
        String currency = "EUR"; // Default fallback

        for (de.trademonitor.model.Account acc : sortedAccounts) {
            totalTrades += acc.getOpenTrades().size();
            // Only sum online accounts or all? Usually all is safer for "current state"
            // But if account is offline, equity might be stale. Decision: Show all.
            totalEquity += acc.getEquity();
            totalProfit += acc.getTotalProfit();
            if (acc.getCurrency() != null && !acc.getCurrency().isEmpty()) {
                currency = acc.getCurrency();
            }
        }

        model.addAttribute("totalTrades", totalTrades);
        model.addAttribute("totalEquity", totalEquity);
        model.addAttribute("totalProfit", totalProfit);
        model.addAttribute("currency", currency);

        return "open-trades";
    }

    /**
     * Admin overview showing database statistics per account.
     */
    @GetMapping("/admin")
    public String admin(Model model) {
        List<AccountEntity> accounts = accountRepository.findAll();
        List<AccountDbStats> statsList = new ArrayList<>();

        long totalOpen = 0;
        long totalClosed = 0;

        for (AccountEntity acc : accounts) {
            AccountDbStats stats = new AccountDbStats();
            stats.setAccountId(acc.getAccountId());
            stats.setBroker(acc.getBroker());
            stats.setCurrency(acc.getCurrency());

            long openCount = openTradeRepository.countByAccountId(acc.getAccountId());
            long closedCount = closedTradeRepository.countByAccountId(acc.getAccountId());
            stats.setOpenTradeCount(openCount);
            stats.setClosedTradeCount(closedCount);

            String minDate = closedTradeRepository.findMinCloseTimeByAccountId(acc.getAccountId());
            String maxDate = closedTradeRepository.findMaxCloseTimeByAccountId(acc.getAccountId());
            stats.setEarliestTradeDate(minDate != null ? minDate : "-");
            stats.setLatestTradeDate(maxDate != null ? maxDate : "-");

            // Sum up total profit from closed trades
            List<de.trademonitor.entity.ClosedTradeEntity> closedTrades = closedTradeRepository
                    .findByAccountId(acc.getAccountId());
            double totalProfit = closedTrades.stream().mapToDouble(t -> t.getProfit()).sum();
            stats.setTotalProfit(totalProfit);

            totalOpen += openCount;
            totalClosed += closedCount;

            statsList.add(stats);
        }

        model.addAttribute("statsList", statsList);
        model.addAttribute("totalAccounts", accounts.size());
        model.addAttribute("totalOpenTrades", totalOpen);
        model.addAttribute("totalClosedTrades", totalClosed);

        // Add global config
        model.addAttribute("magicMaxAgeByConfig", globalConfigService.getMagicNumberMaxAge());
        model.addAttribute("magicMaxAgeByConfig", globalConfigService.getMagicNumberMaxAge());
        model.addAttribute("tradeSyncInterval", globalConfigService.getTradeSyncIntervalSeconds());

        // Mail Config
        model.addAttribute("mailHost", globalConfigService.getMailHost());
        model.addAttribute("mailPort", globalConfigService.getMailPort());
        model.addAttribute("mailUser", globalConfigService.getMailUser());
        model.addAttribute("mailPassword", globalConfigService.getMailPassword());
        model.addAttribute("mailFrom", globalConfigService.getMailFrom());
        model.addAttribute("mailTo", globalConfigService.getMailTo());
        model.addAttribute("mailMaxPerDay", globalConfigService.getMailMaxPerDay());

        // Magic Mappings
        // 1. Collect all magic numbers from DB (Closed + Open) to ensure we have
        // mappings for all
        Set<Long> allMagics = new HashSet<>();
        closedTradeRepository.findAll().forEach(t -> allMagics.add(t.getMagicNumber()));
        openTradeRepository.findAll().forEach(t -> allMagics.add(t.getMagicNumber()));

        // 2. Ensure mappings exist (auto-create with default comment from trades)
        magicMappingService.ensureMappingsExist(new ArrayList<>(allMagics), magic -> {
            // Try to find a name from existing trades
            String name = openTradeRepository.findFirstByMagicNumber(magic)
                    .map(de.trademonitor.entity.OpenTradeEntity::getComment).orElse(null);
            if (name == null) {
                name = closedTradeRepository.findFirstByMagicNumber(magic)
                        .map(de.trademonitor.entity.ClosedTradeEntity::getComment).orElse(null);
            }
            return name;
        });

        // 3. Load all mappings for UI
        model.addAttribute("magicMappings", magicMappingService.getAllMappings());

        return "admin";
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
     * AJAX Endpoint to update account details (name, type).
     */
    @PostMapping("/api/account/update")
    @ResponseBody
    public ResponseEntity<String> updateAccountDetails(
            @RequestParam("accountId") Long accountId,
            @RequestParam("name") String name,
            @RequestParam("type") String type) {
        accountManager.updateAccountDetails(accountId, name, type);
        return ResponseEntity.ok("Saved");
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
        for (Map.Entry<String, List<Object>> entry : layoutData.entrySet()) {
            try {
                Long sectionId = Long.parseLong(entry.getKey());
                List<Object> rawIds = entry.getValue();

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
                                /* ignore */ }
                        }

                        if (accountId != null) {
                            accountManager.saveAccountSection(accountId, sectionId, i);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // ignore invalid keys
            }
        }
        return ResponseEntity.ok("Layout saved");
    }

    /**
     * AJAX Endpoint to get all open trades.
     */
    @org.springframework.web.bind.annotation.GetMapping("/api/trades/open")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.List<java.util.Map<String, Object>> getOpenTrades() {
        return accountManager.getAllOpenTradesSorted();
    }

    /**
     * Update global configuration.
     */
    @org.springframework.web.bind.annotation.PostMapping("/admin/config")
    public String updateConfig(
            @org.springframework.web.bind.annotation.RequestParam("magicNumberMaxAge") int magicNumberMaxAge,
            @org.springframework.web.bind.annotation.RequestParam("tradeSyncInterval") int tradeSyncInterval) {
        globalConfigService.setMagicNumberMaxAge(magicNumberMaxAge);
        globalConfigService.setTradeSyncIntervalSeconds(tradeSyncInterval);
        return "redirect:/admin";
    }

    /**
     * Detail view for a specific account.
     */
    @GetMapping("/account/{accountId}")
    public String accountDetail(@PathVariable long accountId, Model model) {
        Account account = accountManager.getAccount(accountId);
        if (account == null) {
            return "redirect:/";
        }
        model.addAttribute("account", account);
        model.addAttribute("online", account.isOnline(accountManager.getTimeoutSeconds()));

        int maxAge = globalConfigService.getMagicNumberMaxAge();
        Map<Long, String> mappings = magicMappingService.getAllMappings();

        // Pass resolver to getMagicProfitEntries
        model.addAttribute("magicProfits", account.getMagicProfitEntries(maxAge, mappings::get));
        model.addAttribute("magicMaxAge", maxAge);

        // Build magic curve data as JSON for Chart.js
        model.addAttribute("magicCurveJson", buildMagicCurveJson(account, maxAge, mappings));

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

}
