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
    private de.trademonitor.repository.NetworkStatusLogRepository networkStatusLogRepository;

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

    @Autowired
    private de.trademonitor.service.AccountManager accountManager;

    @Autowired
    private javax.sql.DataSource dataSource;

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
                    
            de.trademonitor.model.Account runtimeAcc = accountManager.getAccount(acc.getAccountId());
            double commissionFactor = runtimeAcc != null ? runtimeAcc.getCommissionFactor() : 1.0;
            
            double totalProfit = closedTrades.stream().mapToDouble(t -> 
                t.getProfit() + t.getSwap() + (t.getCommission() * commissionFactor)
            ).sum();
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
        model.addAttribute("logEaDays", globalConfigService.getLogEaDays());

        // Homey Config
        model.addAttribute("homeyId", globalConfigService.getHomeyId());
        model.addAttribute("homeyEvent", globalConfigService.getHomeyEvent());
        model.addAttribute("homeyTriggerSync", globalConfigService.isHomeyTriggerSync());
        model.addAttribute("homeyTriggerApi", globalConfigService.isHomeyTriggerApi());
        model.addAttribute("homeyTriggerHealth", globalConfigService.isHomeyTriggerHealth());
        model.addAttribute("homeyTriggerSecurity", globalConfigService.isHomeyTriggerSecurity());
        model.addAttribute("homeyTriggerOffline", globalConfigService.isHomeyTriggerOffline());
        model.addAttribute("homeyRepeatCount", globalConfigService.getHomeyRepeatCount());
        model.addAttribute("syncAlarmDelayMins", globalConfigService.getSyncAlarmDelayMins());

        // Network Monitor Config
        model.addAttribute("maintenanceTimeoutMins", globalConfigService.getMaintenanceTimeoutMins());
        model.addAttribute("networkOfflineThresholdMins", globalConfigService.getNetworkOfflineThresholdMins());

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
            @RequestParam int logClientDays,
            @RequestParam(defaultValue = "30") int logEaDays) {
        globalConfigService.saveLogRetentionConfig(logLoginDays, logConnDays, logClientDays, logEaDays);
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

    @PostMapping("/network-monitor")
    public String saveNetworkMonitor(
            @RequestParam int maintenanceTimeoutMins,
            @RequestParam int networkOfflineThresholdMins,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        globalConfigService.setMaintenanceTimeoutMins(maintenanceTimeoutMins);
        globalConfigService.setNetworkOfflineThresholdMins(networkOfflineThresholdMins);
        redirectAttrs.addFlashAttribute("successMessage", "Network Monitor configuration saved.");
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
            monthlyTotals.merge(key, Long.valueOf(c.getCount()), (a, b) -> a + b);
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

        // --- Top 10 bar chart data (last 7 days, all accounts) ---
        java.time.LocalDate chartEnd = java.time.LocalDate.now();
        java.time.LocalDate chartStart = chartEnd.minusDays(6);
        java.util.List<de.trademonitor.entity.ClientActionCounter> last7DaysData =
                clientActionCounterRepository.findByDateBetweenOrderByAccountIdAscDateAsc(chartStart, chartEnd);

        // Aggregate by accountId + action
        java.util.Map<String, Long> aggregated = new java.util.LinkedHashMap<>();
        for (de.trademonitor.entity.ClientActionCounter c : last7DaysData) {
            String key = c.getAccountId() + " / " + c.getAction();
            aggregated.merge(key, c.getCount(), Long::sum);
        }

        // Sort by count desc and take top 10
        java.util.List<java.util.Map.Entry<String, Long>> sorted = new java.util.ArrayList<>(aggregated.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        java.util.List<String> top10Labels = new java.util.ArrayList<>();
        java.util.List<Long> top10Values = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            top10Labels.add(sorted.get(i).getKey());
            top10Values.add(sorted.get(i).getValue());
        }

        model.addAttribute("top10Labels", top10Labels);
        model.addAttribute("top10Values", top10Values);

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

    @GetMapping("/api/network-timeline")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> networkTimeline(
            @org.springframework.web.bind.annotation.RequestParam(value="period", defaultValue="24h") String period) {
            
        java.time.LocalDateTime cutoff;
        long totalSecsRange;

        if ("1w".equalsIgnoreCase(period)) {
            cutoff = java.time.LocalDateTime.now().minusDays(7);
            totalSecsRange = 7 * 24 * 60 * 60;
        } else if ("1m".equalsIgnoreCase(period)) {
            cutoff = java.time.LocalDateTime.now().minusDays(30);
            totalSecsRange = 30L * 24 * 60 * 60;
        } else if ("6m".equalsIgnoreCase(period)) {
            cutoff = java.time.LocalDateTime.now().minusDays(180);
            totalSecsRange = 180L * 24 * 60 * 60;
        } else {
            cutoff = java.time.LocalDateTime.now().minusHours(24);
            totalSecsRange = 24 * 60 * 60;
        }
        
        java.util.List<de.trademonitor.entity.NetworkStatusLogEntity> logs = networkStatusLogRepository.findLogsSince(cutoff);
        
        java.util.List<java.util.Map<String, Object>> events = new java.util.ArrayList<>();
        for (de.trademonitor.entity.NetworkStatusLogEntity log : logs) {
            java.util.Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("status", log.getStatus());
            event.put("startTime", log.getStartTime().toString());
            event.put("endTime", log.getEndTime() != null ? log.getEndTime().toString() : java.time.LocalDateTime.now().toString());
            
            java.time.LocalDateTime realStart = log.getStartTime().isBefore(cutoff) ? cutoff : log.getStartTime();
            java.time.LocalDateTime end = log.getEndTime() != null ? log.getEndTime() : java.time.LocalDateTime.now();
            long durationSeconds = java.time.temporal.ChronoUnit.SECONDS.between(realStart, end);
            
            event.put("durationSeconds", durationSeconds);
            events.add(event);
        }
        
        return java.util.Map.of(
            "events", events,
            "totalSecsRange", totalSecsRange
        );
    }

    @GetMapping("/security-audit")
    public String securityAudit(Model model) {
        de.trademonitor.dto.SecurityAuditDto audit = securityAuditService.getLatestAudit();
        model.addAttribute("audit", audit);
        model.addAttribute("fail2banWhitelist", globalConfigService.getFail2banWhitelistIps());
        return "admin-security";
    }

    @PostMapping("/fail2ban/whitelist")
    public String addFail2banWhitelist(
            @RequestParam String ipAddress,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        try {
            if (ipAddress != null && !ipAddress.trim().isEmpty()) {
                securityAuditService.syncFail2banWhitelist(ipAddress.trim());
                redirectAttrs.addFlashAttribute("successMessage", "IP " + ipAddress + " wurde zur Fail2Ban Whitelist hinzugefügt und entsperrt.");
            }
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", "Fehler beim Whitelisten der IP: " + e.getMessage());
        }
        return "redirect:/admin/security-audit";
    }

    @GetMapping("/api/fail2ban/details")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.List<java.util.Map<String, Object>>> getFail2banDetails() {
        return org.springframework.http.ResponseEntity.ok(securityAuditService.getFail2banLiveDetails());
    }

    @PostMapping("/api/fail2ban/unban")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> unbanIp(@org.springframework.web.bind.annotation.RequestParam("ipAddress") String ipAddress) {
        boolean success = securityAuditService.unbanIp(ipAddress);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        if (success) {
            response.put("status", "success");
            response.put("message", "IP " + ipAddress + " erfolgreich freigegeben.");
        } else {
            response.put("status", "error");
            response.put("message", "Fehler beim Freigeben der IP.");
        }
        return org.springframework.http.ResponseEntity.ok(response);
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

    @org.springframework.web.bind.annotation.PostMapping("/api/accounts/{id}/accept-ea-logs")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> acceptEaLogs(@org.springframework.web.bind.annotation.PathVariable("id") long accountId) {
        accountManager.acceptEaLogs(accountId);
        return java.util.Map.of("status", "ok");
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/accounts/accept-all-ea-logs")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> acceptAllEaLogs() {
        for (de.trademonitor.entity.AccountEntity acc : accountRepository.findAll()) {
            accountManager.acceptEaLogs(acc.getAccountId());
        }
        return java.util.Map.of("status", "ok");
    }

    @GetMapping("/api/db-details")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> dbDetails() {
        java.util.List<java.util.Map<String, Object>> tables = new java.util.ArrayList<>();
        long totalBytes = 0;

        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {

            // Step 1: Get all table names and row counts
            java.util.Map<String, Long> rowCounts = new java.util.LinkedHashMap<>();
            java.sql.ResultSet rs = stmt.executeQuery(
                "SELECT TABLE_NAME, COALESCE(ROW_COUNT_ESTIMATE, 0) AS ROW_EST " +
                "FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME"
            );
            while (rs.next()) {
                rowCounts.put(rs.getString("TABLE_NAME"), rs.getLong("ROW_EST"));
            }
            rs.close();

            // Step 2: For each table, estimate row size from column metadata
            for (java.util.Map.Entry<String, Long> entry : rowCounts.entrySet()) {
                String tableName = entry.getKey();
                long rowCount = entry.getValue();
                long estRowSize = 0;

                java.sql.ResultSet colRs = stmt.executeQuery(
                    "SELECT DATA_TYPE, COALESCE(CHARACTER_MAXIMUM_LENGTH, 0) AS MAX_LEN, " +
                    "COALESCE(NUMERIC_PRECISION, 0) AS NUM_PREC " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = '" + tableName + "'"
                );
                while (colRs.next()) {
                    String dataType = colRs.getString("DATA_TYPE").toUpperCase();
                    long maxLen = colRs.getLong("MAX_LEN");

                    // Estimate bytes per column per row
                    long colSize;
                    switch (dataType) {
                        case "BIGINT":
                        case "TIMESTAMP":
                        case "TIMESTAMP WITH TIME ZONE":
                        case "DOUBLE":
                        case "DOUBLE PRECISION":
                            colSize = 8; break;
                        case "INTEGER":
                        case "INT":
                        case "REAL":
                        case "FLOAT":
                            colSize = 4; break;
                        case "SMALLINT":
                        case "TINYINT":
                            colSize = 2; break;
                        case "BOOLEAN":
                        case "BIT":
                            colSize = 1; break;
                        case "CHARACTER VARYING":
                        case "VARCHAR":
                        case "CLOB":
                        case "CHARACTER LARGE OBJECT":
                            // Estimate average fill as 40% of max length, min 20 bytes
                            colSize = Math.max(20, maxLen * 2 / 5);
                            break;
                        default:
                            colSize = 16; // general fallback
                    }
                    estRowSize += colSize;
                }
                colRs.close();

                // Add ~16 bytes overhead per row for H2 internal bookkeeping
                estRowSize += 16;

                long estTableSize = rowCount * estRowSize;
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("tableName", tableName);
                row.put("diskUsedBytes", estTableSize);
                row.put("diskUsedFormatted", formatSize(estTableSize));
                row.put("rowCount", rowCount);
                tables.add(row);
                totalBytes += estTableSize;
            }

            // Sort by estimated size descending
            tables.sort((a, b) -> Long.compare((Long)b.get("diskUsedBytes"), (Long)a.get("diskUsedBytes")));

        } catch (Exception e) {
            System.err.println("[AdminController] DB details error: " + e.getMessage());
            e.printStackTrace();
        }

        // Also report the .mv.db file size
        java.io.File dbFile = new java.io.File(System.getProperty("user.home") + "/trademonitor_data/trademonitor.mv.db");
        long fileSize = dbFile.exists() ? dbFile.length() : 0;

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tables", tables);
        result.put("totalInternalBytes", totalBytes);
        result.put("totalInternalFormatted", formatSize(totalBytes));
        result.put("dbFileBytes", fileSize);
        result.put("dbFileFormatted", formatSize(fileSize));
        return result;
    }
}
