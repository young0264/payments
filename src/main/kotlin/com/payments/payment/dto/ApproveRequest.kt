package com.payments.payment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class ApproveRequest(
    @field:NotBlank
    val orderId: String,

    @field:NotBlank
    val idempotencyKey: String,

    @field:Positive
    val amount: BigDecimal,
)
