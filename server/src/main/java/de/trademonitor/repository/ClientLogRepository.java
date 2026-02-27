package de.trademonitor.repository;

import de.trademonitor.entity.ClientLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ClientLogRepository extends JpaRepository<ClientLog, Long> {
    List<ClientLog> findAllByOrderByTimestampDesc();

    List<ClientLog> findByAccountIdOrderByTimestampDesc(Long accountId);

    @Transactional
    @Modifying
    @Query("DELETE FROM ClientLog c WHERE c.timestamp < :cutoff")
    void deleteByTimestampBefore(java.time.LocalDateTime cutoff);
}
