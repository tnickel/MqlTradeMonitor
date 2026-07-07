package de.trademonitor.controller;

import de.trademonitor.dto.HeartbeatRequest;
import de.trademonitor.dto.HistoryUpdateRequest;
import de.trademonitor.dto.RegisterRequest;
import de.trademonitor.dto.TradeInitRequest;
import de.trademonitor.dto.TradeUpdateRequest;
import de.trademonitor.dto.EaLogRequest;
import de.trademonitor.service.AccountManager;
import de.trademonitor.entity.UserEntity;
import de.trademonitor.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for MetaTrader EA communication.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ApiController.class.getName());

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private de.trademonitor.repository.ClientActionCounterRepository clientActionCounterRepository;

    @Autowired
    private de.trademonitor.repository.ClientErrorLogRepository clientErrorLogRepository;

    @Autowired
    private de.trademonitor.repository.EaLogEntryRepository eaLogEntryRepository;

    @Autowired
    private de.trademonitor.service.EmailService emailService;

    @Autowired
    private de.trademonitor.service.NetworkStatusService networkStatusService;

    @Autowired
    private de.trademonitor.service.ExchangeRateService exchangeRateService;

    private void logClientAction(Long accountId, String action, String message,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    de.trademonitor.entity.ClientActionCounter counter = clientActionCounterRepository
                            .findByAccountIdAndActionAndDate(accountId, action, today)
                            .orElse(null);
                    if (counter != null) {
                        counter.increment();
                        clientActionCounterRepository.save(counter);
                    } else {
                        clientActionCounterRepository.save(
                                new de.trademonitor.entity.ClientActionCounter(accountId, action, today));
                    }
                    break;
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    if (attempt == 2) {
                        throw e;
                    }
                }
            }

            boolean isError = action.contains("ERROR") || action.equals("AUTH_FAILED");
            if (isError) {
                String ip = request.getRemoteAddr();
                clientErrorLogRepository.save(
                        new de.trademonitor.entity.ClientErrorLog(accountId, action, ip, message));
            }
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.SEVERE, "Failed to save client log: " + e.getMessage(), e);
        }
    }

    private boolean isAuthorized(String userKey, Long accountId) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return false;
        }
        UserEntity user = userRepository.findByApiKey(userKey).orElse(null);
        if (user == null) {
            return false;
        }
        // Admins see everything, normal users must have the account allowed
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        return user.getAllowedAccountIds().contains(accountId);
    }

    private boolean isValidTradeMetrics(double equity, double balance) {
        return Double.isFinite(equity) && Double.isFinite(balance);
    }

    /**
     * Test email configuration. Admin-only.
     */
    @PostMapping("/test-email")
    public ResponseEntity<?> testEmail() {
        // Only admins may trigger test emails
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return ResponseEntity.status(403).body(Map.of("status", "error", "message", "Admin access required"));
        }
        try {
            emailService.sendTestEmail();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Test email sent successfully"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", msg));
        }
    }

    /**
     * Register a new MetaTrader account.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody RegisterRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        if (request == null || request.getAccountId() <= 0 || !Double.isFinite(request.getBalance())) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid accountId or balance"));
        }

        if (!isAuthorized(userKey, request.getAccountId())) {
            logClientAction(request.getAccountId(), "AUTH_FAILED", "Invalid or missing User Key during register",
                    httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }

        try {
            logClientAction(request.getAccountId(), "REGISTER", "Broker: " + request.getBroker(), httpRequest);
            accountManager.registerAccount(
                    request.getAccountId(),
                    request.getBroker(),
                    request.getCurrency(),
                    request.getBalance());
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Account registered successfully"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            logClientAction(request.getAccountId(), "REGISTER_ERROR", msg, httpRequest);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", msg));
        }
    }

    /**
     * Receive initial full trade list from MetaTrader (first connect / reconnect).
     * Both open trades and closed history are sent together.
     * Server checks for duplicates and only inserts new trades.
     */
    @PostMapping("/trades-init")
    public ResponseEntity<?> initTrades(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody TradeInitRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid request"));
        }

        if (!isValidTradeMetrics(request.getEquity(), request.getBalance())) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid equity or balance"));
        }

        if (!isAuthorized(userKey, request.getAccountId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }

        try {
            int openCount = request.getTrades() != null ? request.getTrades().size() : 0;
            int closedCount = request.getClosedTrades() != null ? request.getClosedTrades().size() : 0;
            
            if (openCount > 5000 || closedCount > 100000) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Payload size limit exceeded"));
            }

            logClientAction(request.getAccountId(), "INIT_TRADES", "Open: " + openCount + ", Closed: " + closedCount,
                    httpRequest);

            int newTradesInserted = accountManager.initTrades(
                    request.getAccountId(),
                    request.getTrades(),
                    request.getClosedTrades(),
                    request.getEquity(),
                    request.getBalance());

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "openTradesReceived", openCount,
                    "closedTradesReceived", closedCount,
                    "newTradesInserted", newTradesInserted));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            logClientAction(request.getAccountId(), "INIT_ERROR", msg, httpRequest);
            LOG.log(java.util.logging.Level.SEVERE, "ERROR in /api/trades-init: " + msg, e);
            accountManager.reportError(request.getAccountId(), "Init Error: " + msg);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", msg));
        }
    }

    /**
     * Receive trade updates from MetaTrader (incremental, after init).
     */
    @PostMapping("/trades")
    public ResponseEntity<?> updateTrades(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody TradeUpdateRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid request"));
        }

        if (!isValidTradeMetrics(request.getEquity(), request.getBalance())) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid equity or balance"));
        }

        if (!isAuthorized(userKey, request.getAccountId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }

        try {
            int count = request.getTrades() != null ? request.getTrades().size() : 0;
            // Only log if there are actual trades, to reduce noise? User wants to see
            // connection attempts though.
            // Let's log it.
            logClientAction(request.getAccountId(), "UPDATE_TRADES", "Count: " + count, httpRequest);

            accountManager.updateTrades(
                    request.getAccountId(),
                    request.getTrades(),
                    request.getEquity(),
                    request.getBalance());

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "tradesReceived", count));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            logClientAction(request.getAccountId(), "UPDATE_ERROR", msg, httpRequest);
            accountManager.reportError(request.getAccountId(), "Update Error: " + msg);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", msg));
        }
    }

    /**
     * Receive heartbeat from MetaTrader.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody HeartbeatRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid request"));
        }

        if (!isAuthorized(userKey, request.getAccountId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }

        try {
            logClientAction(request.getAccountId(), "HEARTBEAT", "Alive", httpRequest);
            accountManager.updateHeartbeat(request.getAccountId());
            networkStatusService.registerHeartbeat();
            
            // Calculate Server Time Offset from Broker Time
            if (request.getTimestamp() != null && !request.getTimestamp().isEmpty()) {
                try {
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
                    java.time.LocalDateTime brokerTime = java.time.LocalDateTime.parse(request.getTimestamp(), formatter);
                    java.time.LocalDateTime serverTime = java.time.LocalDateTime.now();
                    long offsetSeconds = java.time.temporal.ChronoUnit.SECONDS.between(brokerTime, serverTime);
                    accountManager.updateServerTimeOffset(request.getAccountId(), offsetSeconds);
                } catch (Exception e) {
                    LOG.log(java.util.logging.Level.WARNING, "Could not parse heartbeat timestamp: " + request.getTimestamp() + " for account " + request.getAccountId(), e);
                }
            }

            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            logClientAction(request.getAccountId(), "HEARTBEAT_ERROR", msg, httpRequest);
            accountManager.reportError(request.getAccountId(), "Heartbeat Error: " + msg);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", msg));
        }
    }

    /**
     * Get all accounts as JSON (for AJAX updates).
     */
    @GetMapping("/accounts")
    public ResponseEntity<?> getAccounts(@org.springframework.security.core.annotation.AuthenticationPrincipal de.trademonitor.security.CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "error", "message", "Unauthorized"));
        }
        UserEntity user = userDetails.getUserEntity();
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return ResponseEntity.ok(accountManager.getAccountsWithStatus());
        } else {
            java.util.Set<Long> allowedIds = user.getAllowedAccountIds();
            java.util.List<java.util.Map<String, Object>> filtered = accountManager.getAccountsWithStatus().stream()
                .filter(acc -> {
                    Long accountId = (Long) acc.get("accountId");
                    return allowedIds != null && allowedIds.contains(accountId);
                })
                .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(filtered);
        }
    }

    /**
     * Get current exchange rate for a symbol.
     */
    @GetMapping("/market/rate")
    public ResponseEntity<?> getMarketRate(@RequestParam("symbol") String symbol) {
        // No '/' allowed: it is the only character that could restructure the
        // downstream Yahoo Finance URL path. '.' supports dotted broker symbols.
        if (symbol == null || symbol.length() > 20 || !symbol.matches("^[a-zA-Z0-9.-]+$")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid symbol parameter"));
        }
        de.trademonitor.service.ExchangeRateService.CachedRate rate = exchangeRateService.getRateDetails(symbol);
        if (rate != null && rate.getPrice() > 0) {
            return ResponseEntity.ok(Map.of(
                "symbol", symbol.toUpperCase(),
                "price", rate.getPrice(),
                "timestamp", rate.getTimestamp().toString(),
                "source", "Yahoo Finance",
                "success", true
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("symbol", symbol.toUpperCase(), "success", false, "error", "Rate not found or external API error"));
        }
    }

    /**
     * Receive trade history from MetaTrader (incremental updates).
     * Checks for duplicates and only inserts new trades.
     */
    @PostMapping("/history")
    public ResponseEntity<?> updateHistory(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody HistoryUpdateRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid request"));
        }

        if (!isAuthorized(userKey, request.getAccountId())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }

        try {
            int count = request.getClosedTrades() != null ? request.getClosedTrades().size() : 0;
            logClientAction(request.getAccountId(), "HISTORY_UPDATE", "Count: " + count, httpRequest);

            int newInserted = accountManager.updateHistory(
                    request.getAccountId(),
                    request.getClosedTrades());

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "historyReceived", count,
                    "newTradesInserted", newInserted));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            logClientAction(request.getAccountId(), "HISTORY_ERROR", msg, httpRequest);
            accountManager.reportError(request.getAccountId(), "History Error: " + msg);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", msg));
        }
    }

    /**
     * Receive EA log entries from MetaTrader.
     * The EA sends new log lines incrementally.
     */
    @PostMapping("/ea-logs")
    public ResponseEntity<?> receiveEaLogs(
            @RequestHeader(value = "X-User-Key", required = false) String userKey,
            @RequestBody EaLogRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid request body"));
        }
        long accountId = request.getAccountId();
        if (accountId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid or missing accountId"));
        }

        if (!isAuthorized(userKey, accountId)) {
            logClientAction(accountId, "AUTH_FAILED", "Invalid or missing User Key during log upload", httpRequest);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Unauthorized"));
        }

        try {
            java.util.List<String> logEntries = request.getLogEntries();
            if (logEntries == null || logEntries.isEmpty()) {
                return ResponseEntity.ok(Map.of("status", "ok", "stored", 0));
            }

            if (logEntries.size() > 1000) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Payload size limit exceeded"));
            }

            int stored = 0;
            java.util.List<de.trademonitor.entity.EaLogEntry> entities = new java.util.ArrayList<>();
            for (String line : logEntries) {
                if (line != null && !line.trim().isEmpty()) {
                    // Filter out connection spam/errors and EA debug noise to avoid DB bloat
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("monitor.ki-software-schmiede.de") ||
                        lowerLine.contains("reconnect failed") ||
                        lowerLine.contains("reconnect attempt") ||
                        lowerLine.contains("404 - not found") ||
                        lowerLine.contains("server response: <html") ||
                        lowerLine.contains("signal ist neutral") ||
                        lowerLine.contains("starte konfliktprüfung") ||
                        lowerLine.contains("lese signaldatei") ||
                        lowerLine.contains("signal gelesen:") ||
                        lowerLine.contains("prüfe symbol:") ||
                        lowerLine.contains("header übersprungen:") ||
                        lowerLine.contains("dateialter:") ||
                        lowerLine.contains("aktuelle zeit:") ||
                        lowerLine.contains("datei-änderungszeit:") ||
                        lowerLine.contains("========") ||
                        lowerLine.contains("csv debug ende") ||
                        lowerLine.contains("csv-laden abgeschlossen") ||
                        lowerLine.contains("[no changes]") ||
                        lowerLine.contains("tp-check skipped") ||
                        lowerLine.contains("tp-check für") ||
                        lowerLine.contains("basket tp für") ||
                        lowerLine.contains("ist ok mit signal")) {
                        continue; // Skip saving this log line
                    }
                    entities.add(new de.trademonitor.entity.EaLogEntry(accountId, line));
                    stored++;
                }
            }
            if (!entities.isEmpty()) {
                eaLogEntryRepository.saveAll(entities);
            }

            logClientAction(accountId, "EA_LOG_UPLOAD", "Lines: " + stored, httpRequest);

            return ResponseEntity.ok(Map.of("status", "ok", "stored", stored));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            logClientAction(accountId, "EA_LOG_ERROR", msg, httpRequest);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", msg));
        }
    }

    /**
     * Get the latest available version of the Android application.
     */
    @GetMapping("/latest-version")
    public ResponseEntity<?> getLatestVersion() {
        return ResponseEntity.ok(Map.of(
                "versionCode", 5,
                "versionName", "1.4",
                "downloadUrl", "https://monitor.tnickel-ki.de/trademonitor_v1.4.apk"
        ));
    }
}
