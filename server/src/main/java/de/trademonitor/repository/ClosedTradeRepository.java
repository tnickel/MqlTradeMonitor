package de.trademonitor.repository;

import de.trademonitor.entity.ClosedTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ClosedTradeRepository extends JpaRepository<ClosedTradeEntity, Long> {

    List<ClosedTradeEntity> findByAccountId(long accountId);

    List<ClosedTradeEntity> findByAccountIdOrderByCloseTimeDesc(long accountId);

    boolean existsByAccountIdAndTicket(long accountId, long ticket);

    long countByAccountId(long accountId);

    @Transactional
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
}
