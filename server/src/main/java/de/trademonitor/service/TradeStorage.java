package de.trademonitor.service;

import de.trademonitor.entity.AccountEntity;
import de.trademonitor.entity.ClosedTradeEntity;
import de.trademonitor.entity.OpenTradeEntity;
import de.trademonitor.model.ClosedTrade;
import de.trademonitor.model.Trade;
import de.trademonitor.repository.AccountRepository;
import de.trademonitor.repository.ClosedTradeRepository;
import de.trademonitor.repository.OpenTradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists trade data to H2 database.
 * Replaces the old file-based TradeStorage.
 */
@Service
public class TradeStorage {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClosedTradeRepository closedTradeRepository;

    @Autowired
    private OpenTradeRepository openTradeRepository;

    /**
     * Save or update account in DB.
     */
    public void saveAccount(long accountId, String broker, String currency, double balance) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity == null) {
            entity = new AccountEntity(accountId, broker, currency, balance);
            entity.setRegisteredAt(java.time.LocalDateTime.now().toString());
        } else {
            entity.setBroker(broker);
            entity.setCurrency(currency);
            entity.setBalance(balance);
        }
        entity.setLastSeen(java.time.LocalDateTime.now().toString());
        accountRepository.save(entity);
    }

    /**
     * Update account name and type.
     */
    public void updateAccountDetails(long accountId, String name, String type) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setName(name);
            entity.setType(type);
            accountRepository.save(entity);
        }
    }

    /**
     * Update account layout preferences.
     */
    public void updateAccountLayout(long accountId, String section, int displayOrder) {
        updateAccountLayout(accountId, section, displayOrder, null);
    }

    public void updateAccountLayout(long accountId, String section, int displayOrder, Long sectionId) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            if (section != null)
                entity.setSection(section);
            if (sectionId != null)
                entity.setSectionId(sectionId);
            entity.setDisplayOrder(displayOrder);
            accountRepository.save(entity);
        }
    }

    /**
     * Save closed trades with duplicate check.
     * Only trades that don't already exist (by accountId + ticket) are inserted.
     * Returns the number of newly inserted trades.
     */
    @Transactional
    public int saveClosedTradesWithDuplicateCheck(long accountId, List<ClosedTrade> closedTrades) {
        if (closedTrades == null || closedTrades.isEmpty()) {
            return 0;
        }

        // Load all existing tickets for this account in ONE query (fast)
        List<ClosedTradeEntity> existing = closedTradeRepository.findByAccountId(accountId);
        java.util.Set<Long> existingTickets = new java.util.HashSet<>();
        for (ClosedTradeEntity e : existing) {
            existingTickets.add(e.getTicket());
        }

        // Collect new trades (skip duplicates via HashSet lookup)
        List<ClosedTradeEntity> toInsert = new ArrayList<>();
        for (ClosedTrade trade : closedTrades) {
            if (!existingTickets.contains(trade.getTicket())) {
                ClosedTradeEntity entity = new ClosedTradeEntity();
                entity.setAccountId(accountId);
                entity.setTicket(trade.getTicket());
                entity.setSymbol(trade.getSymbol());
                entity.setType(trade.getType());
                entity.setVolume(trade.getVolume());
                entity.setOpenPrice(trade.getOpenPrice());
                entity.setClosePrice(trade.getClosePrice());
                entity.setOpenTime(trade.getOpenTime());
                entity.setCloseTime(trade.getCloseTime());
                entity.setProfit(trade.getProfit());
                entity.setSwap(trade.getSwap());
                entity.setCommission(trade.getCommission());
                entity.setMagicNumber(trade.getMagicNumber());
                entity.setComment(trade.getComment());
                toInsert.add(entity);
            }
        }

        // Batch save all new trades at once
        if (!toInsert.isEmpty()) {
            closedTradeRepository.saveAll(toInsert);
        }

        System.out.println("Account " + accountId + ": " + toInsert.size() + " new closed trades inserted, "
                + (closedTrades.size() - toInsert.size()) + " duplicates skipped (from " + closedTrades.size()
                + " received)");
        return toInsert.size();
    }

    /**
     * Replace all open trades for an account (delete old, insert new).
     */
    @Transactional
    public void replaceOpenTrades(long accountId, List<Trade> trades) {
        openTradeRepository.deleteByAccountId(accountId);
        openTradeRepository.flush(); // Force DELETE to DB before inserting new records
        if (trades != null) {
            List<OpenTradeEntity> entities = new ArrayList<>();
            for (Trade trade : trades) {
                OpenTradeEntity entity = new OpenTradeEntity();
                entity.setAccountId(accountId);
                entity.setTicket(trade.getTicket());
                entity.setSymbol(trade.getSymbol());
                entity.setType(trade.getType());
                entity.setVolume(trade.getVolume());
                entity.setOpenPrice(trade.getOpenPrice());
                entity.setOpenTime(trade.getOpenTime());
                entity.setStopLoss(trade.getStopLoss());
                entity.setTakeProfit(trade.getTakeProfit());
                entity.setProfit(trade.getProfit());
                entity.setSwap(trade.getSwap());
                entity.setMagicNumber(trade.getMagicNumber());
                entity.setComment(trade.getComment());
                entities.add(entity);
            }
            openTradeRepository.saveAll(entities);
        }
    }

    /**
     * Load all closed trades for an account from DB.
     */
    public List<ClosedTrade> loadClosedTrades(long accountId) {
        List<ClosedTradeEntity> entities = closedTradeRepository.findByAccountId(accountId);
        List<ClosedTrade> result = new ArrayList<>();
        for (ClosedTradeEntity entity : entities) {
            ClosedTrade trade = new ClosedTrade();
            trade.setTicket(entity.getTicket());
            trade.setSymbol(entity.getSymbol());
            trade.setType(entity.getType());
            trade.setVolume(entity.getVolume());
            trade.setOpenPrice(entity.getOpenPrice());
            trade.setClosePrice(entity.getClosePrice());
            trade.setOpenTime(entity.getOpenTime());
            trade.setCloseTime(entity.getCloseTime());
            trade.setProfit(entity.getProfit());
            trade.setSwap(entity.getSwap());
            trade.setCommission(entity.getCommission());
            trade.setMagicNumber(entity.getMagicNumber());
            trade.setComment(entity.getComment());
            result.add(trade);
        }
        return result;
    }

    /**
     * Load all open trades for an account from DB.
     */
    public List<Trade> loadOpenTrades(long accountId) {
        List<OpenTradeEntity> entities = openTradeRepository.findByAccountId(accountId);
        List<Trade> result = new ArrayList<>();
        for (OpenTradeEntity entity : entities) {
            Trade trade = new Trade();
            trade.setTicket(entity.getTicket());
            trade.setSymbol(entity.getSymbol());
            trade.setType(entity.getType());
            trade.setVolume(entity.getVolume());
            trade.setOpenPrice(entity.getOpenPrice());
            trade.setOpenTime(entity.getOpenTime());
            trade.setStopLoss(entity.getStopLoss());
            trade.setTakeProfit(entity.getTakeProfit());
            trade.setProfit(entity.getProfit());
            trade.setSwap(entity.getSwap());
            trade.setMagicNumber(entity.getMagicNumber());
            trade.setComment(entity.getComment());
            result.add(trade);
        }
        return result;
    }

    /**
     * Load all persisted accounts from DB.
     */
    public List<AccountEntity> loadAllAccounts() {
        return accountRepository.findAll();
    }

    /**
     * Update account metrics in DB.
     */
    public void updateAccountMetrics(long accountId, double equity, double balance) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setEquity(equity);
            entity.setBalance(balance);
            entity.setLastSeen(java.time.LocalDateTime.now().toString());
            accountRepository.save(entity);
        }
    }
}
