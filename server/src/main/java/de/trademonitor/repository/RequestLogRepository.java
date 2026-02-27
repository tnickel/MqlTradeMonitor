package de.trademonitor.repository;

import de.trademonitor.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
    List<RequestLog> findAllByOrderByTimestampDesc();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.ipAddress FROM RequestLog r")
    List<String> findDistinctIpAddresses();

    @Transactional
    @Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM RequestLog r WHERE r.timestamp < :cutoff")
    void deleteByTimestampBefore(java.time.LocalDateTime cutoff);
}
