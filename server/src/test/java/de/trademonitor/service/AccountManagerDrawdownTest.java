package de.trademonitor.service;

import de.trademonitor.dto.MagicDrawdownItem;
import de.trademonitor.entity.EquitySnapshotEntity;
import de.trademonitor.model.Account;
import de.trademonitor.model.Trade;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountManagerDrawdownTest {

    @Test
    void calculatesAccountDrawdownsFromOneSharedTimeline() {
        List<EquitySnapshotEntity> snapshots = List.of(
                snapshot("2026-01-01T10:00:00", 1000.0, 1000.0),
                snapshot("2026-01-02T10:00:00", 1080.0, 1100.0),
                snapshot("2026-01-03T10:00:00", 900.0, 1050.0));

        AccountManager.AccountDrawdownMetrics result =
                AccountManager.calculateAccountDrawdownMetrics(snapshots, 1060.0, 950.0);

        assertEquals(50.0, result.drawdownEur(), 0.001);
        assertEquals(50.0 / 1100.0 * 100.0, result.drawdownPercent(), 0.001);
        assertEquals(180.0, result.equityDrawdownEur(), 0.001);
        assertEquals(180.0 / 1080.0 * 100.0, result.equityDrawdownPercent(), 0.001);
    }

    @Test
    @SuppressWarnings("unchecked")
    void includesOpenSwapInMagicAndAccountFloatingProfit() throws Exception {
        Account account = new Account(1L, "Test Broker", "EUR", 1000.0);
        account.setName("Test Account");
        account.setType("REAL");
        account.setEquity(887.74);

        Trade trade = new Trade();
        trade.setTicket(1L);
        trade.setMagicNumber(42L);
        trade.setProfit(-100.0);
        trade.setSwap(-12.26);
        account.setOpenTrades(List.of(trade));

        AccountManager manager = new AccountManager();
        Field accountsField = AccountManager.class.getDeclaredField("accounts");
        accountsField.setAccessible(true);
        ((Map<Long, Account>) accountsField.get(manager)).put(account.getAccountId(), account);

        MagicDrawdownItem result = manager.getMagicDrawdowns().get(0);

        assertEquals(-112.26, result.getOpenProfit(), 0.001);
        assertEquals(-112.26, result.getAccountOpenProfit(), 0.001);
    }

    private static EquitySnapshotEntity snapshot(String timestamp, double equity, double balance) {
        return new EquitySnapshotEntity(1L, timestamp, equity, balance);
    }
}
