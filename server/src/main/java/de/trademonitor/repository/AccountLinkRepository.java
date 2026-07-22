package de.trademonitor.repository;

import de.trademonitor.entity.AccountLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA Repository for managing AccountLinkEntity instances.
 */
public interface AccountLinkRepository extends JpaRepository<AccountLinkEntity, Long> {
    List<AccountLinkEntity> findAllByAccountId(long accountId);

    void deleteByAccountId(long accountId);
}
