package com.payments.payment.repository

import com.payments.payment.domain.Ledger
import com.payments.payment.domain.LedgerEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface LedgerRepository : JpaRepository<Ledger, Long> {

    fun findAllByPaymentId(paymentId: Long): List<Ledger>

    fun findTopByPaymentIdAndEventOrderByCreatedAtDesc(
        paymentId: Long,
        event: LedgerEvent,
    ): Ledger?

    @Modifying
    @Query("DELETE FROM Ledger l WHERE l.paymentId = :paymentId")
    fun deleteAllByPaymentId(paymentId: Long)
}
