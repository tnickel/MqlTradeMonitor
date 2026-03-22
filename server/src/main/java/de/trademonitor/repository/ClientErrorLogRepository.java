package de.trademonitor.repository;

import de.trademonitor.entity.ClientErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ClientErrorLogRepository extends JpaRepository<ClientErrorLog, Long> {

    List<ClientErrorLog> findAllByOrderByTimestampDesc();

    List<ClientErrorLog> findByAccountIdOrderByTimestampDesc(Long accountId);

    List<ClientErrorLog> findTop100ByOrderByTimestampDesc();

    List<ClientErrorLog> findTop100ByAccountIdOrderByTimestampDesc(Long accountId);

    @Transactional
    @Modifying
    @Query("DELETE FROM ClientErrorLog c WHERE c.timestamp < :cutoff")
    void deleteByTimestampBefore(LocalDateTime cutoff);
}
