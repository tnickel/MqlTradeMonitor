package de.trademonitor.repository;

import de.trademonitor.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
    List<RequestLog> findAllByOrderByTimestampDesc();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.ipAddress FROM RequestLog r")
    List<String> findDistinctIpAddresses();
}
