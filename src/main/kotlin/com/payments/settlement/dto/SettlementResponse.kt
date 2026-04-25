package com.payments.settlement.dto

import com.payments.settlement.domain.Settlement
import com.payments.settlement.domain.SettlementStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class SettlementResponse(
    val orderId: String,
    val pgProvider: String,
    val amount: BigDecimal,
    val feeAmount: BigDecimal,
    val netAmount: BigDecimal,
    val status: SettlementStatus,
    val createdAt: LocalDateTime,
    val settledAt: LocalDateTime?,
) {
    companion object {
        fun from(settlement: Settlement) = SettlementResponse(
            orderId = settlement.orderId,
            pgProvider = settlement.pgProvider,
            amount = settlement.amount,
            feeAmount = settlement.feeAmount,
            netAmount = settlement.netAmount,
            status = settlement.status,
            createdAt = settlement.createdAt,
            settledAt = settlement.settledAt,
        )
    }
}
