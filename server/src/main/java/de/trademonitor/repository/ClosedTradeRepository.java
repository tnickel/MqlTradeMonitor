package de.trademonitor.repository;

import de.trademonitor.entity.ClosedTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ClosedTradeRepository extends JpaRepository<ClosedTradeEntity, Long> {

    List<ClosedTradeEntity> findByAccountId(long accountId);

    List<ClosedTradeEntity> findByAccountIdIn(java.util.Collection<Long> accountIds);

    List<ClosedTradeEntity> findByAccountIdOrderByCloseTimeDesc(long accountId);

    boolean existsByAccountIdAndTicket(long accountId, long ticket);

    java.util.Optional<ClosedTradeEntity> findByAccountIdAndTicket(long accountId, long ticket);

    List<ClosedTradeEntity> findByAccountIdAndTicketIn(long accountId, java.util.Collection<Long> tickets);

    @Query("SELECT c.ticket FROM ClosedTradeEntity c WHERE c.accountId = :accountId")
    List<Long> findTicketsByAccountId(@org.springframework.data.repository.query.Param("accountId") long accountId);

    long countByAccountId(long accountId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ClosedTradeEntity c WHERE c.accountId = :accountId")
    void deleteByAccountId(long accountId);

    @Query("SELECT MIN(c.closeTime) FROM ClosedTradeEntity c WHERE c.accountId = ?1")
    String findMinCloseTimeByAccountId(long accountId);

    @Query("SELECT MAX(c.closeTime) FROM ClosedTradeEntity c WHERE c.accountId = ?1")
    String findMaxCloseTimeByAccountId(long accountId);

    @Query("SELECT MIN(c.closeTime) FROM ClosedTradeEntity c WHERE c.accountId IN ?1")
    String findMinCloseTimeByAccountIds(java.util.Collection<Long> accountIds);

    @Query("SELECT MAX(c.closeTime) FROM ClosedTradeEntity c WHERE c.accountId IN ?1")
    String findMaxCloseTimeByAccountIds(java.util.Collection<Long> accountIds);

    @Query("SELECT COUNT(c) FROM ClosedTradeEntity c WHERE c.accountId IN ?1")
    long countByAccountIds(java.util.Collection<Long> accountIds);

    java.util.Optional<ClosedTradeEntity> findFirstByMagicNumber(Long magicNumber);

    /**
     * Sum profit for a specific account where closeTime starts with the given prefix.
     * Useful for daily (prefix="2026.04.01") or monthly (prefix="2026.04") aggregation.
     */
    @Query("SELECT COALESCE(SUM(c.profit), 0) FROM ClosedTradeEntity c WHERE c.accountId = ?1 AND c.closeTime LIKE CONCAT(?2, '%')")
    double sumProfitByAccountIdAndCloseTimePrefix(long accountId, String prefix);

    /**
     * Count trades for a specific account where closeTime starts with the given prefix.
     */
    @Query("SELECT COUNT(c) FROM ClosedTradeEntity c WHERE c.accountId = ?1 AND c.closeTime LIKE CONCAT(?2, '%')")
    long countByAccountIdAndCloseTimePrefix(long accountId, String prefix);

    /**
     * Batch: Get trade count and sum profit per account for trades matching a closeTime prefix.
     * Returns rows of [accountId, count, sumProfit].
     */
    @Query("SELECT c.accountId, COUNT(c), COALESCE(SUM(c.profit), 0) FROM ClosedTradeEntity c WHERE c.accountId IN ?1 AND c.closeTime LIKE CONCAT(?2, '%') GROUP BY c.accountId")
    java.util.List<Object[]> aggregateByAccountIdsAndPrefix(java.util.Collection<Long> accountIds, String prefix);

    @Query("SELECT c.accountId, COUNT(c), COALESCE(SUM(c.profit), 0) FROM ClosedTradeEntity c WHERE c.accountId IN ?1 AND c.closeTime >= ?2 AND c.closeTime <= ?3 GROUP BY c.accountId")
    java.util.List<Object[]> aggregateByAccountIdsAndDateRange(java.util.Collection<Long> accountIds, String startCloseTime, String endCloseTime);

    @Query("SELECT c FROM ClosedTradeEntity c WHERE c.accountId IN ?1 AND c.closeTime >= ?2 AND c.closeTime <= ?3")
    List<ClosedTradeEntity> findByAccountIdsAndDateRange(java.util.Collection<Long> accountIds, String startCloseTime, String endCloseTime);

    @Query("SELECT c FROM ClosedTradeEntity c WHERE c.accountId = ?1 AND c.closeTime >= ?2 AND c.closeTime <= ?3")
    List<ClosedTradeEntity> findByAccountIdAndDateRange(long accountId, String startCloseTime, String endCloseTime);

    @Query("SELECT c FROM ClosedTradeEntity c WHERE c.symbol = :symbol AND " +
           "((:isOpen = true AND c.openTimeMsc >= :minTime AND c.openTimeMsc <= :maxTime) OR " +
           " (:isOpen = false AND c.closeTimeMsc >= :minTime AND c.closeTimeMsc <= :maxTime))")
    List<ClosedTradeEntity> findTradesForComparison(
            @org.springframework.data.repository.query.Param("symbol") String symbol,
            @org.springframework.data.repository.query.Param("isOpen") boolean isOpen,
            @org.springframework.data.repository.query.Param("minTime") long minTime,
            @org.springframework.data.repository.query.Param("maxTime") long maxTime);
}
