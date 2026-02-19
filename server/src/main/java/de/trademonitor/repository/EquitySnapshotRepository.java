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

    /**
     * Delete snapshots older than the given timestamp string (ISO format comparison
     * works lexicographically).
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EquitySnapshotEntity e WHERE e.accountId = :accountId AND e.timestamp < :cutoff")
    void deleteOlderThan(long accountId, String cutoff);

    /** Count snapshots for an account. */
    long countByAccountId(long accountId);
}
