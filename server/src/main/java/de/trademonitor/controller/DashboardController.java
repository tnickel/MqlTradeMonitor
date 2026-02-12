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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
    private de.trademonitor.service.MagicMappingService magicMappingService;

    /**
     * Main dashboard showing all accounts.
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("accounts", accountManager.getAccountsWithStatus());
        model.addAttribute("timeoutSeconds", accountManager.getTimeoutSeconds());
        return "dashboard";
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
    @org.springframework.web.bind.annotation.PostMapping("/admin/mapping")
    public String updateMapping(@org.springframework.web.bind.annotation.RequestParam("magicNumber") Long magicNumber,
            @org.springframework.web.bind.annotation.RequestParam("customComment") String customComment) {
        magicMappingService.saveMapping(magicNumber, customComment);
        return "redirect:/admin";
    }

    /**
     * AJAX Endpoint to update magic mapping.
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/mapping")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<String> updateMappingAjax(
            @org.springframework.web.bind.annotation.RequestParam("magicNumber") Long magicNumber,
            @org.springframework.web.bind.annotation.RequestParam("customComment") String customComment) {
        magicMappingService.saveMapping(magicNumber, customComment);
        return org.springframework.http.ResponseEntity.ok("Saved");
    }

    /**
     * Update global configuration.
     */
    @org.springframework.web.bind.annotation.PostMapping("/admin/config")
    public String updateConfig(
            @org.springframework.web.bind.annotation.RequestParam("magicNumberMaxAge") int magicNumberMaxAge) {
        globalConfigService.setMagicNumberMaxAge(magicNumberMaxAge);
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

}
