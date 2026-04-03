package de.trademonitor.repository;

import de.trademonitor.entity.NetworkStatusLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NetworkStatusLogRepository extends JpaRepository<NetworkStatusLogEntity, Long> {
    
    NetworkStatusLogEntity findFirstByOrderByStartTimeDesc();

    @Query("SELECT n FROM NetworkStatusLogEntity n WHERE n.startTime >= :since ORDER BY n.startTime ASC")
    List<NetworkStatusLogEntity> findLogsSince(LocalDateTime since);
}
