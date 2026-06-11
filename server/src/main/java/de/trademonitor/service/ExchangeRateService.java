package de.trademonitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Service
public class ExchangeRateService {

    private static final Logger LOG = Logger.getLogger(ExchangeRateService.class.getName());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache rate values for 30 seconds
    private static final long CACHE_TTL_SECONDS = 30;
    private final ConcurrentHashMap<String, CachedRate> cache = new ConcurrentHashMap<>();

    public static class CachedRate {
        public final double price;
        public final Instant timestamp;

        public CachedRate(double price, Instant timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }

        public double getPrice() {
            return price;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(timestamp.plusSeconds(CACHE_TTL_SECONDS));
        }
    }

    /**
     * Gets the current rate for a symbol (e.g. AUDUSD).
     * Returns 0.0 if not found or on error.
     */
    public double getRate(String symbol) {
        CachedRate details = getRateDetails(symbol);
        return details != null ? details.getPrice() : 0.0;
    }

    /**
     * Gets the full rate details including price and fetch timestamp.
     */
    public CachedRate getRateDetails(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        String cleanSymbol = symbol.trim().toUpperCase();
        if (cleanSymbol.length() > 6 && cleanSymbol.substring(0, 6).matches("^[A-Z]{6}$")) {
            cleanSymbol = cleanSymbol.substring(0, 6);
        }
        
        // 1. Check cache first
        CachedRate cached = cache.get(cleanSymbol);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        // 2. Format Yahoo Symbol (e.g., AUDUSD -> AUDUSD=X)
        String yahooSymbol = cleanSymbol;
        if (cleanSymbol.length() == 6 && cleanSymbol.matches("^[A-Z]+$")) {
            yahooSymbol = cleanSymbol + "=X";
        }

        try {
            LOG.info("Fetching rate for symbol: " + yahooSymbol + " from Yahoo Finance");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://query1.finance.yahoo.com/v8/finance/chart/" + yahooSymbol))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && yahooSymbol.endsWith("=X")) {
                String fallbackSymbol = cleanSymbol.substring(0, 3) + "-" + cleanSymbol.substring(3);
                LOG.info("Yahoo Finance returned status " + response.statusCode() + " for " + yahooSymbol + ", trying fallback: " + fallbackSymbol);
                request = HttpRequest.newBuilder()
                        .uri(URI.create("https://query1.finance.yahoo.com/v8/finance/chart/" + fallbackSymbol))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    yahooSymbol = fallbackSymbol; // Use fallback symbol for success logging/metadata
                }
            }

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode resultNode = root.at("/chart/result/0/meta/regularMarketPrice");
                if (!resultNode.isMissingNode() && resultNode.isNumber()) {
                    double price = resultNode.asDouble();
                    if (price > 0) {
                        CachedRate rate = new CachedRate(price, Instant.now());
                        cache.put(cleanSymbol, rate);
                        return rate;
                    }
                }
            } else {
                LOG.warning("Yahoo Finance returned status " + response.statusCode() + " for " + yahooSymbol);
            }
        } catch (Exception e) {
            LOG.severe("Failed to fetch market rate for symbol " + cleanSymbol + ": " + e.getMessage());
        }

        // Fallback: If fetch fails but we have an expired cached value, return it as a backup
        if (cached != null) {
            LOG.warning("Using expired cached rate as fallback for: " + cleanSymbol);
            return cached;
        }

        return null;
    }
}
