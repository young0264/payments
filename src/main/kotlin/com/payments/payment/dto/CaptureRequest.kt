package com.payments.payment.dto

import jakarta.validation.constraints.NotBlank

data class CaptureRequest(
    @field:NotBlank
    val orderId: String,
)
