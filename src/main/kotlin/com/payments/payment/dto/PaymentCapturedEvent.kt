package com.payments.payment.dto

import java.math.BigDecimal

data class PaymentCapturedEvent(
    val orderId: String,
    val merchantId: Long,
    val amount: BigDecimal,
    val pgProvider: String,
)
