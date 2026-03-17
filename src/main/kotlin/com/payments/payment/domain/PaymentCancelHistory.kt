package com.payments.payment.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment_cancel_history")
class PaymentCancelHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    val paymentId: Long,

    @Column(nullable = false, precision = 15, scale = 2)
    val cancelAmount: BigDecimal,

    @Column(length = 200)
    val cancelReason: String? = null,

    @Column(nullable = false, precision = 15, scale = 2)
    val canceledAmountBefore: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val canceledAmountAfter: BigDecimal,

    @Column(length = 100)
    val pgCancelTransactionId: String? = null,

    val createdAt: LocalDateTime = LocalDateTime.now(),
)
