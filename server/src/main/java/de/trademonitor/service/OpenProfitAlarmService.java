package de.trademonitor.service;

import de.trademonitor.model.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors open profit for each account and triggers alarm (email + siren)
 * when configurable thresholds are breached.
 */
@Service
public class OpenProfitAlarmService {

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private EmailService emailService;

    @Autowired
    private HomeyService homeyService;

    @Autowired
    private GlobalConfigService globalConfigService;

    // Latch per account: prevents repeated alarm firing until condition clears
    private final Map<Long, Boolean> alarmFiredMap = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 5000)
    public void checkOpenProfitAlarms() {
        for (Account account : accountManager.getAllAccounts()) {
            if (!account.isOpenProfitAlarmEnabled()) {
                account.setOpenProfitAlarmTriggered(false);
                alarmFiredMap.remove(account.getAccountId());
                continue;
            }

            double openProfit = account.getTotalProfit();
            double balance = account.getBalance();
            boolean breached = false;

            // Check absolute threshold (e.g. openProfit < -5000)
            Double absThreshold = account.getOpenProfitAlarmAbs();
            if (absThreshold != null && openProfit < absThreshold) {
                breached = true;
            }

            // Check percentage threshold (e.g. drawdown > 10% of balance)
            Double pctThreshold = account.getOpenProfitAlarmPct();
            if (pctThreshold != null && pctThreshold > 0 && balance > 0) {
                double drawdownPct = (Math.abs(openProfit) / balance) * 100.0;
                if (openProfit < 0 && drawdownPct >= pctThreshold) {
                    breached = true;
                }
            }

            if (breached) {
                account.setOpenProfitAlarmTriggered(true);
                boolean alreadyFired = alarmFiredMap.getOrDefault(account.getAccountId(), false);
                if (!alreadyFired) {
                    triggerAlarm(account, openProfit, balance);
                    alarmFiredMap.put(account.getAccountId(), true);
                }
            } else {
                account.setOpenProfitAlarmTriggered(false);
                alarmFiredMap.put(account.getAccountId(), false);
            }
        }
    }

    private void triggerAlarm(Account account, double openProfit, double balance) {
        String accountName = account.getName() != null ? account.getName()
                : String.valueOf(account.getAccountId());
        double drawdownPct = balance > 0 ? (Math.abs(openProfit) / balance) * 100.0 : 0;

        String subject = "⚠️ Open Profit Alarm: " + accountName;
        String body = "ALARM: Open Profit Schwellwert unterschritten!\n\n"
                + "Account: " + accountName + " (" + account.getAccountId() + ")\n"
                + "Open Profit: " + String.format("%.2f", openProfit) + " " + account.getCurrency() + "\n"
                + "Drawdown: " + String.format("%.2f", drawdownPct) + "%\n"
                + "Balance: " + String.format("%.2f", balance) + " " + account.getCurrency() + "\n\n"
                + "Konfigurierte Schwellwerte:\n"
                + (account.getOpenProfitAlarmAbs() != null
                        ? "  Absolut: " + String.format("%.2f", account.getOpenProfitAlarmAbs()) + " "
                                + account.getCurrency() + "\n"
                        : "")
                + (account.getOpenProfitAlarmPct() != null
                        ? "  Prozentual: " + String.format("%.2f", account.getOpenProfitAlarmPct()) + "%\n"
                        : "")
                + "\nBitte Dashboard prüfen!";

        emailService.sendSyncWarningEmail(subject, body);

        // Trigger Homey Siren (reuse sync trigger config)
        if (globalConfigService.isHomeyTriggerSync()) {
            homeyService.triggerSiren();
        }

        System.out.println("OPEN PROFIT ALARM triggered for account " + account.getAccountId()
                + " (" + accountName + ") - Open Profit: " + openProfit);
    }
}
