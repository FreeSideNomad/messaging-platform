package com.acme.payments.domain.service;

import com.acme.payments.application.command.CreatePaymentCommand;
import com.acme.payments.domain.model.*;
import com.acme.payments.domain.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService.
 * Uses Mockito to mock repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID debitAccountId;
    private UUID paymentId;
    private Beneficiary beneficiary;

    @BeforeEach
    void setUp() {
        debitAccountId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        beneficiary = new Beneficiary(
            "John Doe",
            "123456789",
            "001",
            "Test Bank"
        );
    }

    @Test
    @DisplayName("createPayment - should create payment with same currency (no FX)")
    void testCreatePayment_SameCurrency() {
        // Given
        CreatePaymentCommand command = new CreatePaymentCommand(
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(100, "USD"),
            LocalDate.now(),
            beneficiary
        );

        // When
        Payment result = paymentService.createPayment(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPaymentId()).isNotNull();
        assertThat(result.getDebitAccountId()).isEqualTo(debitAccountId);
        assertThat(result.getDebitAmount()).isEqualTo(Money.of(100, "USD"));
        assertThat(result.getCreditAmount()).isEqualTo(Money.of(100, "USD"));
        assertThat(result.getValueDate()).isEqualTo(LocalDate.now());
        assertThat(result.getBeneficiary()).isEqualTo(beneficiary);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.requiresFx()).isFalse();
        assertThat(result.getFeeAmount()).isEqualTo(Money.zero("USD"));

        // Verify repository was called
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getDebitAccountId()).isEqualTo(debitAccountId);
        assertThat(savedPayment.getDebitAmount()).isEqualTo(Money.of(100, "USD"));
    }

    @Test
    @DisplayName("createPayment - should create payment with different currency (requires FX)")
    void testCreatePayment_DifferentCurrency() {
        // Given
        CreatePaymentCommand command = new CreatePaymentCommand(
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(85, "EUR"),  // Different currency
            LocalDate.now(),
            beneficiary
        );

        // When
        Payment result = paymentService.createPayment(command);

        // Then
        assertThat(result.requiresFx()).isTrue();
        assertThat(result.getDebitAmount().currencyCode()).isEqualTo("USD");
        assertThat(result.getCreditAmount().currencyCode()).isEqualTo("EUR");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("updatePayment - should save payment to repository")
    void testUpdatePayment() {
        // Given
        Payment payment = createTestPayment();
        payment.markAsProcessing();

        // When
        Payment result = paymentService.updatePayment(payment);

        // Then
        assertThat(result).isEqualTo(payment);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        verify(paymentRepository).save(payment);
    }

    @Test
    @DisplayName("getPaymentById - should return payment when found")
    void testGetPaymentById_Success() {
        // Given
        Payment payment = createTestPayment();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // When
        Payment result = paymentService.getPaymentById(paymentId);

        // Then
        assertThat(result).isEqualTo(payment);
        verify(paymentRepository).findById(paymentId);
    }

    @Test
    @DisplayName("getPaymentById - should throw PaymentNotFoundException when not found")
    void testGetPaymentById_NotFound() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> paymentService.getPaymentById(paymentId))
            .isInstanceOf(PaymentService.PaymentNotFoundException.class)
            .hasMessageContaining(paymentId.toString());
    }

    @Test
    @DisplayName("createPayment - should set initial fee amount to zero with correct currency")
    void testCreatePayment_InitialFeeIsZero() {
        // Given
        CreatePaymentCommand command = new CreatePaymentCommand(
            debitAccountId,
            Money.of(500, "EUR"),
            Money.of(500, "EUR"),
            LocalDate.now(),
            beneficiary
        );

        // When
        Payment result = paymentService.createPayment(command);

        // Then
        assertThat(result.getFeeAmount()).isEqualTo(Money.zero("EUR"));
        assertThat(result.getFeeAmount().currencyCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("createPayment - should set createdAt timestamp")
    void testCreatePayment_CreatedAtIsSet() {
        // Given
        CreatePaymentCommand command = new CreatePaymentCommand(
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(100, "USD"),
            LocalDate.now(),
            beneficiary
        );

        // When
        Payment result = paymentService.createPayment(command);

        // Then
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("createPayment - should handle future value date")
    void testCreatePayment_FutureValueDate() {
        // Given
        LocalDate futureDate = LocalDate.now().plusDays(5);
        CreatePaymentCommand command = new CreatePaymentCommand(
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(100, "USD"),
            futureDate,
            beneficiary
        );

        // When
        Payment result = paymentService.createPayment(command);

        // Then
        assertThat(result.getValueDate()).isEqualTo(futureDate);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(paymentRepository).save(any(Payment.class));
    }

    // Helper method to create test payments
    private Payment createTestPayment() {
        return new Payment(
            paymentId,
            debitAccountId,
            Money.of(100, "USD"),
            Money.of(100, "USD"),
            LocalDate.now(),
            beneficiary
        );
    }
}
