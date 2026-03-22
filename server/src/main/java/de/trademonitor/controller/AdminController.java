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
    private de.trademonitor.repository.ClientActionCounterRepository clientActionCounterRepository;

    @Autowired
    private de.trademonitor.repository.ClientErrorLogRepository clientErrorLogRepository;

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

    @Autowired
    private de.trademonitor.service.UserService userService;

    @Autowired
    private de.trademonitor.service.SecurityAuditService securityAuditService;

    @Autowired
    private de.trademonitor.service.AdminNotificationService adminNotificationService;

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
        model.addAttribute("syncAlarmDelayMins", globalConfigService.getSyncAlarmDelayMins());

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

        // --- 4. Users ---
        model.addAttribute("usersList", userService.getAllUsers());
        model.addAttribute("accountsList", accountRepository.findAll()); // for assigning in UI

        // --- 5. Security Config ---
        model.addAttribute("secRateLimitEnabled", globalConfigService.isSecRateLimitEnabled());
        model.addAttribute("secRateLimitPerMin", globalConfigService.getSecRateLimitPerMin());
        model.addAttribute("secBruteForceEnabled", globalConfigService.isSecBruteForceEnabled());
        model.addAttribute("secBruteForceMaxAttempts", globalConfigService.getSecBruteForceMaxAttempts());
        model.addAttribute("secBruteForceLockoutMins", globalConfigService.getSecBruteForceLockoutMins());
        model.addAttribute("secHeadersEnabled", globalConfigService.isSecHeadersEnabled());
        model.addAttribute("secMaxSessions", globalConfigService.getSecMaxSessions());
        model.addAttribute("secH2ConsoleEnabled", globalConfigService.isSecH2ConsoleEnabled());

        // --- 6. Broker Commission Factors ---
        model.addAttribute("brokerCommFactors", globalConfigService.getAllBrokerCommFactors());

        // --- 7. Admin Notifications ---
        model.addAttribute("adminNotifications", adminNotificationService.getUnacknowledgedNotifications());
        model.addAttribute("notificationCount", adminNotificationService.getUnacknowledgedCount());

        return "admin";
    }

    @PostMapping("/create-user")
    public String createUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String role,
            @RequestParam(required = false) String allowedAccountIds,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            java.util.Set<Long> accountIds = new java.util.HashSet<>();
            if (allowedAccountIds != null && !allowedAccountIds.trim().isEmpty()) {
                if ("NONE".equalsIgnoreCase(allowedAccountIds.trim())) {
                    // Empty set means no access
                } else {
                    for (String idStr : allowedAccountIds.split(",")) {
                        try {
                            accountIds.add(Long.parseLong(idStr.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            userService.createUser(username, password, role, accountIds);
            redirectAttrs.addFlashAttribute("successMessage", "User '" + username + "' created.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Error creating user: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/update-user-accounts")
    public String updateUserAccounts(
            @RequestParam Long userId,
            @RequestParam(required = false) String allowedAccountIds,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            de.trademonitor.entity.UserEntity existingUser = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            java.util.Set<Long> accountIds = new java.util.HashSet<>();
            if (allowedAccountIds != null && !allowedAccountIds.trim().isEmpty()) {
                if ("NONE".equalsIgnoreCase(allowedAccountIds.trim())) {
                    // Empty set means no access
                } else {
                    for (String idStr : allowedAccountIds.split(",")) {
                        try {
                            accountIds.add(Long.parseLong(idStr.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            userService.updateUser(userId, existingUser.getRole(), accountIds);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Accounts for user '" + existingUser.getUsername() + "' updated.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Error updating user accounts: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/delete-user")
    public String deleteUser(@RequestParam Long userId,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            userService.deleteUser(userId);
            redirectAttrs.addFlashAttribute("successMessage", "User deleted.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Error deleting user: " + e.getMessage());
        }
        return "redirect:/admin";
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

    @PostMapping("/security")
    public String saveSecurityConfig(
            @RequestParam(required = false) String secRateLimitEnabled,
            @RequestParam(defaultValue = "100") int secRateLimitPerMin,
            @RequestParam(required = false) String secBruteForceEnabled,
            @RequestParam(defaultValue = "5") int secBruteForceMaxAttempts,
            @RequestParam(defaultValue = "15") int secBruteForceLockoutMins,
            @RequestParam(required = false) String secHeadersEnabled,
            @RequestParam(defaultValue = "3") int secMaxSessions,
            @RequestParam(required = false) String secH2ConsoleEnabled,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            globalConfigService.saveSecurityConfig(
                    "on".equals(secRateLimitEnabled),
                    secRateLimitPerMin,
                    "on".equals(secBruteForceEnabled),
                    secBruteForceMaxAttempts,
                    secBruteForceLockoutMins,
                    "on".equals(secHeadersEnabled),
                    secMaxSessions,
                    "on".equals(secH2ConsoleEnabled));
            redirectAttrs.addFlashAttribute("successMessage", "Sicherheitseinstellungen gespeichert.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Fehler: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/broker-comm-factor")
    public String saveBrokerCommFactor(
            @RequestParam String brokerName,
            @RequestParam double factor,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            if (brokerName == null || brokerName.trim().isEmpty()) {
                redirectAttrs.addFlashAttribute("errorMessage", "Broker-Name darf nicht leer sein.");
            } else {
                globalConfigService.saveBrokerCommFactor(brokerName.trim(), factor);
                redirectAttrs.addFlashAttribute("successMessage",
                        "Commission Faktor für '" + brokerName.trim() + "' gespeichert.");
            }
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Fehler: " + e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/broker-comm-factor/delete")
    public String deleteBrokerCommFactor(
            @RequestParam String brokerName,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            globalConfigService.deleteBrokerCommFactor(brokerName);
            redirectAttrs.addFlashAttribute("successMessage",
                    "Commission Faktor für '" + brokerName + "' gelöscht.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Fehler: " + e.getMessage());
        }
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
    public String viewClientLogs(@RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String date,
            Model model) {

        java.time.LocalDate selectedDate;
        if (date != null && !date.isEmpty()) {
            selectedDate = java.time.LocalDate.parse(date);
        } else {
            selectedDate = java.time.LocalDate.now();
        }

        // Today's counters
        java.util.List<de.trademonitor.entity.ClientActionCounter> todayCounters;
        if (accountId != null) {
            todayCounters = clientActionCounterRepository
                    .findByDateOrderByAccountIdAsc(selectedDate).stream()
                    .filter(c -> c.getAccountId().equals(accountId))
                    .collect(java.util.stream.Collectors.toList());
        } else {
            todayCounters = clientActionCounterRepository.findByDateOrderByAccountIdAsc(selectedDate);
        }

        // Monthly totals: aggregate from 1st of that month to selected date
        java.time.LocalDate monthStart = selectedDate.withDayOfMonth(1);
        java.util.List<de.trademonitor.entity.ClientActionCounter> monthRaw;
        if (accountId != null) {
            monthRaw = clientActionCounterRepository
                    .findByAccountIdAndDateBetweenOrderByDateAsc(accountId, monthStart, selectedDate);
        } else {
            monthRaw = clientActionCounterRepository
                    .findByDateBetweenOrderByAccountIdAscDateAsc(monthStart, selectedDate);
        }
        // Aggregate monthly by accountId+action
        java.util.Map<String, Long> monthlyTotals = new java.util.LinkedHashMap<>();
        for (de.trademonitor.entity.ClientActionCounter c : monthRaw) {
            String key = c.getAccountId() + "|" + c.getAction();
            monthlyTotals.merge(key, c.getCount(), Long::sum);
        }

        // Available accounts
        java.util.List<Long> allAccountIds = clientActionCounterRepository.findDistinctAccountIds();

        // Recent errors
        java.util.List<de.trademonitor.entity.ClientErrorLog> errors;
        if (accountId != null) {
            errors = clientErrorLogRepository.findTop100ByAccountIdOrderByTimestampDesc(accountId);
        } else {
            errors = clientErrorLogRepository.findTop100ByOrderByTimestampDesc();
        }

        // Calculate total day count
        long totalDayCount = 0;
        for (de.trademonitor.entity.ClientActionCounter c : todayCounters) {
            totalDayCount += c.getCount();
        }

        model.addAttribute("todayCounters", todayCounters);
        model.addAttribute("monthlyTotals", monthlyTotals);
        model.addAttribute("allAccountIds", allAccountIds);
        model.addAttribute("errors", errors);
        model.addAttribute("filterAccountId", accountId);
        model.addAttribute("selectedDate", selectedDate.toString());
        model.addAttribute("selectedDateFormatted",
                selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        model.addAttribute("totalDayCount", totalDayCount);

        return "admin-client-logs";
    }

    @GetMapping("/client-logs/chart-data")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> clientLogsChartData(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "7") int days) {

        java.time.LocalDate end = java.time.LocalDate.now();
        java.time.LocalDate start = end.minusDays(days - 1);

        java.util.List<de.trademonitor.entity.ClientActionCounter> data =
                clientActionCounterRepository.findByAccountIdAndDateBetweenOrderByDateAsc(accountId, start, end);

        // Collect all unique actions and dates
        java.util.Set<String> actions = new java.util.LinkedHashSet<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < days; i++) {
            java.time.LocalDate d = start.plusDays(i);
            labels.add(d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM")));
        }
        for (de.trademonitor.entity.ClientActionCounter c : data) {
            actions.add(c.getAction());
        }

        // Build datasets: one per action
        java.util.List<java.util.Map<String, Object>> datasets = new java.util.ArrayList<>();
        String[] colors = {"#3fb950", "#58a6ff", "#f0883e", "#f85149", "#a371f7", "#3dc9b0", "#d2a8ff", "#8b949e"};
        int colorIdx = 0;
        for (String action : actions) {
            java.util.Map<String, Object> ds = new java.util.LinkedHashMap<>();
            ds.put("label", action);
            ds.put("backgroundColor", colors[colorIdx % colors.length]);
            long[] counts = new long[days];
            for (de.trademonitor.entity.ClientActionCounter c : data) {
                if (c.getAction().equals(action)) {
                    int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(start, c.getDate());
                    if (idx >= 0 && idx < days) {
                        counts[idx] = c.getCount();
                    }
                }
            }
            ds.put("data", counts);
            datasets.add(ds);
            colorIdx++;
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("labels", labels);
        result.put("datasets", datasets);
        return result;
    }

    @GetMapping("/health")
    public String serverHealth(Model model) {
        de.trademonitor.dto.ServerHealthDto health = new de.trademonitor.dto.ServerHealthDto();
        
        // OS info
        health.setOsName(System.getProperty("os.name"));
        
        // Memory JVM
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long usedMem = totalMem - freeMem;
        health.setTotalMemory(formatSize(totalMem));
        health.setFreeMemory(formatSize(freeMem));
        health.setUsedMemory(formatSize(usedMem));
        
        // Memory System
        try {
            javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
            javax.management.ObjectName name = javax.management.ObjectName.getInstance("java.lang:type=OperatingSystem");
            long sysTotal = (Long) mbs.getAttribute(name, "TotalPhysicalMemorySize");
            long sysFree = (Long) mbs.getAttribute(name, "FreePhysicalMemorySize");
            long sysUsed = sysTotal - sysFree;
            health.setSystemTotalMemory(formatSize(sysTotal));
            health.setSystemFreeMemory(formatSize(sysFree));
            health.setSystemUsedMemory(formatSize(sysUsed));
            model.addAttribute("sysFreeRaw", sysFree);
            model.addAttribute("sysUsedRaw", sysUsed);
        } catch (Throwable t) {
            health.setSystemTotalMemory("N/A");
            health.setSystemFreeMemory("N/A");
            health.setSystemUsedMemory("N/A");
            model.addAttribute("sysFreeRaw", 0);
            model.addAttribute("sysUsedRaw", 0);
        }
        
        // Disk (working dir)
        java.io.File root = new java.io.File(".");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getUsableSpace();
        long usedSpace = totalSpace - freeSpace;
        health.setDiskTotal(formatSize(totalSpace));
        health.setDiskFree(formatSize(freeSpace));
        health.setDiskUsed(formatSize(usedSpace));
        
        // CPU Load
        try {
            javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
            javax.management.ObjectName name = javax.management.ObjectName.getInstance("java.lang:type=OperatingSystem");
            double load = -1.0;
            try {
                load = (Double) mbs.getAttribute(name, "CpuLoad");
            } catch (Exception e) {
                try {
                    load = (Double) mbs.getAttribute(name, "SystemCpuLoad");
                } catch (Exception ex) {
                    load = (Double) mbs.getAttribute(name, "ProcessCpuLoad");
                }
            }
            if (load < 0) {
                load = (Double) mbs.getAttribute(name, "ProcessCpuLoad");
            }
            health.setCpuLoad(load >= 0 ? String.format("%.1f %%", load * 100) : "N/A");
        } catch (Throwable t) {
            health.setCpuLoad("N/A");
        }

        // File sizes
        java.io.File dbFile = new java.io.File(System.getProperty("user.home") + "/trademonitor_data/trademonitor.mv.db");
        health.setDbFileSize(dbFile.exists() ? formatSize(dbFile.length()) : "Not Found");

        java.io.File aiWar = new java.io.File("/opt/wildfly/standalone/deployments/ai-task-manager.war");
        health.setAiTaskManagerWarSize(aiWar.exists() ? formatSize(aiWar.length()) : "Not Found");
        
        java.io.File rootWar = new java.io.File("/opt/wildfly/standalone/deployments/ROOT.war");
        health.setRootWarSize(rootWar.exists() ? formatSize(rootWar.length()) : "Not Found");
        
        java.io.File logFile = new java.io.File("/opt/wildfly/standalone/log/server.log");
        if (!logFile.exists()) {
            java.io.File sysLog = new java.io.File("/var/log/syslog");
            if(sysLog.exists()) logFile = sysLog;
        }
        health.setLogFileSize(logFile.exists() ? formatSize(logFile.length()) : "Not Found");

        model.addAttribute("diskFreeRaw", freeSpace);
        model.addAttribute("diskUsedRaw", usedSpace);
        model.addAttribute("health", health);
        
        return "admin-health";
    }

    @GetMapping("/security-audit")
    public String securityAudit(Model model) {
        de.trademonitor.dto.SecurityAuditDto audit = securityAuditService.getLatestAudit();
        model.addAttribute("audit", audit);
        return "admin-security";
    }

    @PostMapping("/notifications/acknowledge")
    public String acknowledgeNotifications(org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        adminNotificationService.acknowledgeAll();
        redirectAttrs.addFlashAttribute("successMessage", "Alle Benachrichtigungen bestätigt.");
        return "redirect:/admin";
    }

    @PostMapping("/security-audit/run")
    public String runSecurityAudit(org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            securityAuditService.runManualAudit();
            redirectAttrs.addFlashAttribute("successMessage", "Security Audit erfolgreich durchgeführt.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Fehler beim Audit: " + e.getMessage());
        }
        return "redirect:/admin/security-audit";
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format(java.util.Locale.US, "%.1f %sB", (double)v / (1L << (z*10)), " KMGTPE".charAt(z));
    }
}
