package com.payments.pg.connector

import java.math.BigDecimal

interface PgConnector {
    val providerName: String
    fun approve(orderId: String, amount: BigDecimal): PgResponse
    fun capture(pgTransactionId: String, amount: BigDecimal): PgResponse
    fun cancel(pgTransactionId: String, amount: BigDecimal): PgResponse
}

data class PgResponse(
    val success: Boolean,
    val pgTransactionId: String?,
    val message: String?,
)
