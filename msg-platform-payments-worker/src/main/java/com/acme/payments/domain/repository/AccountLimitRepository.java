package com.acme.payments.domain.repository;

import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.PeriodType;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for AccountLimit aggregate
 */
public interface AccountLimitRepository {
    void save(AccountLimit limit);
    List<AccountLimit> findActiveByAccountId(UUID accountId);
    List<AccountLimit> findByAccountIdAndPeriodType(UUID accountId, PeriodType periodType);
}
