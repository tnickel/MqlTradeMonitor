package de.trademonitor.controller;

import de.trademonitor.dto.HeartbeatRequest;
import de.trademonitor.dto.HistoryUpdateRequest;
import de.trademonitor.dto.RegisterRequest;
import de.trademonitor.dto.TradeInitRequest;
import de.trademonitor.dto.TradeUpdateRequest;
import de.trademonitor.service.AccountManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for MetaTrader EA communication.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private de.trademonitor.repository.ClientLogRepository clientLogRepository;

    @Autowired
    private de.trademonitor.service.EmailService emailService;

    private void logClientAction(Long accountId, String action, String message,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            String ip = request.getRemoteAddr();
            de.trademonitor.entity.ClientLog log = new de.trademonitor.entity.ClientLog(accountId, action, ip, message);
            clientLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Failed to save client log: " + e.getMessage());
        }
    }

    /**
     * Test email configuration.
     */
    @PostMapping("/test-email")
    public ResponseEntity<?> testEmail() {
        try {
            emailService.sendTestEmail();
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Test email sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * Register a new MetaTrader account.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
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
            logClientAction(request.getAccountId(), "REGISTER_ERROR", e.getMessage(), httpRequest);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * Receive initial full trade list from MetaTrader (first connect / reconnect).
     * Both open trades and closed history are sent together.
     * Server checks for duplicates and only inserts new trades.
     */
    @PostMapping("/trades-init")
    public ResponseEntity<?> initTrades(@RequestBody TradeInitRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        try {
            int openCount = request.getTrades() != null ? request.getTrades().size() : 0;
            int closedCount = request.getClosedTrades() != null ? request.getClosedTrades().size() : 0;
            logClientAction(request.getAccountId(), "INIT_TRADES", "Open: " + openCount + ", Closed: " + closedCount,
                    httpRequest);

            // Update open trades
            accountManager.updateTrades(
                    request.getAccountId(),
                    request.getTrades(),
                    request.getEquity(),
                    request.getBalance());

            // Save closed trades with duplicate check
            int newTradesInserted = accountManager.updateHistory(
                    request.getAccountId(),
                    request.getClosedTrades());

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "openTradesReceived", openCount,
                    "closedTradesReceived", closedCount,
                    "newTradesInserted", newTradesInserted));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            logClientAction(request.getAccountId(), "INIT_ERROR", msg, httpRequest);
            System.err.println("ERROR in /api/trades-init: " + msg);
            e.printStackTrace();
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
    public ResponseEntity<?> updateTrades(@RequestBody TradeUpdateRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
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
            logClientAction(request.getAccountId(), "UPDATE_ERROR", e.getMessage(), httpRequest);
            accountManager.reportError(request.getAccountId(), "Update Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * Receive heartbeat from MetaTrader.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@RequestBody HeartbeatRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        try {
            logClientAction(request.getAccountId(), "HEARTBEAT", "Alive", httpRequest);
            accountManager.updateHeartbeat(request.getAccountId());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            logClientAction(request.getAccountId(), "HEARTBEAT_ERROR", e.getMessage(), httpRequest);
            accountManager.reportError(request.getAccountId(), "Heartbeat Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * Get all accounts as JSON (for AJAX updates).
     */
    @GetMapping("/accounts")
    public ResponseEntity<?> getAccounts() {
        return ResponseEntity.ok(accountManager.getAccountsWithStatus());
    }

    /**
     * Receive trade history from MetaTrader (incremental updates).
     * Checks for duplicates and only inserts new trades.
     */
    @PostMapping("/history")
    public ResponseEntity<?> updateHistory(@RequestBody HistoryUpdateRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
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
            logClientAction(request.getAccountId(), "HISTORY_ERROR", e.getMessage(), httpRequest);
            accountManager.reportError(request.getAccountId(), "History Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }
}
