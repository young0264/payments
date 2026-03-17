package com.payments.payment.repository

import com.payments.payment.domain.PaymentCancelHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface PaymentCancelHistoryRepository : JpaRepository<PaymentCancelHistory, Long> {
    @Transactional
    fun deleteAllByPaymentId(paymentId: Long)
}
