package com.acme.payments.domain.repository;

import com.acme.payments.domain.model.FxContract;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for FxContract aggregate
 */
public interface FxContractRepository {
    void save(FxContract fxContract);
    Optional<FxContract> findById(UUID fxContractId);
}
