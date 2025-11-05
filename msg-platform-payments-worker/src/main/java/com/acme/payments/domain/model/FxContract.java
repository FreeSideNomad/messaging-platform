package com.acme.payments.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/** Aggregate root for Foreign Exchange Contract */
@Getter
public class FxContract {
  private final UUID fxContractId;
  private final UUID customerId;
  private final UUID debitAccountId;
  private final Money debitAmount;
  private final Money creditAmount;
  private final BigDecimal rate;
  private final LocalDate valueDate;
  private FxStatus status;

  public FxContract(
      UUID fxContractId,
      UUID customerId,
      UUID debitAccountId,
      Money debitAmount,
      Money creditAmount,
      BigDecimal rate,
      LocalDate valueDate) {
    if (fxContractId == null) {
      throw new IllegalArgumentException("FX contract ID cannot be null");
    }
    if (customerId == null) {
      throw new IllegalArgumentException("Customer ID cannot be null");
    }
    if (debitAccountId == null) {
      throw new IllegalArgumentException("Debit account ID cannot be null");
    }
    if (debitAmount == null) {
      throw new IllegalArgumentException("Debit amount cannot be null");
    }
    if (creditAmount == null) {
      throw new IllegalArgumentException("Credit amount cannot be null");
    }
    if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Rate must be positive");
    }
    if (valueDate == null) {
      throw new IllegalArgumentException("Value date cannot be null");
    }
    if (debitAmount.currencyCode().equals(creditAmount.currencyCode())) {
      throw new IllegalArgumentException("Debit and credit currencies must be different for FX");
    }

    this.fxContractId = fxContractId;
    this.customerId = customerId;
    this.debitAccountId = debitAccountId;
    this.debitAmount = debitAmount;
    this.creditAmount = creditAmount;
    this.rate = rate;
    this.valueDate = valueDate;
    this.status = FxStatus.BOOKED;
  }

  /** Unwind (cancel) this FX contract */
  public void unwind(String reason) {
    if (status == FxStatus.UNWOUND) {
      throw new FxContractAlreadyUnwoundException(fxContractId);
    }
    status = FxStatus.UNWOUND;
  }

  /** Exception thrown when trying to unwind an already unwound contract */
  @Getter
  public static class FxContractAlreadyUnwoundException extends RuntimeException {
    private final UUID fxContractId;

    public FxContractAlreadyUnwoundException(UUID fxContractId) {
      super("FX contract already unwound: " + fxContractId);
      this.fxContractId = fxContractId;
    }
  }
}
