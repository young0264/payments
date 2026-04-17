package com.payments.settlement.repository

import com.payments.settlement.domain.Settlement
import com.payments.settlement.domain.SettlementStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface SettlementRepository : JpaRepository<Settlement, Long> {

    fun existsByOrderId(orderId: String): Boolean

    @Modifying
    @Query("UPDATE Settlement s SET s.status = :status, s.settledAt = :settledAt WHERE s.status = 'PENDING'")
    fun completeAllPending(
        status: SettlementStatus = SettlementStatus.COMPLETED,
        settledAt: LocalDateTime = LocalDateTime.now(),
    ): Int
}
