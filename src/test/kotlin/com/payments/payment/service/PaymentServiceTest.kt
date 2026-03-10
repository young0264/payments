package com.payments.payment.service

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.payment.domain.PaymentStatus
import com.payments.payment.repository.PaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@Transactional
class PaymentServiceTest {

    @Autowired lateinit var paymentService: PaymentService
    @Autowired lateinit var paymentRepository: PaymentRepository

    @Test
    fun `결제 승인 성공`() {
        val payment = paymentService.approve("order-1", "key-1", BigDecimal(10000))

        assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(payment.pgProvider).isEqualTo("MOCK_PG")
        assertThat(payment.pgTransactionId).isNotNull()
    }

    @Test
    fun `멱등성 체크 - 같은 키로 재요청시 기존 결제 리턴`() {
        val first = paymentService.approve("order-2", "key-2", BigDecimal(5000))
        val second = paymentService.approve("order-2", "key-2", BigDecimal(5000))

        assertThat(first.id).isEqualTo(second.id)
    }

    @Test
    fun `중복 주문 시 예외 발생`() {
        paymentService.approve("order-3", "key-3", BigDecimal(3000))

        assertThrows<PaymentException> {
            paymentService.approve("order-3", "key-4", BigDecimal(3000))
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.DUPLICATE_ORDER)
        }
    }

    @Test
    fun `결제 취소 성공`() {
        paymentService.approve("order-4", "key-4", BigDecimal(7000))
        val canceled = paymentService.cancel("order-4")

        assertThat(canceled.status).isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    fun `APPROVED 상태가 아니면 취소 불가`() {
        paymentService.approve("order-5", "key-5", BigDecimal(2000))
        paymentService.cancel("order-5")

        assertThrows<PaymentException> {
            paymentService.cancel("order-5")
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS)
        }
    }
}
