package de.trademonitor.repository;

import de.trademonitor.entity.ClientLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClientLogRepository extends JpaRepository<ClientLog, Long> {
    List<ClientLog> findAllByOrderByTimestampDesc();

    List<ClientLog> findByAccountIdOrderByTimestampDesc(Long accountId);
}
