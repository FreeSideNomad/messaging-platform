package com.acme.payments.domain.service;

import com.acme.payments.application.command.BookFxCommand;
import com.acme.payments.application.command.UnwindFxCommand;
import com.acme.payments.domain.model.FxContract;
import com.acme.payments.domain.model.FxStatus;
import com.acme.payments.domain.model.Money;
import com.acme.payments.domain.repository.FxContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FxService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FxService Unit Tests")
class FxServiceTest {

    @Mock
    private FxContractRepository fxContractRepository;

    @InjectMocks
    private FxService fxService;

    private UUID customerId;
    private UUID debitAccountId;
    private UUID fxContractId;
    private LocalDate valueDate;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        debitAccountId = UUID.randomUUID();
        fxContractId = UUID.randomUUID();
        valueDate = LocalDate.now();
    }

    // Helper methods
    private FxContract createActiveFxContract() {
        return new FxContract(
                fxContractId,
                customerId,
                debitAccountId,
                Money.of(100, "USD"),
                Money.of(85, "EUR"),
                new BigDecimal("0.85"),
                valueDate);
    }

    @Nested
    @DisplayName("Book FX Contract")
    class BookFxTests {

        @Test
        @DisplayName("Should book FX contract with correct rate calculation (USD to EUR)")
        void testBookFx_UsdToEur() {
            // Given: $100 USD -> €85 EUR (rate 0.85)
            BookFxCommand command =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(100, "USD"), Money.of(85, "EUR"), valueDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getCustomerId()).isEqualTo(customerId);
            assertThat(result.getDebitAccountId()).isEqualTo(debitAccountId);
            assertThat(result.getDebitAmount()).isEqualTo(Money.of(100, "USD"));
            assertThat(result.getCreditAmount()).isEqualTo(Money.of(85, "EUR"));
            assertThat(result.getRate()).isEqualByComparingTo("0.85");
            assertThat(result.getValueDate()).isEqualTo(valueDate);
            assertThat(result.getStatus()).isEqualTo(FxStatus.BOOKED);

            // Verify repository interaction
            ArgumentCaptor<FxContract> captor = ArgumentCaptor.forClass(FxContract.class);
            verify(fxContractRepository).save(captor.capture());

            FxContract savedContract = captor.getValue();
            assertThat(savedContract.getDebitAmount().currencyCode()).isEqualTo("USD");
            assertThat(savedContract.getCreditAmount().currencyCode()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Should book FX contract with correct rate calculation (GBP to USD)")
        void testBookFx_GbpToUsd() {
            // Given: £100 GBP -> $125 USD (rate 1.25)
            BookFxCommand command =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(100, "GBP"), Money.of(125, "USD"), valueDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then
            assertThat(result.getRate()).isEqualByComparingTo("1.25");
            assertThat(result.getDebitAmount()).isEqualTo(Money.of(100, "GBP"));
            assertThat(result.getCreditAmount()).isEqualTo(Money.of(125, "USD"));

            verify(fxContractRepository).save(any(FxContract.class));
        }

        @Test
        @DisplayName("Should calculate rate with high precision (6 decimal places)")
        void testBookFx_HighPrecisionRate() {
            // Given: $100 USD -> €84.33 EUR (rate 0.8433)
            BookFxCommand command =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(100, "USD"), Money.of(84.33, "EUR"), valueDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then: Rate should be precise to 6 decimal places
            assertThat(result.getRate()).isEqualByComparingTo("0.8433");
            assertThat(result.getRate().scale()).isLessThanOrEqualTo(6);

            verify(fxContractRepository).save(any(FxContract.class));
        }

        @Test
        @DisplayName("Should handle large amounts")
        void testBookFx_LargeAmounts() {
            // Given: $1,000,000 USD -> €850,000 EUR
            BookFxCommand command =
                    new BookFxCommand(
                            customerId,
                            debitAccountId,
                            Money.of(1000000, "USD"),
                            Money.of(850000, "EUR"),
                            valueDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then
            assertThat(result.getRate()).isEqualByComparingTo("0.85");
            assertThat(result.getDebitAmount()).isEqualTo(Money.of(1000000, "USD"));
            assertThat(result.getCreditAmount()).isEqualTo(Money.of(850000, "EUR"));

            verify(fxContractRepository).save(any(FxContract.class));
        }

        @Test
        @DisplayName("Should handle small fractional amounts")
        void testBookFx_SmallAmounts() {
            // Given: $1.50 USD -> €1.28 EUR
            BookFxCommand command =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(1.50, "USD"), Money.of(1.28, "EUR"), valueDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then
            assertThat(result.getDebitAmount()).isEqualTo(Money.of(1.50, "USD"));
            assertThat(result.getCreditAmount()).isEqualTo(Money.of(1.28, "EUR"));
            // Rate should be approximately 0.853333
            assertThat(result.getRate()).isGreaterThan(BigDecimal.valueOf(0.85));
            assertThat(result.getRate()).isLessThan(BigDecimal.valueOf(0.86));

            verify(fxContractRepository).save(any(FxContract.class));
        }

        @Test
        @DisplayName("Should handle various currency pairs")
        void testBookFx_VariousCurrencies() {
            // EUR to JPY
            BookFxCommand eurJpy =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(100, "EUR"), Money.of(16000, "JPY"), valueDate);

            FxContract result = fxService.bookFx(eurJpy);

            assertThat(result.getDebitAmount().currencyCode()).isEqualTo("EUR");
            assertThat(result.getCreditAmount().currencyCode()).isEqualTo("JPY");
            assertThat(result.getRate()).isEqualByComparingTo("160");

            verify(fxContractRepository).save(any(FxContract.class));
        }
    }

    @Nested
    @DisplayName("Unwind FX Contract")
    class UnwindFxTests {

        @Test
        @DisplayName("Should unwind active FX contract")
        void testUnwindFx_Success() {
            // Given: Active FX contract
            FxContract fxContract = createActiveFxContract();
            when(fxContractRepository.findById(fxContractId)).thenReturn(Optional.of(fxContract));

            // When
            fxService.unwindFx(fxContractId, "Payment failed");

            // Then: Contract should be marked as unwound
            assertThat(fxContract.getStatus()).isEqualTo(FxStatus.UNWOUND);

            // Verify repository interactions
            verify(fxContractRepository).findById(fxContractId);
            ArgumentCaptor<FxContract> captor = ArgumentCaptor.forClass(FxContract.class);
            verify(fxContractRepository).save(captor.capture());

            FxContract savedContract = captor.getValue();
            assertThat(savedContract.getStatus()).isEqualTo(FxStatus.UNWOUND);
        }

        @Test
        @DisplayName("Should throw exception when FX contract not found")
        void testUnwindFx_NotFound() {
            // Given: Non-existent FX contract
            UUID nonExistentId = UUID.randomUUID();
            when(fxContractRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> fxService.unwindFx(nonExistentId, "Test"))
                    .isInstanceOf(FxService.FxContractNotFoundException.class)
                    .hasMessageContaining(nonExistentId.toString());

            verify(fxContractRepository).findById(nonExistentId);
            verify(fxContractRepository, never()).save(any(FxContract.class));
        }

        @Test
        @DisplayName("Should handle unwind with various reasons")
        void testUnwindFx_VariousReasons() {
            // Test different unwind reasons
            String[] reasons = {
                    "Payment cancelled", "Customer request", "System error", "Timeout", "Insufficient funds"
            };

            for (String reason : reasons) {
                FxContract fxContract = createActiveFxContract();
                UUID contractId = UUID.randomUUID();
                when(fxContractRepository.findById(contractId)).thenReturn(Optional.of(fxContract));

                fxService.unwindFx(contractId, reason);

                assertThat(fxContract.getStatus()).isEqualTo(FxStatus.UNWOUND);
            }

            verify(fxContractRepository, times(reasons.length)).save(any(FxContract.class));
        }
    }

    @Nested
    @DisplayName("Command Handlers")
    class CommandHandlerTests {

        @Test
        @DisplayName("handleUnwindFx - should delegate to unwindFx method")
        void testHandleUnwindFx() {
            // Given
            FxContract fxContract = createActiveFxContract();
            when(fxContractRepository.findById(fxContractId)).thenReturn(Optional.of(fxContract));

            UnwindFxCommand command = new UnwindFxCommand(fxContractId, "Test reason");

            // When
            fxService.handleUnwindFx(command);

            // Then
            assertThat(fxContract.getStatus()).isEqualTo(FxStatus.UNWOUND);
            verify(fxContractRepository).findById(fxContractId);
            verify(fxContractRepository).save(any(FxContract.class));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle rate of 1.0 (same value in different currencies)")
        void testBookFx_RateOne() {
            // Given: Theoretical $100 USD -> 100 Special Currency
            BookFxCommand command =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(100, "USD"), Money.of(100, "EUR"), valueDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then
            assertThat(result.getRate()).isEqualByComparingTo("1.0");

            verify(fxContractRepository).save(any(FxContract.class));
        }

        @Test
        @DisplayName("Should handle very favorable rate (rate > 1)")
        void testBookFx_FavorableRate() {
            // Given: $100 USD -> £125 GBP (rate 1.25)
            BookFxCommand command =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(100, "USD"), Money.of(125, "GBP"), valueDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then
            assertThat(result.getRate()).isGreaterThan(BigDecimal.ONE);
            assertThat(result.getRate()).isEqualByComparingTo("1.25");

            verify(fxContractRepository).save(any(FxContract.class));
        }

        @Test
        @DisplayName("Should handle future value date")
        void testBookFx_FutureValueDate() {
            // Given: Forward contract with future value date
            LocalDate futureDate = LocalDate.now().plusDays(30);
            BookFxCommand command =
                    new BookFxCommand(
                            customerId, debitAccountId, Money.of(1000, "USD"), Money.of(850, "EUR"), futureDate);

            // When
            FxContract result = fxService.bookFx(command);

            // Then
            assertThat(result.getValueDate()).isEqualTo(futureDate);
            assertThat(result.getValueDate()).isAfter(LocalDate.now());

            verify(fxContractRepository).save(any(FxContract.class));
        }
    }
}
