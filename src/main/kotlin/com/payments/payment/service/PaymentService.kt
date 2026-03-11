package com.payments.payment.service

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.common.lock.DistributedLock
import com.payments.payment.domain.Payment
import com.payments.payment.domain.PaymentStatus
import com.payments.payment.repository.PaymentRepository
import com.payments.pg.connector.PgConnector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val pgConnector: PgConnector,
    private val distributedLock: DistributedLock,
) {

    fun approve(orderId: String, idempotencyKey: String, amount: BigDecimal): Payment {
        return distributedLock.withLock("payment:$orderId") {
            doApprove(orderId, idempotencyKey, amount)
        }
    }

    @Transactional
    protected fun doApprove(orderId: String, idempotencyKey: String, amount: BigDecimal): Payment {
        // 멱등성 체크: 같은 키로 이미 처리된 결제가 있으면 그대로 리턴
        paymentRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        //  APPROVED, CAPTURED, CANCELED → 이미 처리된 결제. 또 결제하면 안 됨 → DUPLICATE_ORDER
        //  FAILED                       → 이전에 실패한 건. 다시 시도해도 됨 → 통과

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

    fun getByOrderId(orderId: String): Payment {
        return paymentRepository.findByOrderId(orderId)
            ?: throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)
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
