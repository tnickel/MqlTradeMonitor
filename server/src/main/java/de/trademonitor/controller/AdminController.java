package de.trademonitor.controller;

import de.trademonitor.repository.LoginLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private LoginLogRepository loginLogRepository;

    @Autowired
    private de.trademonitor.repository.ClientLogRepository clientLogRepository;

    @Autowired
    private de.trademonitor.repository.AccountRepository accountRepository;

    @Autowired
    private de.trademonitor.repository.OpenTradeRepository openTradeRepository;

    @Autowired
    private de.trademonitor.repository.ClosedTradeRepository closedTradeRepository;

    @Autowired
    private de.trademonitor.service.GlobalConfigService globalConfigService;

    @Autowired
    private de.trademonitor.service.MagicMappingService magicMappingService;

    @GetMapping("")
    public String adminDashboard(Model model) {
        // --- 1. Admin Stats (migrated from DashboardController) ---
        java.util.List<de.trademonitor.entity.AccountEntity> accounts = accountRepository.findAll();
        java.util.List<de.trademonitor.dto.AccountDbStats> statsList = new java.util.ArrayList<>();

        long totalOpen = 0;
        long totalClosed = 0;

        for (de.trademonitor.entity.AccountEntity acc : accounts) {
            de.trademonitor.dto.AccountDbStats stats = new de.trademonitor.dto.AccountDbStats();
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
            java.util.List<de.trademonitor.entity.ClosedTradeEntity> closedTrades = closedTradeRepository
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

        // --- 2. Configuration ---
        model.addAttribute("magicMaxAgeByConfig", globalConfigService.getMagicNumberMaxAge());
        model.addAttribute("tradeSyncInterval", globalConfigService.getTradeSyncIntervalSeconds());

        // Live Indicator Config
        model.addAttribute("liveGreenMins", globalConfigService.getLiveGreenMins());
        model.addAttribute("liveYellowMins", globalConfigService.getLiveYellowMins());
        model.addAttribute("liveOrangeMins", globalConfigService.getLiveOrangeMins());
        model.addAttribute("liveColorGreen", globalConfigService.getLiveColorGreen());
        model.addAttribute("liveColorYellow", globalConfigService.getLiveColorYellow());
        model.addAttribute("liveColorOrange", globalConfigService.getLiveColorOrange());
        model.addAttribute("liveColorRed", globalConfigService.getLiveColorRed());

        // Mail Config
        model.addAttribute("mailHost", globalConfigService.getMailHost());
        model.addAttribute("mailPort", globalConfigService.getMailPort());
        model.addAttribute("mailUser", globalConfigService.getMailUser());
        model.addAttribute("mailPassword", globalConfigService.getMailPassword());
        model.addAttribute("mailFrom", globalConfigService.getMailFrom());
        model.addAttribute("mailTo", globalConfigService.getMailTo());
        model.addAttribute("mailMaxPerDay", globalConfigService.getMailMaxPerDay());

        // Log Retention Config
        model.addAttribute("logLoginDays", globalConfigService.getLogLoginDays());
        model.addAttribute("logConnDays", globalConfigService.getLogConnDays());
        model.addAttribute("logClientDays", globalConfigService.getLogClientDays());

        // Homey Config
        model.addAttribute("homeyId", globalConfigService.getHomeyId());
        model.addAttribute("homeyEvent", globalConfigService.getHomeyEvent());
        model.addAttribute("homeyTriggerSync", globalConfigService.isHomeyTriggerSync());
        model.addAttribute("homeyTriggerApi", globalConfigService.isHomeyTriggerApi());
        model.addAttribute("homeyRepeatCount", globalConfigService.getHomeyRepeatCount());

        // --- 3. Magic Mappings ---
        java.util.Set<Long> allMagics = new java.util.HashSet<>();
        closedTradeRepository.findAll().forEach(t -> allMagics.add(t.getMagicNumber()));
        openTradeRepository.findAll().forEach(t -> allMagics.add(t.getMagicNumber()));

        magicMappingService.ensureMappingsExist(new java.util.ArrayList<>(allMagics), magic -> {
            String name = openTradeRepository.findFirstByMagicNumber(magic)
                    .map(de.trademonitor.entity.OpenTradeEntity::getComment).orElse(null);
            if (name == null) {
                name = closedTradeRepository.findFirstByMagicNumber(magic)
                        .map(de.trademonitor.entity.ClosedTradeEntity::getComment).orElse(null);
            }
            return name;
        });

        model.addAttribute("magicMappings", magicMappingService.getAllMappings());

        // Sync Exemptions
        java.util.Set<Long> exemptSet = globalConfigService.getSyncExemptMagicNumbers();
        String exemptStr = exemptSet.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        model.addAttribute("syncExemptMagics", exemptStr);

        return "admin";
    }

    @PostMapping("/sync-exemptions")
    public String saveSyncExemptions(@RequestParam(defaultValue = "") String magicNumbers) {
        java.util.Set<Long> result = new java.util.LinkedHashSet<>();
        for (String part : magicNumbers.split("[,\\s]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    result.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        globalConfigService.setSyncExemptMagicNumbers(result);
        return "redirect:/admin";
    }

    @PostMapping("/live-config")
    public String saveLiveConfig(
            @RequestParam int liveGreenMins,
            @RequestParam int liveYellowMins,
            @RequestParam int liveOrangeMins,
            @RequestParam String liveColorGreen,
            @RequestParam String liveColorYellow,
            @RequestParam String liveColorOrange,
            @RequestParam String liveColorRed) {

        globalConfigService.saveLiveIndicatorConfig(
                liveGreenMins, liveYellowMins, liveOrangeMins,
                liveColorGreen, liveColorYellow, liveColorOrange, liveColorRed);
        return "redirect:/admin";
    }

    @PostMapping("/log-retention")
    public String saveLogRetention(
            @RequestParam int logLoginDays,
            @RequestParam int logConnDays,
            @RequestParam int logClientDays) {
        globalConfigService.saveLogRetentionConfig(logLoginDays, logConnDays, logClientDays);
        return "redirect:/admin";
    }

    @Autowired
    private de.trademonitor.repository.RequestLogRepository requestLogRepository;

    @GetMapping("/logs")
    public String viewLogs(Model model) {
        model.addAttribute("logs", loginLogRepository.findAllByOrderByTimestampDesc());
        return "admin-logs";
    }

    @GetMapping("/requests")
    public String viewRequests(Model model) {
        model.addAttribute("requests", requestLogRepository.findAllByOrderByTimestampDesc());
        return "admin-requests";
    }

    @GetMapping("/client-logs")
    public String viewClientLogs(@org.springframework.web.bind.annotation.RequestParam(required = false) Long accountId,
            Model model) {
        if (accountId != null) {
            model.addAttribute("logs", clientLogRepository.findByAccountIdOrderByTimestampDesc(accountId));
            model.addAttribute("filterAccountId", accountId);
        } else {
            model.addAttribute("logs", clientLogRepository.findAllByOrderByTimestampDesc());
        }
        return "admin-client-logs";
    }
}
