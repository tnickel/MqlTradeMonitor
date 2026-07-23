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
    private TelegramService telegramService;

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

    public void saveAccount(long accountId, Long realAccountId, String computerName, String loginName, String eaVersion, String platform, String broker, String currency, double balance) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity == null) {
            entity = new AccountEntity(accountId, broker, currency, balance);
            entity.setRegisteredAt(java.time.LocalDateTime.now().toString());
        } else {
            entity.setBroker(broker);
            entity.setCurrency(currency);
            entity.setBalance(balance);
        }
        entity.setRealAccountId(realAccountId);
        entity.setComputerName(computerName);
        entity.setLoginName(loginName);
        entity.setEaVersion(eaVersion);
        entity.setPlatform(platform);
        entity.setLastSeen(java.time.LocalDateTime.now().toString());
        accountRepository.save(entity);
    }

    /**
     * Update account name, type, and alarm config.
     */
    public void updateAccountDetails(long accountId, String name, String type,
            boolean alarmEnabled, Double alarmAbs, Double alarmPct, boolean monitored, boolean telegramTradesEnabled) {
        AccountEntity entity = accountRepository.findById(accountId).orElse(null);
        if (entity != null) {
            entity.setName(name);
            entity.setType(type);
            entity.setOpenProfitAlarmEnabled(alarmEnabled);
            entity.setOpenProfitAlarmAbs(alarmAbs);
            entity.setOpenProfitAlarmPct(alarmPct);
            entity.setMonitored(monitored);
            entity.setTelegramTradesEnabled(telegramTradesEnabled);
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
        List<ClosedTradeEntity> toUpdate = new ArrayList<>();

        AccountEntity ae = accountRepository.findById(accountId).orElse(null);
        boolean telegramEnabled = ae != null && Boolean.TRUE.equals(ae.getTelegramTradesEnabled());

        java.util.Map<Long, ClosedTradeEntity> existingEntitiesMap = new java.util.HashMap<>();
        if (!existingTicketsSet.isEmpty()) {
            List<ClosedTradeEntity> existingEntities = closedTradeRepository.findByAccountIdAndTicketIn(accountId, existingTickets);
            for (ClosedTradeEntity ext : existingEntities) {
                existingEntitiesMap.put(ext.getTicket(), ext);
            }
        }

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
                entity.setOpenTimeMsc(trade.getOpenTimeMsc());
                entity.setCloseTimeMsc(trade.getCloseTimeMsc());
                entity.setOpenAsk(trade.getOpenAsk());
                entity.setOpenBid(trade.getOpenBid());
                entity.setCloseAsk(trade.getCloseAsk());
                entity.setCloseBid(trade.getCloseBid());
                entity.setOpenOrderSetupTimeMsc(trade.getOpenOrderSetupTimeMsc());
                entity.setCloseOrderSetupTimeMsc(trade.getCloseOrderSetupTimeMsc());
                entity.setOpenTicks(trade.getOpenTicks());
                entity.setCloseTicks(trade.getCloseTicks());
                entity.setCandlesM5(trade.getCandlesM5());
                entity.setCandlesM15(trade.getCandlesM15());
                entity.setCandlesH1(trade.getCandlesH1());
                toInsert.add(entity);
                existingTicketsSet.add(trade.getTicket());

                if (telegramEnabled) {
                    try {
                        String accountName = ae.getName() != null ? ae.getName() : String.valueOf(accountId);
                        String typeStr = trade.getType();
                        String profitSign = trade.getProfit() >= 0 ? "+" : "";
                        String currencyStr = ae.getCurrency() != null ? " " + ae.getCurrency() : "";
                        telegramService.sendNotification("✅ *Trade geschlossen (" + accountName + ")*:\n"
                                + "Ticket: #" + trade.getTicket() + "\n"
                                + "Symbol: " + trade.getSymbol() + "\n"
                                + "Typ: " + typeStr + "\n"
                                + "Volumen: " + String.format("%.2f", trade.getVolume()) + " Lot\n"
                                + "Einstieg: " + String.format("%.5f", trade.getOpenPrice()) + "\n"
                                + "Ausstieg: " + String.format("%.5f", trade.getClosePrice()) + "\n"
                                + "Gewinn: " + profitSign + String.format("%.2f", trade.getProfit()) + currencyStr + "\n"
                                + (trade.getComment() != null && !trade.getComment().trim().isEmpty() ? "Comment: " + trade.getComment() : ""));
                    } catch (Exception ex) {
                        LOG.warning("Failed to send Telegram for closed trade: " + ex.getMessage());
                    }
                }
            } else {
                ClosedTradeEntity existingEntity = existingEntitiesMap.get(trade.getTicket());
                if (existingEntity != null && 
                    (existingEntity.getCandlesM5() == null || existingEntity.getCandlesM5().trim().isEmpty() || existingEntity.getCandlesM5().equals("[]")) &&
                    trade.getCandlesM5() != null && !trade.getCandlesM5().trim().isEmpty() && !trade.getCandlesM5().equals("[]")) {
                    
                    existingEntity.setCandlesM5(trade.getCandlesM5());
                    existingEntity.setCandlesM15(trade.getCandlesM15());
                    existingEntity.setCandlesH1(trade.getCandlesH1());
                    toUpdate.add(existingEntity);
                }
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
        if (!toUpdate.isEmpty()) {
            closedTradeRepository.saveAll(toUpdate);
            LOG.info("Account " + accountId + ": " + toUpdate.size() + " existing closed trades updated with native candles");
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
        java.util.Set<Long> existingTickets = new java.util.HashSet<>();
        for (OpenTradeEntity e : existingEntities) {
            existingDrawdowns.put(e.getTicket(), e.getMaxDrawdown());
            existingTickets.add(e.getTicket());
        }

        openTradeRepository.deleteByAccountId(accountId);
        openTradeRepository.flush(); // Force DELETE to DB before inserting new records
        
        if (trades != null) {
            List<OpenTradeEntity> entities = new ArrayList<>();
            java.util.Set<Long> seenTickets = new java.util.HashSet<>();
            
            AccountEntity ae = accountRepository.findById(accountId).orElse(null);
            boolean telegramEnabled = ae != null && Boolean.TRUE.equals(ae.getTelegramTradesEnabled());

            for (Trade trade : trades) {
                if (trade == null) continue;
                if (!seenTickets.add(trade.getTicket())) {
                    LOG.warning("Duplicate open trade ticket " + trade.getTicket() + " for account " + accountId + " skipped.");
                    continue;
                }

                if (!existingTickets.contains(trade.getTicket())) {
                    if (telegramEnabled) {
                        try {
                            String accountName = ae.getName() != null ? ae.getName() : String.valueOf(accountId);
                            String typeStr = trade.getType();
                            telegramService.sendNotification("🆕 *Neuer Trade geöffnet (" + accountName + ")*:\n"
                                    + "Ticket: #" + trade.getTicket() + "\n"
                                    + "Symbol: " + trade.getSymbol() + "\n"
                                    + "Typ: " + typeStr + "\n"
                                    + "Volumen: " + String.format("%.2f", trade.getVolume()) + " Lot\n"
                                    + "Preis: " + String.format("%.5f", trade.getOpenPrice()) + "\n"
                                    + (trade.getMagicNumber() > 0 ? "Magic: " + trade.getMagicNumber() + "\n" : "")
                                    + (trade.getComment() != null && !trade.getComment().trim().isEmpty() ? "Comment: " + trade.getComment() : ""));
                        } catch (Exception ex) {
                            LOG.warning("Failed to send Telegram for new trade: " + ex.getMessage());
                        }
                    }
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
            trade.setOpenTimeMsc(entity.getOpenTimeMsc());
            trade.setCloseTimeMsc(entity.getCloseTimeMsc());
            trade.setOpenAsk(entity.getOpenAsk());
            trade.setOpenBid(entity.getOpenBid());
            trade.setCloseAsk(entity.getCloseAsk());
            trade.setCloseBid(entity.getCloseBid());
            trade.setOpenOrderSetupTimeMsc(entity.getOpenOrderSetupTimeMsc());
            trade.setCloseOrderSetupTimeMsc(entity.getCloseOrderSetupTimeMsc());
            trade.setOpenTicks(entity.getOpenTicks());
            trade.setCloseTicks(entity.getCloseTicks());
            trade.setCandlesM5(entity.getCandlesM5());
            trade.setCandlesM15(entity.getCandlesM15());
            trade.setCandlesH1(entity.getCandlesH1());
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

    @Autowired
    private de.trademonitor.repository.AccountDocumentRepository accountDocumentRepository;

    @Autowired
    private de.trademonitor.repository.AccountLinkRepository accountLinkRepository;

    /**
     * Permanently delete an account and ALL associated data (open/closed trades,
     * equity snapshots, EA logs, timelines, LLM analysis logs, documents, links and the account
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
        try { accountDocumentRepository.deleteByAccountId(accountId); } catch (Exception e) { LOG.warning("deleteAccount: failed to delete documents for " + accountId + ": " + e.getMessage()); }
        try { accountLinkRepository.deleteByAccountId(accountId); } catch (Exception e) { LOG.warning("deleteAccount: failed to delete links for " + accountId + ": " + e.getMessage()); }
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
