package de.trademonitor.repository;

import de.trademonitor.entity.GlobalConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GlobalConfigRepository extends JpaRepository<GlobalConfigEntity, String> {
}
