package com.payments.payment.repository

import com.payments.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: String): Payment?
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByPgTransactionId(pgTransactionId: String): Payment?
}
