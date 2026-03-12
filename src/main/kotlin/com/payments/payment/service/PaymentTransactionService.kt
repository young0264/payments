package com.payments.payment.service

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.payment.domain.Payment
import com.payments.payment.domain.PaymentStatus
import com.payments.payment.repository.PaymentRepository
import com.payments.pg.connector.PgConnector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PaymentTransactionService(
    private val paymentRepository: PaymentRepository,
    private val pgConnector: PgConnector,
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

        // PG 승인 요청
        val pgResponse = pgConnector.approve(orderId, amount)

        if (pgResponse.success) {
            // 금액 위변조 검증: PG 승인 금액과 요청 금액 비교
            if (pgResponse.amount != null && pgResponse.amount.compareTo(amount) != 0) {
                pgResponse.pgTransactionId?.let { txId ->
                    pgConnector.cancel(txId, pgResponse.amount)
                }
                payment.status = PaymentStatus.FAILED
                payment.pgProvider = pgConnector.providerName
                payment.pgTransactionId = pgResponse.pgTransactionId
                payment.failReason = "금액 위변조 감지: 요청=${amount}, PG승인=${pgResponse.amount}"
                payment.updatedAt = LocalDateTime.now()
                return payment
            }

            payment.status = PaymentStatus.APPROVED
            payment.pgProvider = pgConnector.providerName
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

        val pgResponse = pgConnector.capture(txId, payment.amount)

        if (pgResponse.success) {
            payment.status = PaymentStatus.CAPTURED
        } else {
            throw PaymentException(ErrorCode.PG_CAPTURE_FAILED)
        }
        payment.updatedAt = LocalDateTime.now()

        return payment
    }

    @Transactional
    fun cancel(orderId: String): Payment {
        val payment = paymentRepository.findByOrderId(orderId)
            ?: throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)

        if (payment.status != PaymentStatus.APPROVED && payment.status != PaymentStatus.CAPTURED) {
            throw PaymentException(ErrorCode.INVALID_PAYMENT_STATUS)
        }

        val txId = requireNotNull(payment.pgTransactionId) {
            "pgTransactionId is null for orderId=${payment.orderId}"
        }

        val pgResponse = pgConnector.cancel(txId, payment.amount)

        if (pgResponse.success) {
            payment.status = PaymentStatus.CANCELED
        } else {
            throw PaymentException(ErrorCode.PG_CANCEL_FAILED)
        }
        payment.updatedAt = LocalDateTime.now()

        return payment
    }
}
