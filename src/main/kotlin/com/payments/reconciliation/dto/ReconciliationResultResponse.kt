package com.payments.reconciliation.dto

import com.payments.reconciliation.domain.ReconciliationResult
import com.payments.reconciliation.domain.ReconciliationStatus
import java.math.BigDecimal

data class ReconciliationResultResponse(
    val pgTransactionId: String,
    val orderId: String,
    val internalAmount: BigDecimal,
    val pgAmount: BigDecimal,
    val status: ReconciliationStatus,
) {
    companion object {
        fun from(result: ReconciliationResult) = ReconciliationResultResponse(
            pgTransactionId = result.pgTransactionId,
            orderId = result.orderId,
            internalAmount = result.internalAmount,
            pgAmount = result.pgAmount,
            status = result.status,
        )
    }
}
