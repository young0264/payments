package com.payments.payment.dto

import com.payments.payment.domain.Payment
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentResponse(
    val id: Long,
    val orderId: String,
    val amount: BigDecimal,
    val canceledAmount: BigDecimal,
    val cancelableAmount: BigDecimal,
    val status: String,
    val pgProvider: String?,
    val pgTransactionId: String?,
    val failReason: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            id = payment.id,
            orderId = payment.orderId,
            amount = payment.amount,
            canceledAmount = payment.canceledAmount,
            cancelableAmount = payment.cancelableAmount,
            status = payment.status.name,
            pgProvider = payment.pgProvider,
            pgTransactionId = payment.pgTransactionId,
            failReason = payment.failReason,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt,
        )
    }
}
