package com.acme.payments.domain.service;

import com.acme.payments.application.command.BookLimitsCommand;
import com.acme.payments.application.command.CreateLimitsCommand;
import com.acme.payments.application.command.ReverseLimitsCommand;
import com.acme.payments.domain.model.AccountLimit;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.model.PeriodType;
import com.acme.payments.domain.repository.AccountLimitRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Domain service for Limit operations */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class LimitService {
  private final AccountLimitRepository limitRepository;

  /**
   * Command handler for CreateLimitsCommand. Creates initial time-bucketed limits for an account.
   * This method will be auto-discovered by AutoCommandHandlerRegistry.
   *
   * @param cmd the create limits command
   * @return map containing created limit IDs and count
   */
  @Transactional
  public Map<String, Object> handleCreateLimits(CreateLimitsCommand cmd) {
    log.info("Creating {} limits for account {}", cmd.limits().size(), cmd.accountId());

    List<String> createdLimitIds = new ArrayList<>();
    Instant now = Instant.now();

    // Create a limit bucket for each period type
    for (Map.Entry<PeriodType, Money> entry : cmd.limits().entrySet()) {
      PeriodType periodType = entry.getKey();
      Money limitAmount = entry.getValue();

      // Calculate time bucket start aligned to period boundaries
      Instant bucketStart = periodType.alignToBucketStart(now);

      AccountLimit limit =
          new AccountLimit(
              UUID.randomUUID(), cmd.accountId(), periodType, bucketStart, limitAmount);

      limitRepository.save(limit);
      createdLimitIds.add(limit.getLimitId().toString());

      log.info(
          "Created {} limit for account {}: {} (bucket: {} to {})",
          periodType,
          cmd.accountId(),
          limitAmount.amount(),
          bucketStart,
          limit.getEndTime());
    }

    log.info(
        "Successfully created {} limits for account {}", createdLimitIds.size(), cmd.accountId());

    // Return created limit IDs for auditing
    Map<String, Object> result = new HashMap<>();
    result.put("limitIds", createdLimitIds);
    result.put("limitCount", createdLimitIds.size());
    return result;
  }

  @Transactional
  public void bookLimits(BookLimitsCommand cmd) {
    log.info("Booking limits for account {} amount {}", cmd.accountId(), cmd.amount().amount());

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
        log.info(
            "Booked {} against {} limit: {}/{}",
            cmd.amount().amount(),
            limit.getPeriodType(),
            limit.getUtilized().amount(),
            limit.getLimitAmount().amount());
      }
    }
  }

  @Transactional
  public void reverseLimits(UUID accountId, Money amount) {
    log.info("Reversing limits for account {} amount {}", accountId, amount.amount());

    List<AccountLimit> limits = limitRepository.findActiveByAccountId(accountId);

    for (AccountLimit limit : limits) {
      limit.reverse(amount);
      limitRepository.save(limit);
      log.info("Reversed {} against {} limit", amount.amount(), limit.getPeriodType());
    }
  }

  /**
   * Command handler for ReverseLimitsCommand This method will be auto-discovered by
   * AutoCommandHandlerRegistry
   */
  public void handleReverseLimits(ReverseLimitsCommand cmd) {
    reverseLimits(cmd.accountId(), cmd.amount());
  }
}
