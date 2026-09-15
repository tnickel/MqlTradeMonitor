package de.trademonitor.service;

import de.trademonitor.entity.ClosedTradeEntity;
import de.trademonitor.model.ClosedTrade;
import de.trademonitor.repository.AccountRepository;
import de.trademonitor.repository.ClosedTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradeStorageHistoryRepairTest {
    // Synthetic regression fixture inspired by the screenshot's failure pattern.
    // Entry prices, commissions and metadata are not reconstructed account data.
    private final TradeStorage storage = new TradeStorage();
    private final ClosedTradeRepository repository = mock(ClosedTradeRepository.class);
    private final TelegramService telegram = mock(TelegramService.class);
    private ClosedTradeEntity existing;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(storage, "accountRepository", mock(AccountRepository.class));
        ReflectionTestUtils.setField(storage, "closedTradeRepository", repository);
        ReflectionTestUtils.setField(storage, "telegramService", telegram);
        existing = new ClosedTradeEntity();
        existing.setTicket(249828953L);
        existing.setSymbol("XAUUSD");
        existing.setType("BUY");
        existing.setVolume(0.17);
        existing.setOpenTime("2026.09.14 16:47:47");
        existing.setCloseTime(existing.getOpenTime());
        existing.setOpenPrice(4280.31);
        existing.setClosePrice(4280.31);
        existing.setCommission(-0.44);
        existing.setProfit(43.48);
        existing.setOpenTicks("[\"wrong closing ticks\"]");
        existing.setCandlesM5("[\"old M5\"]");
        existing.setCandlesH1("[\"old H1\"]");
        when(repository.findTicketsByAccountId(1L)).thenReturn(List.of(existing.getTicket()));
        when(repository.findByAccountIdAndTicketIn(1L, List.of(existing.getTicket())))
                .thenReturn(List.of(existing));
    }

    @Test
    void repairsEntryMetadataWithoutChangingClosingDealOrCreatingNotification() {
        ClosedTrade trade = completeTrade();
        trade.setCandlesM5("[\"complete M5\"]");
        trade.setCandlesH1("[]");
        var result = storage.saveClosedTradesWithResult(1L, List.of(trade));

        assertEquals(new TradeStorage.ClosedTradeSaveResult(0, 1), result);
        assertEquals("SELL", existing.getType());
        assertEquals(trade.getOpenTime(), existing.getOpenTime());
        assertEquals(trade.getOpenPrice(), existing.getOpenPrice());
        assertEquals(trade.getOpenTimeMsc(), existing.getOpenTimeMsc());
        assertEquals(-0.88, existing.getCommission());
        assertEquals(42L, existing.getMagicNumber());
        assertEquals("Gold Spike MT5", existing.getComment());
        assertEquals(trade.getOpenOrderSetupTimeMsc(), existing.getOpenOrderSetupTimeMsc());
        assertEquals(trade.getOpenAsk(), existing.getOpenAsk());
        assertEquals(trade.getOpenBid(), existing.getOpenBid());
        assertNull(existing.getOpenTicks());
        assertEquals("[\"complete M5\"]", existing.getCandlesM5());
        assertEquals("[\"old H1\"]", existing.getCandlesH1());
        assertEquals("2026.09.14 16:47:47", existing.getCloseTime());
        assertEquals(4280.31, existing.getClosePrice());
        assertEquals(43.48, existing.getProfit());
        verify(repository).saveAll(List.of(existing));
        verifyNoInteractions(telegram);
    }

    @Test
    void repeatedRepairIsIdempotentAndWorsePayloadCannotOverwriteCorrectEntry() {
        ClosedTrade trade = completeTrade();
        assertEquals(1, storage.saveClosedTradesWithResult(1L, List.of(trade)).updated());
        assertFalse(storage.saveClosedTradesWithResult(1L, List.of(trade)).hasChanges());
        trade.setType("BUY");
        trade.setOpenTime(trade.getCloseTime());
        trade.setOpenPrice(trade.getClosePrice());
        assertEquals(0, storage.saveClosedTradesWithDuplicateCheck(1L, List.of(trade)));
        assertEquals("SELL", existing.getType());
        assertEquals("2026.09.14 12:26:00", existing.getOpenTime());
        verify(repository, times(1)).saveAll(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "same-time", "later-time", "bad-date", "symbol", "close-price", "close-time", "volume", "open-price", "commission"})
    void rejectsUnprovenOrInconsistentReplacement(String invalidField) {
        ClosedTrade trade = completeTrade();
        switch (invalidField) {
            case "type" -> trade.setType("UNKNOWN");
            case "same-time" -> trade.setOpenTime(trade.getCloseTime());
            case "later-time" -> trade.setOpenTime("2026.09.14 17:00:00");
            case "bad-date" -> trade.setOpenTime("2026.02.30 12:00:00");
            case "symbol" -> trade.setSymbol("EURUSD");
            case "close-price" -> trade.setClosePrice(100);
            case "close-time" -> trade.setCloseTime("2026.09.14 16:47:48");
            case "volume" -> trade.setVolume(0.05);
            case "open-price" -> trade.setOpenPrice(Double.NaN);
            case "commission" -> trade.setCommission(Double.NaN);
        }
        assertFalse(storage.saveClosedTradesWithResult(1L, List.of(trade)).hasChanges());
        assertEquals("BUY", existing.getType());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void enrichesMissingCandlesWithoutDiscardingExistingOtherTimeframes() {
        existing.setOpenTime("2026.09.14 12:26:00");
        existing.setOpenPrice(4282.75);
        existing.setCandlesM5("[]");
        ClosedTrade trade = completeTrade();
        trade.setCandlesM5("[\"new M5\"]");
        trade.setCandlesH1(null);
        assertEquals(1, storage.saveClosedTradesWithResult(1L, List.of(trade)).updated());
        assertEquals("[\"new M5\"]", existing.getCandlesM5());
        assertEquals("[\"old H1\"]", existing.getCandlesH1());
        assertEquals("BUY", existing.getType());
    }

    private ClosedTrade completeTrade() {
        ClosedTrade trade = new ClosedTrade();
        trade.setTicket(existing.getTicket());
        trade.setSymbol("XAUUSD");
        trade.setType("SELL");
        trade.setVolume(0.17);
        trade.setOpenTime("2026.09.14 12:26:00");
        trade.setCloseTime("2026.09.14 16:47:47");
        trade.setOpenPrice(4282.75);
        trade.setClosePrice(4280.31);
        trade.setCommission(-0.88);
        trade.setMagicNumber(42L);
        trade.setComment("Gold Spike MT5");
        trade.setOpenTimeMsc(1789388760000L);
        trade.setOpenOrderSetupTimeMsc(1789388759857L);
        trade.setOpenAsk(4282.95);
        trade.setOpenBid(4282.75);
        return trade;
    }
}
