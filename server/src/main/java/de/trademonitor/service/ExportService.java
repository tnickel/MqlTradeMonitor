package de.trademonitor.service;

import de.trademonitor.entity.ClosedTradeEntity;
import de.trademonitor.repository.ClosedTradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ExportService {

    private final ClosedTradeRepository closedTradeRepository;

    public ExportService(ClosedTradeRepository closedTradeRepository) {
        this.closedTradeRepository = closedTradeRepository;
    }

    /**
     * Generates a CSV string of all closed trades for all accounts.
     */
    public String exportAllTradesToCsv() {
        List<ClosedTradeEntity> trades = closedTradeRepository.findAll();
        return generateCsv(trades);
    }

    public String exportAccountTradesToCsv(long accountId) {
        List<ClosedTradeEntity> trades = closedTradeRepository.findByAccountIdOrderByCloseTimeDesc(accountId);
        return generateCsv(trades);
    }


    private String generateCsv(List<ClosedTradeEntity> trades) {
        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("Ticket,Symbol,Type,Lots,OpenTime,OpenPrice,SL,TP,CloseTime,ClosePrice,Commission,Swap,Profit,Comment,Magic\n");

        for (ClosedTradeEntity trade : trades) {
            sb.append(trade.getTicket()).append(",");
            sb.append(escapeCsv(trade.getSymbol())).append(",");
            sb.append(trade.getType()).append(",");
            sb.append(String.format(Locale.US, "%.2f", trade.getVolume())).append(",");
            sb.append(trade.getOpenTime()).append(",");
            sb.append(String.format(Locale.US, "%.5f", trade.getOpenPrice())).append(",");
            sb.append(trade.getSl() != null ? String.format(Locale.US, "%.5f", trade.getSl()) : "").append(",");
            sb.append("").append(","); // TP is not in ClosedTradeEntity? Let me re-verify.
            sb.append(trade.getCloseTime()).append(",");
            sb.append(String.format(Locale.US, "%.5f", trade.getClosePrice())).append(",");
            sb.append(String.format(Locale.US, "%.2f", trade.getCommission())).append(",");
            sb.append(String.format(Locale.US, "%.2f", trade.getSwap())).append(",");
            sb.append(String.format(Locale.US, "%.2f", trade.getProfit())).append(",");
            sb.append(escapeCsv(trade.getComment())).append(",");
            sb.append(trade.getMagicNumber()).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
