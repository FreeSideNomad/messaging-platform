package com.acme.payments.config;

import com.acme.payments.domain.repository.AccountLimitRepository;
import com.acme.payments.domain.repository.AccountRepository;
import com.acme.payments.domain.repository.FxContractRepository;
import com.acme.payments.domain.repository.PaymentRepository;
import com.acme.payments.infrastructure.persistence.JdbcAccountLimitRepository;
import com.acme.payments.infrastructure.persistence.JdbcAccountRepository;
import com.acme.payments.infrastructure.persistence.JdbcFxContractRepository;
import com.acme.payments.infrastructure.persistence.JdbcPaymentRepository;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * Configuration for repository bindings
 */
@Factory
public class RepositoryConfiguration {

    @Singleton
    public AccountRepository accountRepository(JdbcAccountRepository impl) {
        return impl;
    }

    @Singleton
    public AccountLimitRepository accountLimitRepository(JdbcAccountLimitRepository impl) {
        return impl;
    }

    @Singleton
    public FxContractRepository fxContractRepository(JdbcFxContractRepository impl) {
        return impl;
    }

    @Singleton
    public PaymentRepository paymentRepository(JdbcPaymentRepository impl) {
        return impl;
    }
}
