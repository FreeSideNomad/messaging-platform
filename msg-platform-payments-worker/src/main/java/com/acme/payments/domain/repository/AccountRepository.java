package com.acme.payments.domain.repository;

import com.acme.payments.domain.model.Account;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Account aggregate
 */
public interface AccountRepository {
    void save(Account account);

    Optional<Account> findById(UUID accountId);

    Optional<Account> findByAccountNumber(String accountNumber);
}
