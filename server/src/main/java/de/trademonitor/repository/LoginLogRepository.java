package de.trademonitor.repository;

import de.trademonitor.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {
    List<LoginLog> findAllByOrderByTimestampDesc();

    @Transactional
    @Modifying
    @Query("DELETE FROM LoginLog l WHERE l.timestamp < :cutoff")
    void deleteByTimestampBefore(java.time.LocalDateTime cutoff);
}
