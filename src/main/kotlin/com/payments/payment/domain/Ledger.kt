package com.payments.payment.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "ledger",
    indexes = [Index(name = "idx_ledger_payment_id", columnList = "paymentId")]
)
class Ledger(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    val paymentId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val event: LedgerEvent,

    @Column(nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val signedAmount: BigDecimal,

    @Column(nullable = false, length = 36)
    val groupId: String,

    @Column(nullable = false)
    val isCancellation: Boolean = false,

    @Column
    val parentLedgerId: Long? = null,

    @Column(length = 500)
    val memo: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun approved(payment: Payment) = Ledger(
            paymentId = payment.id,
            event = LedgerEvent.PAYMENT_APPROVED,
            amount = payment.amount,
            signedAmount = payment.amount,
            groupId = UUID.randomUUID().toString(),
        )

        fun captured(payment: Payment) = Ledger(
            paymentId = payment.id,
            event = LedgerEvent.PAYMENT_CAPTURED,
            amount = payment.amount,
            signedAmount = payment.amount,
            groupId = UUID.randomUUID().toString(),
        )

        fun canceled(payment: Payment, cancelAmount: BigDecimal, parentLedgerId: Long?) = Ledger(
            paymentId = payment.id,
            event = LedgerEvent.PAYMENT_CANCELED,
            amount = cancelAmount,
            signedAmount = cancelAmount.negate(),
            groupId = UUID.randomUUID().toString(),
            isCancellation = true,
            parentLedgerId = parentLedgerId,
        )

        fun failed(payment: Payment) = Ledger(
            paymentId = payment.id,
            event = LedgerEvent.PAYMENT_FAILED,
            amount = payment.amount,
            signedAmount = payment.amount.negate(),
            groupId = UUID.randomUUID().toString(),
            isCancellation = true,
        )
    }
}
