package de.trademonitor.repository;

import de.trademonitor.entity.TimelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimelineRepository extends JpaRepository<TimelineEntity, Long> {
    List<TimelineEntity> findByAccountIdOrderByTimelineDateAsc(long accountId);
}
