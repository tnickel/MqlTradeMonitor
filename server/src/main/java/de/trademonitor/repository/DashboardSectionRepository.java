package de.trademonitor.repository;

import de.trademonitor.entity.DashboardSectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashboardSectionRepository extends JpaRepository<DashboardSectionEntity, Long> {
    List<DashboardSectionEntity> findAllByOrderByDisplayOrderAsc();
}
