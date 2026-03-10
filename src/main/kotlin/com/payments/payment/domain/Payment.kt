package com.payments.payment.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, unique = true, length = 64)
    val orderId: String,

    @Column(nullable = false, length = 36)
    val idempotencyKey: String,

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.READY,

    @Column(length = 30)
    var pgProvider: String? = null,

    @Column(length = 100)
    var pgTransactionId: String? = null,

    @Column(length = 500)
    var failReason: String? = null,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
