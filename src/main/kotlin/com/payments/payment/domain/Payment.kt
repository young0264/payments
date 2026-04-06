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

    @Column(nullable = false, unique = true, length = 36)
    val idempotencyKey: String,

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    var canceledAmount: BigDecimal = BigDecimal.ZERO,

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
) {
    val cancelableAmount: BigDecimal
        get() = amount - canceledAmount

    fun approve(pgTransactionId: String, pgProvider: String) {
        check(status == PaymentStatus.READY) { "승인 가능한 상태가 아님: $status" }
        this.status = PaymentStatus.APPROVED
        this.pgTransactionId = pgTransactionId
        this.pgProvider = pgProvider
        this.updatedAt = LocalDateTime.now()
    }

    fun capture() {
        check(status == PaymentStatus.APPROVED) { "매입 가능한 상태가 아님: $status" }
        this.status = PaymentStatus.CAPTURED
        this.updatedAt = LocalDateTime.now()
    }

    fun cancel(cancelAmount: BigDecimal) {
        check(
            status in listOf(
                PaymentStatus.APPROVED,
                PaymentStatus.CAPTURED,
                PaymentStatus.PARTIAL_CANCELED,
            )
        ) { "취소 가능한 상태가 아님: $status" }
        this.canceledAmount += cancelAmount
        this.status = if (cancelableAmount.compareTo(BigDecimal.ZERO) == 0) {
            PaymentStatus.CANCELED
        } else {
            PaymentStatus.PARTIAL_CANCELED
        }
        this.updatedAt = LocalDateTime.now()
    }

    fun fail(reason: String, pgTransactionId: String? = null, pgProvider: String? = null) {
        this.status = PaymentStatus.FAILED
        this.failReason = reason
        this.pgTransactionId = pgTransactionId
        this.pgProvider = pgProvider
        this.updatedAt = LocalDateTime.now()
    }
}
