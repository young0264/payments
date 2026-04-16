package com.payments.pg.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "pg_fee_policy")
class PgFeePolicy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, length = 50, unique = true)
    val pgProvider: String,

    @Column(nullable = false, precision = 5, scale = 4)
    val feeRate: BigDecimal,

    val createdAt: LocalDateTime = LocalDateTime.now(),
)
