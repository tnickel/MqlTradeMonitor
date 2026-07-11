package de.trademonitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class HistoricalRatesService {

    private static final Logger LOG = Logger.getLogger(HistoricalRatesService.class.getName());

    @Autowired
    private AccountManager accountManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class Candle {
        public final long time; // milliseconds UTC
        public final double open;
        public final double high;
        public final double low;
        public final double close;

        public Candle(long time, double open, double high, double low, double close) {
            this.time = time;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }

        public long getTime() { return time; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
    }

    /**
     * Fetches historical candles directly from stored MetaTrader data in H2 database.
     */
    public List<Candle> getHistoricalRates(long accountId, String symbol, long openTimeMsc, long closeTimeMsc) {
        return getHistoricalRates(accountId, symbol, openTimeMsc, closeTimeMsc, "trade");
    }

    public List<Candle> getHistoricalRates(long accountId, String symbol, long openTimeMsc, long closeTimeMsc, String range) {
        return getHistoricalRates(accountId, symbol, openTimeMsc, closeTimeMsc, range, null);
    }

    public List<Candle> getHistoricalRates(long accountId, String symbol, long openTimeMsc, long closeTimeMsc, String range, de.trademonitor.entity.ClosedTradeEntity tradeEntity) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Get GMT Offset of the broker (in seconds)
        Long offsetSeconds = 0L;
        de.trademonitor.model.Account account = accountManager.getAccount(accountId);
        if (account != null && account.getServerTimeOffsetSeconds() != null) {
            offsetSeconds = account.getServerTimeOffsetSeconds();
        }

        // Check if database contains native candles from MetaTrader
        String candlesJson = null;
        if (tradeEntity != null) {
            if ("day".equalsIgnoreCase(range)) {
                candlesJson = tradeEntity.getCandlesM15();
            } else if ("week".equalsIgnoreCase(range)) {
                candlesJson = tradeEntity.getCandlesH1();
            } else {
                candlesJson = tradeEntity.getCandlesM5();
            }
        }

        if (candlesJson != null && !candlesJson.trim().isEmpty() && !candlesJson.equals("[]")) {
            try {
                JsonNode root = objectMapper.readTree(candlesJson);
                if (root.isArray()) {
                    List<Candle> candles = new ArrayList<>();
                    for (JsonNode node : root) {
                        long brokerTimeMsc = node.get("time").asLong();
                        long utcTimeMsc = brokerTimeMsc - (offsetSeconds * 1000);
                        
                        candles.add(new Candle(
                                utcTimeMsc,
                                node.get("open").asDouble(),
                                node.get("high").asDouble(),
                                node.get("low").asDouble(),
                                node.get("close").asDouble()
                        ));
                    }
                    if (!candles.isEmpty()) {
                        LOG.info("Returning native MT5 candles for trade: " + tradeEntity.getTicket());
                        return candles;
                    }
                }
            } catch (Exception e) {
                LOG.warning("Failed to parse native MT5 candles for trade " + tradeEntity.getTicket() + ": " + e.getMessage());
            }
        }

        return new ArrayList<>();
    }
}
