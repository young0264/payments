package com.payments.payment.domain

enum class PaymentStatus {
    READY,
    APPROVED,
    CAPTURED,
    CANCELED,
    PARTIAL_CANCELED,
    FAILED,
}
