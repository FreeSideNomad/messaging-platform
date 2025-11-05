package com.acme.payments.domain.service;

import com.acme.payments.application.command.BookFxCommand;
import com.acme.payments.application.command.UnwindFxCommand;
import com.acme.payments.domain.model.FxContract;
import com.acme.payments.domain.repository.FxContractRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Domain service for FX operations */
@Singleton
@RequiredArgsConstructor
@Slf4j
public class FxService {
  private final FxContractRepository fxContractRepository;

  @Transactional
  public FxContract bookFx(BookFxCommand cmd) {
    log.info(
        "Booking FX: {} -> {} for customer {}",
        cmd.debitAmount(),
        cmd.creditAmount(),
        cmd.customerId());

    // Calculate FX rate
    BigDecimal rate =
        cmd.creditAmount().amount().divide(cmd.debitAmount().amount(), 6, RoundingMode.HALF_UP);

    FxContract fxContract =
        new FxContract(
            UUID.randomUUID(),
            cmd.customerId(),
            cmd.debitAccountId(),
            cmd.debitAmount(),
            cmd.creditAmount(),
            rate,
            cmd.valueDate());

    fxContractRepository.save(fxContract);
    log.info("FX contract created: {} at rate {}", fxContract.getFxContractId(), rate);

    return fxContract;
  }

  @Transactional
  public void unwindFx(UUID fxContractId, String reason) {
    log.info("Unwinding FX contract {} reason: {}", fxContractId, reason);

    FxContract fxContract =
        fxContractRepository
            .findById(fxContractId)
            .orElseThrow(() -> new FxContractNotFoundException(fxContractId));

    fxContract.unwind(reason);
    fxContractRepository.save(fxContract);

    log.info("FX contract unwound: {}", fxContractId);
  }

  /**
   * Command handler for UnwindFxCommand This method will be auto-discovered by
   * AutoCommandHandlerRegistry
   */
  public void handleUnwindFx(UnwindFxCommand cmd) {
    unwindFx(cmd.fxContractId(), cmd.reason());
  }

  public static class FxContractNotFoundException extends RuntimeException {
    public FxContractNotFoundException(UUID fxContractId) {
      super("FX contract not found: " + fxContractId);
    }
  }
}
