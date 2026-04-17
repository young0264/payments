package com.payments.settlement.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "settlement")
class Settlement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, unique = true, length = 64)
    val orderId: String,

    @Column(nullable = false)
    val merchantId: Long,

    @Column(nullable = false, length = 50)
    val pgProvider: String,

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val feeAmount: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val netAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SettlementStatus = SettlementStatus.PENDING,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    var settledAt: LocalDateTime? = null,
)
