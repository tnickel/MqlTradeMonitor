package de.trademonitor.controller;

import de.trademonitor.dto.HeartbeatRequest;
import de.trademonitor.dto.HistoryUpdateRequest;
import de.trademonitor.dto.RegisterRequest;
import de.trademonitor.dto.TradeUpdateRequest;
import de.trademonitor.service.AccountManager;
import de.trademonitor.service.TradeStorage;
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
    private TradeStorage tradeStorage;

    /**
     * Register a new MetaTrader account.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            accountManager.registerAccount(
                    request.getAccountId(),
                    request.getBroker(),
                    request.getCurrency(),
                    request.getBalance());
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Account registered successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * Receive trade updates from MetaTrader.
     */
    @PostMapping("/trades")
    public ResponseEntity<?> updateTrades(@RequestBody TradeUpdateRequest request) {
        try {
            accountManager.updateTrades(
                    request.getAccountId(),
                    request.getTrades(),
                    request.getEquity(),
                    request.getBalance());
            tradeStorage.saveTrades(request);

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "tradesReceived", request.getTrades() != null ? request.getTrades().size() : 0));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * Receive heartbeat from MetaTrader.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@RequestBody HeartbeatRequest request) {
        try {
            accountManager.updateHeartbeat(request.getAccountId());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
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
     * Receive trade history from MetaTrader.
     */
    @PostMapping("/history")
    public ResponseEntity<?> updateHistory(@RequestBody HistoryUpdateRequest request) {
        try {
            accountManager.updateHistory(
                    request.getAccountId(),
                    request.getClosedTrades());

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "historyReceived", request.getClosedTrades() != null ? request.getClosedTrades().size() : 0));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }
}
