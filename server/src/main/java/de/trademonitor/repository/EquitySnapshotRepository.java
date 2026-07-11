package de.trademonitor.repository;

import de.trademonitor.entity.EquitySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Repository for equity snapshot persistence.
 */
public interface EquitySnapshotRepository extends JpaRepository<EquitySnapshotEntity, Long> {

    List<EquitySnapshotEntity> findByAccountIdOrderByTimestampAsc(long accountId);

    List<EquitySnapshotEntity> findByAccountIdAndTimestampBetweenOrderByTimestampAsc(long accountId, String from, String to);

    @Modifying
    @Transactional
    @Query("DELETE FROM EquitySnapshotEntity e WHERE e.accountId = :accountId AND e.timestamp < :cutoff")
    void deleteOlderThan(long accountId, String cutoff);

    @Query("SELECT e FROM EquitySnapshotEntity e WHERE e.accountId = :accountId AND (" +
           "  e.timestamp >= :recentCutoff OR e.timestamp LIKE '____-__-__T__:00:%'" +
           ") ORDER BY e.timestamp ASC")
    List<EquitySnapshotEntity> findByAccountIdAndTimestampGreaterThanOrMinuteIsZero(
            @org.springframework.data.repository.query.Param("accountId") long accountId, 
            @org.springframework.data.repository.query.Param("recentCutoff") String recentCutoff);

    @Query("SELECT e FROM EquitySnapshotEntity e WHERE e.accountId = :accountId AND e.timestamp BETWEEN :from AND :to AND (" +
           "  e.timestamp >= :recentCutoff OR e.timestamp LIKE '____-__-__T__:00:%'" +
           ") ORDER BY e.timestamp ASC")
    List<EquitySnapshotEntity> findByAccountIdAndTimestampBetweenAndRecentOrMinuteIsZero(
            @org.springframework.data.repository.query.Param("accountId") long accountId,
            @org.springframework.data.repository.query.Param("from") String from,
            @org.springframework.data.repository.query.Param("to") String to,
            @org.springframework.data.repository.query.Param("recentCutoff") String recentCutoff);

    /** Count snapshots for an account. */
    long countByAccountId(long accountId);

    @Modifying
    @Transactional
    @Query("DELETE FROM EquitySnapshotEntity e WHERE e.accountId = :accountId")
    void deleteByAccountId(long accountId);
}
