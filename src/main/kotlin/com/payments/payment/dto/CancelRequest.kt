package com.payments.payment.dto

import jakarta.validation.constraints.NotBlank

data class CancelRequest(
    @field:NotBlank
    val orderId: String,
)
