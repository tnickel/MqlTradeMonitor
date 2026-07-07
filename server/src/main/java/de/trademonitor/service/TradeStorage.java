package de.trademonitor.service;

import de.trademonitor.entity.AccountEntity;
import de.trademonitor.entity.ClosedTradeEntity;
import de.trademonitor.entity.EquitySnapshotEntity;
import de.trademonitor.entity.OpenTradeEntity;
import de.trademonitor.model.ClosedTrade;
import de.trademonitor.model.Trade;
import de.trademonitor.repository.AccountRepository;
import de.trademonitor.repository.ClosedTradeRepository;
import de.trademonitor.repository.EquitySnapshotRepository;
import de.trademonitor.repository.OpenTradeRepository;
import de.trademonitor.repository.LlmAnalysisLogRepository;
import de.trademonitor.entity.LlmAnalysisLogEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persists trade data to H2 database.
 * Replaces the old file-based TradeStorage.
 */
@Service
public class TradeStorage {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(TradeStorage.class.getName());

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClosedTradeRepository closedTradeRepository;

    @Autowired
    private OpenTradeRepository openTradeRepository;

    @Autowired
    private EquitySnapshotRepository equitySnapshotRepository;

    @Autowired
    private LlmAnalysisLogRepository llmAnalysisLogRepository;

    @Autowired
    private de.trademonitor.repository.EaLogEntryRepository eaLogEntryRepository;

    @Autowired
    private de.trademonitor.repository.TimelineRepository timelineRepository;

