package de.trademonitor.service;

import de.trademonitor.entity.UserEntity;
import de.trademonitor.model.Account;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Central authorization policy for physical and server-assigned virtual accounts.
 */
@Service
public class AccountAccessService {

    private final AccountManager accountManager;

    public AccountAccessService(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    public boolean canAccess(UserEntity user, long accountId) {
        return canAccess(user, accountId, null);
    }

    public boolean canAccess(UserEntity user, long accountId, Long knownPhysicalAccountId) {
        if (user == null) {
            return false;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return true;
        }
        Set<Long> allowed = safeAllowedIds(user);
        if (allowed.contains(accountId)) {
            return true;
        }
        long physicalId = knownPhysicalAccountId != null && knownPhysicalAccountId > 0
                ? knownPhysicalAccountId
                : getPhysicalAccountId(accountId);
        return allowed.contains(physicalId);
    }

    /**
     * Registration writes are authorized against the physical MetaTrader account,
     * never against a caller-supplied virtual account ID.
     */
    public boolean canAccessPhysicalAccount(UserEntity user, long physicalAccountId) {
        if (user == null || physicalAccountId <= 0) {
            return false;
        }
        return "ROLE_ADMIN".equals(user.getRole()) || safeAllowedIds(user).contains(physicalAccountId);
    }

    public long getPhysicalAccountId(long accountId) {
        Account account = accountManager.getAccount(accountId);
        if (account != null && account.getRealAccountId() != null && account.getRealAccountId() > 0) {
            return account.getRealAccountId();
        }
        return accountId;
    }

    public boolean belongsToPhysicalAccount(long accountId, long physicalAccountId) {
        return accountId == physicalAccountId || getPhysicalAccountId(accountId) == physicalAccountId;
    }

    public Set<Long> getAccessibleAccountIds(UserEntity user) {
        if (user == null) {
            return Collections.emptySet();
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return null;
        }
        Set<Long> accessible = new HashSet<>(safeAllowedIds(user));
        for (Account account : accountManager.getAccounts().values()) {
            Long physicalId = account.getRealAccountId();
            if (physicalId != null && accessible.contains(physicalId)) {
                accessible.add(account.getAccountId());
            }
        }
        return accessible;
    }

    private Set<Long> safeAllowedIds(UserEntity user) {
        return user.getAllowedAccountIds() == null ? Collections.emptySet() : user.getAllowedAccountIds();
    }
}
