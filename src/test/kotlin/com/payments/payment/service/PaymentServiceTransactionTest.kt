package com.payments.payment.service

import com.payments.payment.domain.PaymentStatus
import com.payments.payment.repository.PaymentCancelHistoryRepository
import com.payments.payment.repository.PaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
class PaymentServiceTransactionTest {

    @Autowired lateinit var paymentService: PaymentService
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var cancelHistoryRepository: PaymentCancelHistoryRepository

    private lateinit var orderId: String
    private lateinit var idempotencyKey: String

    @BeforeEach
    fun setUp() {
        orderId = "tx-test-${UUID.randomUUID()}"
        idempotencyKey = UUID.randomUUID().toString()
    }

    @AfterEach
    fun tearDown() {
        paymentRepository.findByOrderId(orderId)?.let {
            cancelHistoryRepository.deleteAllByPaymentId(it.id)
            paymentRepository.delete(it)
        }
    }

    @Test
    fun `approve 후 DB에 APPROVED 상태가 커밋되는지 확인`() {
        paymentService.approve(orderId, idempotencyKey, BigDecimal(10000))

        val fromDb = paymentRepository.findByOrderId(orderId)!!

        assertThat(fromDb.status).isEqualTo(PaymentStatus.APPROVED)
    }

    @Test
    fun `approve 후 capture 호출 시 CAPTURED 상태가 DB에 커밋되는지 확인`() {
        paymentService.approve(orderId, idempotencyKey, BigDecimal(5000))
        paymentService.capture(orderId)

        val fromDb = paymentRepository.findByOrderId(orderId)!!

        assertThat(fromDb.status).isEqualTo(PaymentStatus.CAPTURED)
    }

    @Test
    fun `approve 후 cancel 호출 시 CANCELED 상태가 DB에 커밋되는지 확인`() {
        paymentService.approve(orderId, idempotencyKey, BigDecimal(3000))
        paymentService.cancel(orderId)

        val fromDb = paymentRepository.findByOrderId(orderId)!!

        assertThat(fromDb.status).isEqualTo(PaymentStatus.CANCELED)
    }
}