    /**
     * Timestamp format for equity snapshots (lexicographic ordering works
     * correctly)
     */
    private static final DateTimeFormatter SNAPSHOT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Track last snapshot time per account to avoid saving too frequently (every
     * 60s is enough)
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, LocalDateTime> lastSnapshotTime = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Cache for open trades' maximum drawdown (MAE) values to preserve them across deletion cycles
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Double> ticketMaxDrawdownCache = new java.util.concurrent.ConcurrentHashMap<>();

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
     * Update account name, type, and alarm config.
     */
    public void updateAccountDetails(long accountId, String name, String type,
            boolean alarmEnabled, Double alarmAbs, Double alarmPct, boolean monitored) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setName(name);
            entity.setType(type);
            entity.setOpenProfitAlarmEnabled(alarmEnabled);
            entity.setOpenProfitAlarmAbs(alarmAbs);
            entity.setOpenProfitAlarmPct(alarmPct);
            entity.setMonitored(monitored);
            accountRepository.save(entity);
        }
    }

    /**
     * Update account icon (Base64 data URL).
     */
    public void updateAccountIcon(long accountId, String iconBase64) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setIconBase64(iconBase64);
            accountRepository.save(entity);
        }
    }

    /**
     * Update MetaTrader Info text.
     */
    public void updateMetaTraderInfo(long accountId, String metaTraderInfo) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setMetaTraderInfo(metaTraderInfo);
            accountRepository.save(entity);
        }
    }

    /**
     * Update the eaLogAcceptedAt timestamp for a specific account.
     */
    public void updateAccountEaLogAcceptedAt(long accountId, LocalDateTime timestamp) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setEaLogAcceptedAt(timestamp != null ? timestamp.toString() : null);
            accountRepository.save(entity);
        }
    }

    /**
     * Update magic number max age for a specific account.
     */
    public void updateAccountMagicMaxAge(long accountId, int days) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setMagicNumberMaxAge(days);
            accountRepository.save(entity);
        }
    }

    /**
     * Update magic min trades for a specific account.
     */
    public void updateAccountMagicMinTrades(long accountId, int minTrades) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setMagicMinTrades(minTrades);
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
     * Update server time offset.
     */
    public void updateServerTimeOffset(long accountId, long offsetSeconds) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setServerTimeOffsetSeconds(offsetSeconds);
            accountRepository.save(entity);
        }
    }

    /**
     * Update copier error state.
     */
    public void updateCopierError(long accountId, boolean isError, String errorMessage) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setCopierError(isError);
            entity.setCopierErrorMessage(errorMessage);
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

        // Load only the existing tickets for this account in ONE query (fast)
        List<Long> existingTickets = closedTradeRepository.findTicketsByAccountId(accountId);
        java.util.Set<Long> existingTicketsSet = new java.util.HashSet<>(existingTickets);

        // Collect new trades (skip duplicates via HashSet lookup)
        List<ClosedTradeEntity> toInsert = new ArrayList<>();
        for (ClosedTrade trade : closedTrades) {
            if (!existingTicketsSet.contains(trade.getTicket())) {
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
                entity.setSl(trade.getSl());
                toInsert.add(entity);
                existingTicketsSet.add(trade.getTicket());
            }
        }

        // Batch save all new trades at once
        if (!toInsert.isEmpty()) {
            closedTradeRepository.saveAll(toInsert);
            // Prune maximum drawdown memory cache for closed trades
            for (ClosedTradeEntity e : toInsert) {
                ticketMaxDrawdownCache.remove(accountId + "_" + e.getTicket());
            }
        }

        LOG.info("Account " + accountId + ": " + toInsert.size() + " new closed trades inserted, "
                + (closedTrades.size() - toInsert.size()) + " duplicates skipped (from " + closedTrades.size()
                + " received)");
        return toInsert.size();
    }

    /**
     * Replace all open trades for an account (delete old, insert new).
     */
    @Transactional
    public void replaceOpenTrades(long accountId, List<Trade> trades) {
        // Load existing open trades to preserve maxDrawdown
        List<OpenTradeEntity> existingEntities = openTradeRepository.findByAccountId(accountId);
        Map<Long, Double> existingDrawdowns = new java.util.HashMap<>();
        for (OpenTradeEntity e : existingEntities) {
            existingDrawdowns.put(e.getTicket(), e.getMaxDrawdown());
        }

        openTradeRepository.deleteByAccountId(accountId);
        openTradeRepository.flush(); // Force DELETE to DB before inserting new records
        
        if (trades != null) {
            List<OpenTradeEntity> entities = new ArrayList<>();
            java.util.Set<Long> seenTickets = new java.util.HashSet<>();
            for (Trade trade : trades) {
                if (trade == null) continue;
                if (!seenTickets.add(trade.getTicket())) {
                    LOG.warning("Duplicate open trade ticket " + trade.getTicket() + " for account " + accountId + " skipped.");
                    continue;
                }
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
                
                // Track max drawdown (MAE)
                double currentProfit = trade.getProfit();
                String cacheKey = accountId + "_" + trade.getTicket();
                double prevMaxDD = ticketMaxDrawdownCache.getOrDefault(cacheKey, currentProfit);
                
                if (existingDrawdowns.containsKey(trade.getTicket())) {
                    prevMaxDD = Math.min(prevMaxDD, existingDrawdowns.get(trade.getTicket()));
                }
                
                double newMaxDD = Math.min(prevMaxDD, currentProfit);
                ticketMaxDrawdownCache.put(cacheKey, newMaxDD);
                entity.setMaxDrawdown(newMaxDD);
                
                // Propagate to in-memory model for immediate UI update
                trade.setMaxDrawdown(entity.getMaxDrawdown());
                
                entities.add(entity);
            }
            openTradeRepository.saveAll(entities);
        }
    }

    /**
     * Load all closed trades for an account from DB.
     */
    public List<ClosedTrade> loadClosedTrades(long accountId) {
        List<ClosedTradeEntity> entities = closedTradeRepository.findByAccountIdOrderByCloseTimeDesc(accountId);
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
            trade.setSl(entity.getSl());
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
            trade.setMaxDrawdown(entity.getMaxDrawdown());
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
            entity.setLastSeen(LocalDateTime.now().toString());
            accountRepository.save(entity);
        }
    }

    /**
     * Persist lastSeen only (lightweight heartbeat update).
     */
    public void updateLastSeen(long accountId, LocalDateTime lastSeen) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setLastSeen(lastSeen.toString());
            accountRepository.save(entity);
        }
    }

    /**
     * Save an equity snapshot if at least 60 seconds have passed since the last
     * snapshot for this account.
     * Automatically cleans up snapshots older than 90 days.
     */
    @Transactional
    public void saveEquitySnapshot(long accountId, double equity, double balance) {
        LocalDateTime nowTime = LocalDateTime.now();

        // Rate-limit: require at least 60 real seconds since the last snapshot.
        // (Uses atomic compute block to ensure thread safety under concurrent writes.)
        AtomicBoolean shouldSave = new AtomicBoolean(false);
        lastSnapshotTime.compute(accountId, (k, prev) -> {
            if (prev == null || java.time.temporal.ChronoUnit.SECONDS.between(prev, nowTime) >= 60) {
                shouldSave.set(true);
                return nowTime;
            }
            return prev;
        });

        if (!shouldSave.get()) {
            return;
        }

        String now = nowTime.format(SNAPSHOT_FMT);
        equitySnapshotRepository.save(new EquitySnapshotEntity(accountId, now, equity, balance));

        // Cleanup: delete snapshots older than 90 days
        String cutoff = nowTime.minusDays(90).format(SNAPSHOT_FMT);
        equitySnapshotRepository.deleteOlderThan(accountId, cutoff);
    }

    /**
     * Load all equity snapshots for an account, ordered by timestamp ascending.
     */
    public List<EquitySnapshotEntity> loadEquitySnapshots(long accountId) {
        String cutoff = LocalDateTime.now().minusHours(2).format(SNAPSHOT_FMT);
        return equitySnapshotRepository.findByAccountIdAndTimestampGreaterThanOrMinuteIsZero(accountId, cutoff);
    }

    /**
     * Load equity snapshots for an account within a specific time range.
     */
    public List<EquitySnapshotEntity> loadEquitySnapshotsBetween(long accountId, String from, String to) {
        String cutoff = LocalDateTime.now().minusHours(2).format(SNAPSHOT_FMT);
        return equitySnapshotRepository.findByAccountIdAndTimestampBetweenAndRecentOrMinuteIsZero(accountId, from, to, cutoff);
    }

    /**
     * Delete ALL trade data for an account (open trades, closed trades, equity
     * snapshots).
     * The account entity itself is preserved.
     * After this, the MetaTrader EA should do a "Reconnect Server" to re-send all
     * data.
     */
    @Transactional
    public void resetAccountTrades(long accountId) {
        LOG.info("RESET Account " + accountId + ": Deleting all open trades, closed trades, and equity snapshots.");
        openTradeRepository.deleteByAccountId(accountId);
        closedTradeRepository.deleteByAccountId(accountId);
        equitySnapshotRepository.deleteByAccountId(accountId);
        ticketMaxDrawdownCache.entrySet().removeIf(e -> e.getKey().startsWith(accountId + "_"));
    }

    /**
     * Permanently delete an account and ALL associated data (open/closed trades,
     * equity snapshots, EA logs, timelines, LLM analysis logs and the account
     * entity itself). Used by the "Delete Robot" feature.
     */
    @Transactional
    public void deleteAccount(long accountId) {
        LOG.info("DELETE Account " + accountId + ": Permanently deleting account and all associated data.");
        openTradeRepository.deleteByAccountId(accountId);
        closedTradeRepository.deleteByAccountId(accountId);
        equitySnapshotRepository.deleteByAccountId(accountId);
        ticketMaxDrawdownCache.entrySet().removeIf(e -> e.getKey().startsWith(accountId + "_"));
        lastSnapshotTime.remove(accountId);
        try { eaLogEntryRepository.deleteByAccountId(accountId); } catch (Exception e) { LOG.warning("deleteAccount: failed to delete EA logs for " + accountId + ": " + e.getMessage()); }
        try { timelineRepository.deleteByAccountId(accountId); } catch (Exception e) { LOG.warning("deleteAccount: failed to delete timelines for " + accountId + ": " + e.getMessage()); }
        try { llmAnalysisLogRepository.deleteByAccountId(accountId); } catch (Exception e) { LOG.warning("deleteAccount: failed to delete LLM logs for " + accountId + ": " + e.getMessage()); }
        accountRepository.deleteById(accountId);
    }

    public void updatePromptAnalysisConfig(long accountId, boolean enabled, String customPrompt) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setPromptAnalysisEnabled(enabled);
            entity.setCustomPrompt(customPrompt);
            accountRepository.save(entity);
        }
    }

    @Transactional
    public void updatePromptAnalysisResult(long accountId, String result, java.time.LocalDateTime time) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setLastPromptAnalysisResult(result);
            entity.setLastPromptAnalysisTime(time);
            accountRepository.save(entity);
        }
        try {
            LlmAnalysisLogEntity log = new LlmAnalysisLogEntity(accountId, result, time);
            llmAnalysisLogRepository.save(log);
        } catch (Exception e) {
            LOG.severe("Failed to save LLM analysis log: " + e.getMessage());
        }
    }
}
