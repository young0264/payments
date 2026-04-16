package com.payments.merchant.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "merchant")
class Merchant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false, length = 100)
    val name: String,

    val createdAt: LocalDateTime = LocalDateTime.now(),
)
