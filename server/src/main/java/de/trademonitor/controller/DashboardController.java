package de.trademonitor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.trademonitor.model.Account;
import de.trademonitor.model.ClosedTrade;
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
        model.addAttribute("magicProfits", account.getMagicProfitEntries());

        // Build magic curve data as JSON for Chart.js
        model.addAttribute("magicCurveJson", buildMagicCurveJson(account));

        return "account-detail";
    }

    /**
     * Build JSON string with cumulative profit curves per magic number.
     * Format: { "12345": { "labels": ["2026-01-01 10:00", ...], "data": [10.5,
     * 25.3, ...] }, ... }
     */
    private String buildMagicCurveJson(Account account) {
        try {
            // Group closed trades by magic number
            Map<Long, List<ClosedTrade>> byMagic = account.getClosedTrades().stream()
                    .collect(Collectors.groupingBy(ClosedTrade::getMagicNumber));

            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<Long, List<ClosedTrade>> entry : new TreeMap<>(byMagic).entrySet()) {
                List<ClosedTrade> trades = entry.getValue();
                // Sort by close time
                trades.sort(Comparator.comparing(ClosedTrade::getCloseTime, Comparator.nullsLast(String::compareTo)));

                List<String> labels = new ArrayList<>();
                List<Double> data = new ArrayList<>();
                double cumulative = 0;

                // Find first non-empty comment for this magic
                String comment = trades.stream()
                        .map(ClosedTrade::getComment)
                        .filter(c -> c != null && !c.isEmpty())
                        .findFirst()
                        .orElse("");

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
