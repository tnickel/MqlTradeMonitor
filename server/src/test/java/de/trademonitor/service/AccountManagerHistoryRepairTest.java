package de.trademonitor.service;

import de.trademonitor.model.Account;
import de.trademonitor.model.ClosedTrade;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountManagerHistoryRepairTest {
    // Synthetic profit/commission values exercise cache refresh, not account reconstruction.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void repairRefreshesLoadedHistoryAndCachedMetricsWithoutCountingAnInsert(boolean init) {
        AccountManager manager = spy(new AccountManager());
        TradeStorage storage = mock(TradeStorage.class);
        ReflectionTestUtils.setField(manager, "tradeStorage", storage);
        Account account = new Account(1L, "Test", "EUR", 1000);
        ((Map<Long, Account>) ReflectionTestUtils.getField(manager, "accounts")).put(1L, account);

        ClosedTrade before = new ClosedTrade();
        before.setCloseTime(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " 16:47:47");
        before.setProfit(43.48);
        before.setCommission(-0.44);
        account.setClosedTrades(List.of(before));
        assertEquals(43.04, manager.getCachedDailyProfit(1L), 0.0001);

        ClosedTrade repaired = new ClosedTrade();
        repaired.setType("SELL");
        repaired.setCloseTime(before.getCloseTime());
        repaired.setProfit(43.48);
        repaired.setCommission(-0.88);
        List<ClosedTrade> incoming = List.of(repaired);
        when(storage.saveClosedTradesWithResult(1L, incoming))
                .thenReturn(new TradeStorage.ClosedTradeSaveResult(0, 1));
        when(storage.loadClosedTrades(1L)).thenReturn(incoming);
        // Validate refresh dispatch independently of performance metric calculation.
        doReturn(Map.of()).when(manager).getOrCalculatePerformanceMetrics(1L);

        int inserted = init ? manager.initTrades(1L, List.of(), incoming, 1000, 1000)
                : manager.updateHistory(1L, incoming);

        assertEquals(0, inserted);
        assertSame(incoming, account.getClosedTrades());
        assertEquals(42.60, manager.getCachedDailyProfit(1L), 0.0001);
        verify(manager).getOrCalculatePerformanceMetrics(1L);
        verify(storage).loadClosedTrades(1L);
    }
}
