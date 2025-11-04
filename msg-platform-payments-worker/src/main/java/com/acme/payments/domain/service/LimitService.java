package com.acme.payments.domain.service;

import com.acme.payments.application.command.BookLimitsCommand;
import com.acme.payments.application.command.ReverseLimitsCommand;
import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.repository.AccountLimitRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

/**
 * Domain service for Limit operations
 */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class LimitService {
    private final AccountLimitRepository limitRepository;

    @Transactional
    public void bookLimits(BookLimitsCommand cmd) {
        log.info("Booking limits for account {} amount {}",
            cmd.accountId(), cmd.amount().amount());

        List<AccountLimit> limits = limitRepository.findActiveByAccountId(cmd.accountId());

        if (limits.isEmpty()) {
            log.warn("No active limits found for account {}", cmd.accountId());
            return;
        }

        // Book against all active limits
        for (AccountLimit limit : limits) {
            if (!limit.isExpired()) {
                limit.book(cmd.amount());
                limitRepository.save(limit);
                log.info("Booked {} against {} limit: {}/{}",
                    cmd.amount().amount(),
                    limit.getPeriodType(),
                    limit.getUtilized().amount(),
                    limit.getLimitAmount().amount());
            }
        }
    }

    @Transactional
    public void reverseLimits(UUID accountId, Money amount) {
        log.info("Reversing limits for account {} amount {}",
            accountId, amount.amount());

        List<AccountLimit> limits = limitRepository.findActiveByAccountId(accountId);

        for (AccountLimit limit : limits) {
            limit.reverse(amount);
            limitRepository.save(limit);
            log.info("Reversed {} against {} limit",
                amount.amount(), limit.getPeriodType());
        }
    }

    /**
     * Command handler for ReverseLimitsCommand
     * This method will be auto-discovered by AutoCommandHandlerRegistry
     */
    public void handleReverseLimits(ReverseLimitsCommand cmd) {
        reverseLimits(cmd.accountId(), cmd.amount());
    }
}
