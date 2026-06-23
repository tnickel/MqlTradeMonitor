package de.trademonitor.service;

import de.trademonitor.model.ClosedTrade;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvImportServiceTest {

    private final CsvImportService csvImportService = new CsvImportService();

    @Test
    void testParseTradesCsv() throws Exception {
        String csvContent = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n" +
                "2026.06.16 21:19:52;Sell;0.01;XAUUSD;4339.93;0.01;2026.06.16 21:42:04;4339.04;-0.08;;0.89\n" +
                "2026.06.15 20:58:58;Buy;0.01;XAUUSD;4323.66;0.01;2026.06.15 21:17:57;4325.3;-0.08;;1.6400000000000001\n" +
                "2026.06.03 23:10:06;Buy;0.01;XAUUSD;4441.69;0.01;2026.06.04 01:56:52;4445.4;-0.08;-1.6099999999999999;3.71\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "trades.csv",
                "text/csv",
                csvContent.getBytes()
        );

        List<ClosedTrade> trades = csvImportService.parseTradesCsv(file);

        assertEquals(3, trades.size());

        // Test first trade (Sell)
        ClosedTrade t1 = trades.get(0);
        assertEquals("XAUUSD", t1.getSymbol());
        assertEquals("SELL", t1.getType());
        assertEquals(0.01, t1.getVolume());
        assertEquals(4339.93, t1.getOpenPrice());
        assertEquals("2026.06.16 21:19:52", t1.getOpenTime());
        assertEquals("2026.06.16 21:42:04", t1.getCloseTime());
        assertEquals(4339.04, t1.getClosePrice());
        assertEquals(-0.08, t1.getCommission());
        assertEquals(0.0, t1.getSwap());
        assertEquals(0.89, t1.getProfit());

        // Test second trade (Buy with long float profit)
        ClosedTrade t2 = trades.get(1);
        assertEquals("BUY", t2.getType());
        assertEquals(4323.66, t2.getOpenPrice());
        assertEquals(4325.3, t2.getClosePrice());
        assertEquals(1.64, t2.getProfit(), 0.00001);

        // Test third trade (Buy with swap)
        ClosedTrade t3 = trades.get(2);
        assertEquals(-1.61, t3.getSwap(), 0.00001);
        assertEquals(3.71, t3.getProfit());
    }

    @Test
    void testParseTradesCsvWithThousandsSeparators() throws Exception {
        String csvContent = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n" +
                "2026.06.16 21:19:52;Sell;0.01;XAUUSD;4.339,93;0.01;2026.06.16 21:42:04;4.339,04;-0,08;;1.234,89 €\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "trades.csv",
                "text/csv",
                csvContent.getBytes()
        );

        List<ClosedTrade> trades = csvImportService.parseTradesCsv(file);

        assertEquals(1, trades.size());
        ClosedTrade t1 = trades.get(0);
        assertEquals(4339.93, t1.getOpenPrice());
        assertEquals(4339.04, t1.getClosePrice());
        assertEquals(-0.08, t1.getCommission());
        assertEquals(1234.89, t1.getProfit());
    }

    @Test
    void testDeterministicTicketsForTicketlessCsv() throws Exception {
        String csvContent = "Time;Type;Volume;Symbol;Price;Volume;Time;Price;Commission;Swap;Profit\n" +
                "2026.06.16 21:19:52;Sell;0.01;XAUUSD;4339.93;0.01;2026.06.16 21:42:04;4339.04;-0.08;;0.89\n" +
                "2026.06.16 21:19:52;Sell;0.01;XAUUSD;4339.93;0.01;2026.06.16 21:42:04;4339.04;-0.08;;0.89\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "trades.csv",
                "text/csv",
                csvContent.getBytes()
        );

        List<ClosedTrade> trades1 = csvImportService.parseTradesCsv(file);
        List<ClosedTrade> trades2 = csvImportService.parseTradesCsv(file);

        assertEquals(2, trades1.size());
        assertEquals(2, trades2.size());

        // Check tickets are deterministic (same on re-import)
        assertEquals(trades1.get(0).getTicket(), trades2.get(0).getTicket());
        assertEquals(trades1.get(1).getTicket(), trades2.get(1).getTicket());

        // Check tickets are distinct for different rows in same file
        assertNotEquals(trades1.get(0).getTicket(), trades1.get(1).getTicket());
    }
}
