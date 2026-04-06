package com.payments.payment.domain

enum class LedgerEvent {
    PAYMENT_APPROVED,
    PAYMENT_CAPTURED,
    PAYMENT_CANCELED,
    PAYMENT_FAILED
}
