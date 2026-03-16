package com.payments.payment.service

import com.payments.payment.domain.PaymentStatus
import com.payments.payment.repository.PaymentRepository
import com.payments.pg.connector.PgConnector
import com.payments.pg.connector.PgResponse
import com.payments.pg.router.PgRouter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
class AmountVerificationTest {

    @Autowired lateinit var paymentTransactionService: PaymentTransactionService
    @Autowired lateinit var paymentRepository: PaymentRepository
    @MockitoBean lateinit var pgRouter: PgRouter

    @Test
    fun `금액 불일치 시 자동 취소 후 FAILED 처리`() {
        val requestAmount = BigDecimal(10000)
        val pgApprovedAmount = BigDecimal(9000)

        whenever(pgRouter.approve(eq("order-tampered"), eq(requestAmount))).thenReturn(
            PgResponse(
                success = true,
                pgTransactionId = "mock-tx-123",
                message = "승인 완료",
                amount = pgApprovedAmount,
                providerName = "MOCK_PG_A",
            )
        )

        val mockConnector = mock<PgConnector>()
        whenever(pgRouter.getConnector(eq("MOCK_PG_A"))).thenReturn(mockConnector)
        whenever(mockConnector.cancel(eq("mock-tx-123"), eq(pgApprovedAmount))).thenReturn(
            PgResponse(
                success = true,
                pgTransactionId = "mock-tx-123",
                message = "취소 완료",
            )
        )

        val payment = paymentTransactionService.approve("order-tampered", "key-tampered", requestAmount)

        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.failReason).contains("금액 위변조 감지")
        assertThat(payment.failReason).contains("10000")
        assertThat(payment.failReason).contains("9000")
        verify(mockConnector).cancel(eq("mock-tx-123"), eq(pgApprovedAmount))
    }

    @Test
    fun `금액 일치 시 정상 승인`() {
        val amount = BigDecimal(10000)

        whenever(pgRouter.approve(eq("order-ok"), eq(amount))).thenReturn(
            PgResponse(
                success = true,
                pgTransactionId = "mock-tx-456",
                message = "승인 완료",
                amount = amount,
                providerName = "MOCK_PG_A",
            )
        )

        val payment = paymentTransactionService.approve("order-ok", "key-ok", amount)

        assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(payment.pgTransactionId).isEqualTo("mock-tx-456")
    }
}
