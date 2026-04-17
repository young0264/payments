package com.payments.payment.service

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.payment.domain.Ledger
import com.payments.payment.domain.LedgerEvent
import com.payments.payment.domain.Payment
import com.payments.payment.domain.PaymentCancelHistory
import com.payments.payment.domain.PaymentStatus
import com.payments.payment.repository.LedgerRepository
import com.payments.payment.repository.PaymentCancelHistoryRepository
import com.payments.payment.repository.PaymentRepository
import com.payments.pg.router.PgRouter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class PaymentTransactionService(
    private val paymentRepository: PaymentRepository,
    private val cancelHistoryRepository: PaymentCancelHistoryRepository,
    private val ledgerRepository: LedgerRepository,
    private val pgRouter: PgRouter,
    private val eventPublisher: PaymentEventPublisher,
) {

    @Transactional
    fun approve(orderId: String, idempotencyKey: String, amount: BigDecimal, merchantId: Long): Payment {
        paymentRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        paymentRepository.findByOrderId(orderId)?.let { existing ->
            if (existing.status != PaymentStatus.FAILED) {
                throw PaymentException(ErrorCode.DUPLICATE_ORDER)
            }
        }

        val payment = paymentRepository.save(
            Payment(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                amount = amount,
                merchantId = merchantId,
            )
        )

        val pgResponse = pgRouter.approve(orderId, amount)

        if (pgResponse.success) {
            if (pgResponse.amount != null && pgResponse.amount.compareTo(amount) != 0) {
                pgResponse.pgTransactionId?.let { txId ->
                    val providerName = requireNotNull(pgResponse.providerName) {
                        "providerName is null for orderId=$orderId"
                    }
                    pgRouter.getConnector(providerName).cancel(txId, pgResponse.amount)
                }
                payment.fail(
                    reason = "금액 위변조 감지: 요청=${amount}, PG승인=${pgResponse.amount}",
                    pgTransactionId = pgResponse.pgTransactionId,
                    pgProvider = pgResponse.providerName,
                )
                ledgerRepository.save(Ledger.failed(payment))
                return payment
            }

            payment.approve(
                pgTransactionId = requireNotNull(pgResponse.pgTransactionId),
                pgProvider = requireNotNull(pgResponse.providerName),
            )
            ledgerRepository.save(Ledger.approved(payment))
        } else {
            payment.fail(reason = pgResponse.message ?: "PG 승인 실패")
            ledgerRepository.save(Ledger.failed(payment))
        }

        return payment
    }

    @Transactional
    fun capture(orderId: String): Payment {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)

        if (payment.status != PaymentStatus.APPROVED) {
            throw PaymentException(ErrorCode.INVALID_PAYMENT_STATUS)
        }

        val txId = requireNotNull(payment.pgTransactionId) {
            "pgTransactionId is null for orderId=${payment.orderId}"
        }
        val providerName = requireNotNull(payment.pgProvider) {
            "pgProvider is null for orderId=${payment.orderId}"
        }

        val pgResponse = pgRouter.getConnector(providerName).capture(txId, payment.amount)

        if (pgResponse.success) {
            payment.capture()
            ledgerRepository.save(Ledger.captured(payment))
            eventPublisher.publishCaptured(payment.orderId, payment.amount)
        } else {
            throw PaymentException(ErrorCode.PG_CAPTURE_FAILED)
        }

        return payment
    }

    @Transactional
    fun cancel(orderId: String, cancelAmount: BigDecimal? = null, cancelReason: String? = null): Payment {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)

        if (payment.status !in listOf(PaymentStatus.APPROVED, PaymentStatus.CAPTURED, PaymentStatus.PARTIAL_CANCELED)) {
            throw PaymentException(ErrorCode.INVALID_PAYMENT_STATUS)
        }

        val actualCancelAmount = cancelAmount ?: payment.cancelableAmount

        if (actualCancelAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw PaymentException(ErrorCode.CANCEL_AMOUNT_NOT_POSITIVE)
        }
        if (actualCancelAmount.compareTo(payment.cancelableAmount) > 0) {
            throw PaymentException(ErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELABLE)
        }

        val txId = requireNotNull(payment.pgTransactionId) {
            "pgTransactionId is null for orderId=${payment.orderId}"
        }
        val providerName = requireNotNull(payment.pgProvider) {
            "pgProvider is null for orderId=${payment.orderId}"
        }

        val pgResponse = pgRouter.getConnector(providerName).cancel(txId, actualCancelAmount)

        if (pgResponse.success) {
            val canceledAmountBefore = payment.canceledAmount
            payment.cancel(actualCancelAmount)

            cancelHistoryRepository.save(
                PaymentCancelHistory(
                    payment = payment,
                    cancelAmount = actualCancelAmount,
                    cancelReason = cancelReason,
                    canceledAmountBefore = canceledAmountBefore,
                    canceledAmountAfter = payment.canceledAmount,
                    pgCancelTransactionId = pgResponse.pgTransactionId,
                )
            )

            val parentLedger = ledgerRepository.findTopByPaymentIdAndEventOrderByCreatedAtDesc(
                paymentId = payment.id,
                event = LedgerEvent.PAYMENT_APPROVED,
            )
            ledgerRepository.save(Ledger.canceled(payment, actualCancelAmount, parentLedger?.id))
        } else {
            throw PaymentException(ErrorCode.PG_CANCEL_FAILED)
        }

        return payment
    }

    @Transactional
    fun deleteAllByPaymentId(paymentId: Long) {
        ledgerRepository.deleteAllByPaymentId(paymentId)
    }
}
