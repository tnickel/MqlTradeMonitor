package de.trademonitor.repository;

import de.trademonitor.entity.ClosedTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClosedTradeRepository extends JpaRepository<ClosedTradeEntity, Long> {

    List<ClosedTradeEntity> findByAccountId(long accountId);

    List<ClosedTradeEntity> findByAccountIdOrderByCloseTimeDesc(long accountId);

    boolean existsByAccountIdAndTicket(long accountId, long ticket);

    long countByAccountId(long accountId);

    @Query("SELECT MIN(c.closeTime) FROM ClosedTradeEntity c WHERE c.accountId = ?1")
    String findMinCloseTimeByAccountId(long accountId);

    @Query("SELECT MAX(c.closeTime) FROM ClosedTradeEntity c WHERE c.accountId = ?1")
    String findMaxCloseTimeByAccountId(long accountId);

    java.util.Optional<ClosedTradeEntity> findFirstByMagicNumber(Long magicNumber);
}
