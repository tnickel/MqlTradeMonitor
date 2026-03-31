package de.trademonitor.repository;

import de.trademonitor.entity.EaLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface EaLogEntryRepository extends JpaRepository<EaLogEntry, Long> {

    List<EaLogEntry> findByAccountIdOrderByTimestampDesc(Long accountId);

    @Query("SELECT e FROM EaLogEntry e WHERE e.accountId = :accountId ORDER BY e.timestamp DESC LIMIT 5000")
    List<EaLogEntry> findTop5000ByAccountIdOrderByTimestampDesc(Long accountId);

    @Transactional
    @Modifying
    @Query("DELETE FROM EaLogEntry e WHERE e.timestamp < :cutoff")
    void deleteByTimestampBefore(LocalDateTime cutoff);

    long countByAccountId(Long accountId);

    @Query(value = "SELECT CASE " +
                   "WHEN SUM(CASE WHEN LOWER(e.log_line) LIKE '%error%' OR LOWER(e.log_line) LIKE '%failed%' OR LOWER(e.log_line) LIKE '%exception%' THEN 1 ELSE 0 END) > 0 THEN 2 " +
                   "WHEN SUM(CASE WHEN LOWER(e.log_line) LIKE '%warn %' OR LOWER(e.log_line) LIKE '%warning%' THEN 1 ELSE 0 END) > 0 THEN 1 " +
                   "ELSE 0 END " +
                   "FROM ea_log_entry e WHERE e.account_id = :accountId AND e.timestamp > :since " +
                   "AND (CAST(:logAcceptedAt AS TIMESTAMP) IS NULL OR e.timestamp > :logAcceptedAt)", nativeQuery = true)
    Integer getLogSeverityForAccountSince(@org.springframework.data.repository.query.Param("accountId") Long accountId, 
                                          @org.springframework.data.repository.query.Param("since") LocalDateTime since,
                                          @org.springframework.data.repository.query.Param("logAcceptedAt") LocalDateTime logAcceptedAt);
}
