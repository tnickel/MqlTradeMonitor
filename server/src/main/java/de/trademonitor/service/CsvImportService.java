package de.trademonitor.service;

import de.trademonitor.model.ClosedTrade;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CsvImportService {

    public List<ClosedTrade> parseTradesCsv(MultipartFile file) throws Exception {
        List<ClosedTrade> trades = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            String separator = null;
            String[] headers = null;
            boolean hasTicketColumn = false;

            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                lineNum++;
                if (lineNum == 1) {
                    // Auto-detect separator
                    if (line.contains(";")) separator = ";";
                    else if (line.contains(",")) separator = ",";
                    else if (line.contains("\t")) separator = "\t";
                    else separator = ";"; // fallback to semicolon as it is common in German locale MT5

                    headers = splitCsvLine(line, separator);
                    for (int i = 0; i < headers.length; i++) {
                        headers[i] = headers[i].trim().toLowerCase().replaceAll("[^a-z0-9/ _-]", "");
                        if (isHeaderFor(headers[i], "ticket", "position", "id", "order", "deal")) {
                            hasTicketColumn = true;
                        }
                    }
                    continue;
                }

                String[] values = splitCsvLine(line, separator);
                if (values.length == 0) continue;

                ClosedTrade trade = new ClosedTrade();
                // Set defaults
                long defaultTicket = System.currentTimeMillis() * 100 + trades.size();
                trade.setTicket(defaultTicket);
                trade.setSymbol("EURUSD");
                trade.setType("BUY");
                trade.setVolume(0.01);
                trade.setOpenTime("");
                trade.setCloseTime("");

                // Track occurrences of duplicate headers like Time, Price, Volume
                Map<String, Integer> headerOccurrences = new HashMap<>();

                for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                    String header = headers[i];
                    String val = values[i].trim();
                    if (val.isEmpty()) continue;

                    // Increment occurrence count for this header
                    headerOccurrences.put(header, headerOccurrences.getOrDefault(header, 0) + 1);
                    int occurrence = headerOccurrences.get(header);

                    try {
                        if (isHeaderFor(header, "ticket", "position", "id", "order", "deal")) {
                            trade.setTicket(parseLong(val, defaultTicket));
                        } else if (isHeaderFor(header, "symbol", "item", "asset")) {
                            trade.setSymbol(val.toUpperCase());
                        } else if (isHeaderFor(header, "type", "action", "direction")) {
                            String upper = val.toUpperCase();
                            if (upper.contains("SELL") || upper.contains("SHORT")) {
                                trade.setType("SELL");
                            } else {
                                trade.setType("BUY");
                            }
                        } else if (isHeaderFor(header, "volume", "lots", "size")) {
                            // First occurrence is open volume (usually just Volume)
                            if (occurrence == 1) {
                                trade.setVolume(parseDouble(val, 0.01));
                            }
                        } else if (isHeaderFor(header, "openprice", "open price")) {
                            trade.setOpenPrice(parseDouble(val, 0.0));
                        } else if (isHeaderFor(header, "price")) {
                            // In Metatrader export: 1st Price is Open Price, 2nd Price is Close Price
                            if (occurrence == 1) {
                                trade.setOpenPrice(parseDouble(val, 0.0));
                            } else if (occurrence == 2) {
                                trade.setClosePrice(parseDouble(val, 0.0));
                            }
                        } else if (isHeaderFor(header, "opentime", "open time")) {
                            trade.setOpenTime(normalizeDateTime(val));
                        } else if (isHeaderFor(header, "time")) {
                            // In Metatrader export: 1st Time is Open Time, 2nd Time is Close Time
                            if (occurrence == 1) {
                                trade.setOpenTime(normalizeDateTime(val));
                            } else if (occurrence == 2) {
                                trade.setCloseTime(normalizeDateTime(val));
                            }
                        } else if (isHeaderFor(header, "closeprice", "close price")) {
                            trade.setClosePrice(parseDouble(val, 0.0));
                        } else if (isHeaderFor(header, "closetime", "close time")) {
                            trade.setCloseTime(normalizeDateTime(val));
                        } else if (isHeaderFor(header, "sl", "s/l", "stop loss", "stoploss")) {
                            trade.setSl(parseDouble(val, 0.0));
                        } else if (isHeaderFor(header, "commission", "commissions", "fee", "fees")) {
                            trade.setCommission(parseDouble(val, 0.0));
                        } else if (isHeaderFor(header, "swap", "swaps", "rollover")) {
                            trade.setSwap(parseDouble(val, 0.0));
                        } else if (isHeaderFor(header, "profit", "pl", "pnl", "gain")) {
                            trade.setProfit(parseDouble(val, 0.0));
                        } else if (isHeaderFor(header, "comment", "comments")) {
                            trade.setComment(val);
                        } else if (isHeaderFor(header, "magic", "magic number", "magicnumber")) {
                            trade.setMagicNumber(parseLong(val, 0L));
                        }
                    } catch (Exception ignored) {
                    }
                }

                // Sanity checks
                if (trade.getOpenTime() == null || trade.getOpenTime().isEmpty()) {
                    trade.setOpenTime("2026.01.01 00:00:00");
                }
                if (trade.getCloseTime() == null || trade.getCloseTime().isEmpty()) {
                    trade.setCloseTime(trade.getOpenTime());
                }

                if (!hasTicketColumn) {
                    trade.setTicket(generateDeterministicTicket(trade, trades.size()));
                }

                trades.add(trade);
            }
        }
        return trades;
    }

    private boolean isHeaderFor(String header, String... candidates) {
        for (String c : candidates) {
            String normC = c.toLowerCase().replaceAll("[^a-z0-9/ _-]", "");
            if (header.equals(normC) || header.contains(normC)) {
                return true;
            }
        }
        return false;
    }

    private String[] splitCsvLine(String line, String separator) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char sepChar = separator.charAt(0);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == sepChar && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    private double parseDouble(String val, double defaultValue) {
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            String clean = val.trim();
            int lastComma = clean.lastIndexOf(',');
            int lastDot = clean.lastIndexOf('.');
            
            char decimalSeparator = 0;
            if (lastComma >= 0 && lastDot >= 0) {
                decimalSeparator = lastComma > lastDot ? ',' : '.';
            } else if (lastComma >= 0) {
                decimalSeparator = ',';
            } else if (lastDot >= 0) {
                decimalSeparator = '.';
            }
            
            if (decimalSeparator == ',') {
                clean = clean.replace(".", "").replaceAll("[\\s]", "");
                clean = clean.replace(",", ".");
            } else if (decimalSeparator == '.') {
                clean = clean.replace(",", "").replaceAll("[\\s]", "");
            } else {
                clean = clean.replaceAll("[\\s]", "");
            }
            
            clean = clean.replaceAll("[^0-9.-]", "");
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long generateDeterministicTicket(ClosedTrade trade, int index) {
        String key = (trade.getSymbol() != null ? trade.getSymbol() : "") + "_" 
                   + (trade.getOpenTime() != null ? trade.getOpenTime() : "") + "_" 
                   + (trade.getCloseTime() != null ? trade.getCloseTime() : "") + "_" 
                   + trade.getVolume() + "_" 
                   + trade.getProfit() + "_" 
                   + (trade.getType() != null ? trade.getType() : "") + "_" 
                   + trade.getSwap() + "_" 
                   + trade.getCommission() + "_"
                   + index;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (hash[i] & 0xff);
            }
            return Math.abs(result);
        } catch (Exception e) {
            return Math.abs((long) key.hashCode());
        }
    }

    private long parseLong(String val, long defaultValue) {
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            String clean = val.replaceAll("[^0-9-]", "").trim();
            return Long.parseLong(clean);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String normalizeDateTime(String val) {
        String clean = val.trim().replace("-", ".").replace("/", ".");
        if (clean.matches("^\\d{2}\\.\\d{2}\\.\\d{4}\\s+.*")) {
            String day = clean.substring(0, 2);
            String month = clean.substring(3, 5);
            String year = clean.substring(6, 10);
            String rest = clean.substring(10);
            return year + "." + month + "." + day + rest;
        }
        return clean;
    }
}
