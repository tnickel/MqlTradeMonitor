package de.trademonitor.repository;

import de.trademonitor.entity.CopierLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CopierLinkRepository extends JpaRepository<CopierLinkEntity, Long> {
    List<CopierLinkEntity> findByTargetAccountId(long targetAccountId);
    List<CopierLinkEntity> findBySourceAccountId(long sourceAccountId);
}
