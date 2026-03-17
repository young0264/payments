package com.payments.payment.dto

import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

data class CancelRequest(
    @field:NotBlank
    val orderId: String,
    val cancelAmount: BigDecimal? = null,
    val cancelReason: String? = null,
)
