package de.trademonitor.model;

import de.trademonitor.dto.MagicProfitEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountDrawdownTest {

    @Test
    void keepsRealizedAndEquityDrawdownSeparate() {
        Account account = new Account(123L, "Test Broker", "EUR", 1060.0);

        ClosedTrade winningTrade = closedTrade(1L, 42L, "2026.01.01 10:00:00", 100.0);
        ClosedTrade losingTrade = closedTrade(2L, 42L, "2026.01.02 10:00:00", -40.0);
        account.setClosedTrades(List.of(winningTrade, losingTrade));

        Trade openTrade = new Trade();
        openTrade.setTicket(3L);
        openTrade.setMagicNumber(42L);
        openTrade.setProfit(-100.0);
        openTrade.setComment("Strategy 42");
        account.setOpenTrades(List.of(openTrade));

        MagicProfitEntry result = account.getMagicProfitEntries(0, 0, null).get(0);

        assertEquals(40.0, result.getMaxDrawdownEur(), 0.001);
        assertEquals(40.0 / 1100.0 * 100.0, result.getMaxDrawdownPercent(), 0.001);
        assertEquals(140.0, result.getMaxEquityDrawdownEur(), 0.001);
        assertEquals(140.0 / 1100.0 * 100.0, result.getMaxEquityDrawdownPercent(), 0.001);
        assertEquals(-100.0, result.getOpenProfit(), 0.001);
    }

    private static ClosedTrade closedTrade(long ticket, long magicNumber, String closeTime, double profit) {
        ClosedTrade trade = new ClosedTrade();
        trade.setTicket(ticket);
        trade.setMagicNumber(magicNumber);
        trade.setCloseTime(closeTime);
        trade.setProfit(profit);
        return trade;
    }
}
