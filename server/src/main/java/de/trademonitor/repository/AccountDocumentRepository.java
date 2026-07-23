package de.trademonitor.repository;

import de.trademonitor.entity.AccountDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Spring Data JPA Repository for managing AccountDocumentEntity instances.
 */
public interface AccountDocumentRepository extends JpaRepository<AccountDocumentEntity, Long> {
    List<AccountDocumentEntity> findAllByAccountId(long accountId);

    /**
     * Lightweight projection used by the account info page.  Keeping the BLOB out
     * of the list query prevents every document from being loaded into memory just
     * to render its name and description.
     */
    interface AccountDocumentSummary {
        Long getId();
        long getAccountId();
        String getFileName();
        String getMinText();
        String getContentType();
        long getFileSize();
    }

    List<AccountDocumentSummary> findAllProjectedByAccountId(long accountId);

    void deleteByAccountId(long accountId);
}
