package com.payments.reconciliation.dto

import java.math.BigDecimal

data class PgCsvRow(
    val pgTransactionId: String,
    val amount: BigDecimal,
)
