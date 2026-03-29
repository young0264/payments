package com.payments.payment.service

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.payment.domain.Payment
import com.payments.payment.domain.PaymentCancelHistory
import com.payments.payment.domain.PaymentStatus
import com.payments.payment.repository.PaymentCancelHistoryRepository
import com.payments.payment.repository.PaymentRepository
import com.payments.pg.router.PgRouter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PaymentTransactionService(
    private val paymentRepository: PaymentRepository,
    private val cancelHistoryRepository: PaymentCancelHistoryRepository,
    private val pgRouter: PgRouter,
) {

    @Transactional
    fun approve(orderId: String, idempotencyKey: String, amount: BigDecimal): Payment {
        // 멱등성 체크: 같은 키로 이미 처리된 결제가 있으면 그대로 리턴
        paymentRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        // 중복 주문 체크 (FAILED면 재시도 허용)
        paymentRepository.findByOrderId(orderId)?.let { existing ->
            if (existing.status != PaymentStatus.FAILED) {
                throw PaymentException(ErrorCode.DUPLICATE_ORDER)
            }
        }

        // 결제 생성 (READY)
        val payment = paymentRepository.save(
            Payment(
                orderId = orderId,
                idempotencyKey = idempotencyKey,
                amount = amount,
            )
        )

        // PG 승인 요청 (라우터가 fallback 처리)
        val pgResponse = pgRouter.approve(orderId, amount)

        if (pgResponse.success) {
            // 금액 위변조 검증: PG 승인 금액과 요청 금액 비교
            if (pgResponse.amount != null && pgResponse.amount.compareTo(amount) != 0) {
                pgResponse.pgTransactionId?.let { txId ->
                    val providerName = requireNotNull(pgResponse.providerName) {
                        "providerName is null for orderId=$orderId"
                    }
                    val connector = pgRouter.getConnector(providerName)
                    connector.cancel(txId, pgResponse.amount)
                }
                payment.status = PaymentStatus.FAILED
                payment.pgProvider = pgResponse.providerName
                payment.pgTransactionId = pgResponse.pgTransactionId
                payment.failReason = "금액 위변조 감지: 요청=${amount}, PG승인=${pgResponse.amount}"
                payment.updatedAt = LocalDateTime.now()
                return payment
            }

            payment.status = PaymentStatus.APPROVED
            payment.pgProvider = pgResponse.providerName
            payment.pgTransactionId = pgResponse.pgTransactionId
        } else {
            payment.status = PaymentStatus.FAILED
            payment.failReason = pgResponse.message
        }
        payment.updatedAt = LocalDateTime.now()

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
        val connector = pgRouter.getConnector(providerName)
        val pgResponse = connector.capture(txId, payment.amount)

        if (pgResponse.success) {
            payment.status = PaymentStatus.CAPTURED
        } else {
            throw PaymentException(ErrorCode.PG_CAPTURE_FAILED)
        }
        payment.updatedAt = LocalDateTime.now()

        return payment
    }

    @Transactional
    fun cancel(orderId: String, cancelAmount: BigDecimal? = null, cancelReason: String? = null): Payment {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)

        if (payment.status != PaymentStatus.APPROVED &&
            payment.status != PaymentStatus.CAPTURED &&
            payment.status != PaymentStatus.PARTIAL_CANCELED) {
            throw PaymentException(ErrorCode.INVALID_PAYMENT_STATUS)
        }

        // 취소 금액 결정: null이면 잔액 전체 취소
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
        val connector = pgRouter.getConnector(providerName)
        val pgResponse = connector.cancel(txId, actualCancelAmount)

        if (pgResponse.success) {
            val canceledAmountBefore = payment.canceledAmount
            payment.canceledAmount = payment.canceledAmount + actualCancelAmount

            payment.status = if (payment.cancelableAmount.compareTo(BigDecimal.ZERO) == 0) {
                PaymentStatus.CANCELED
            } else {
                PaymentStatus.PARTIAL_CANCELED
            }

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
        } else {
            throw PaymentException(ErrorCode.PG_CANCEL_FAILED)
        }
        payment.updatedAt = LocalDateTime.now()

        return payment
    }
}
