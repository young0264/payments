package com.payments.reconciliation.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "reconciliation_result")
class ReconciliationResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, length = 100)
    val pgTransactionId: String,

    @Column(nullable = false, length = 64)
    val orderId: String,

    @Column(nullable = false, precision = 15, scale = 2)
    val internalAmount: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val pgAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: ReconciliationStatus,

    val createdAt: LocalDateTime = LocalDateTime.now(),
)
