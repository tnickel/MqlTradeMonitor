package de.trademonitor.service;

import de.trademonitor.entity.ClosedTradeEntity;
import de.trademonitor.repository.ClosedTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ExportServiceTest {

    @Mock
    private ClosedTradeRepository closedTradeRepository;

    @InjectMocks
    private ExportService exportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testExportAllTradesToCsvContent() {
        // Given
        ClosedTradeEntity trade = new ClosedTradeEntity();
        trade.setTicket(12345L);
        trade.setSymbol("EURUSD");
        trade.setType("BUY");
        trade.setVolume(0.1);
        trade.setOpenTime("2023.10.01 10:00:00");
        trade.setOpenPrice(1.0500);
        trade.setCloseTime("2023.10.01 11:00:00");
        trade.setClosePrice(1.0600);
        trade.setProfit(100.0);
        trade.setCommission(-2.5);
        trade.setSwap(-0.5);
        trade.setComment("Test Trade");
        trade.setMagicNumber(123);
        trade.setSl(1.0400);

        when(closedTradeRepository.findAll()).thenReturn(Arrays.asList(trade));

        // When
        String csv = exportService.exportAllTradesToCsv();

        // Then
        assertTrue(csv.contains("Ticket,Symbol,Type,Lots,OpenTime,OpenPrice,SL,TP,CloseTime,ClosePrice,Commission,Swap,Profit,Comment,Magic"));
        assertTrue(csv.contains("12345"));
        assertTrue(csv.contains("EURUSD"));
        assertTrue(csv.contains("BUY"));
        assertTrue(csv.contains("2023.10.01 10:00:00"));
        assertTrue(csv.contains("100.00"));
        assertTrue(csv.contains("-2.50"));
        assertTrue(csv.contains("-0.50"));
        assertTrue(csv.contains("Test Trade"));
    }
}
