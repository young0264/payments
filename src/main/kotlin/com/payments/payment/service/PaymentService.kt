package com.payments.payment.service

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.common.lock.DistributedLock
import com.payments.payment.domain.Payment
import com.payments.payment.repository.PaymentRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentTransactionService: PaymentTransactionService,
    private val distributedLock: DistributedLock,
) {

    fun approve(orderId: String, idempotencyKey: String, amount: BigDecimal, merchantId: Long): Payment {
        return distributedLock.withLock("payment:$orderId") {
            paymentTransactionService.approve(orderId, idempotencyKey, amount, merchantId)
        }
    }

    fun capture(orderId: String): Payment {
        return distributedLock.withLock("payment:$orderId") {
            paymentTransactionService.capture(orderId)
        }
    }

    fun cancel(orderId: String, cancelAmount: BigDecimal? = null, cancelReason: String? = null): Payment {
        return distributedLock.withLock("payment:$orderId") {
            paymentTransactionService.cancel(orderId, cancelAmount, cancelReason)
        }
    }

    fun getByOrderId(orderId: String): Payment {
        return paymentRepository.findByOrderId(orderId)
            ?: throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)
    }
}
