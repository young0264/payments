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
        val payment = paymentService.approve("order-1", "key-1", BigDecimal(10000), merchantId = 1L)

        assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(payment.pgProvider).isEqualTo("MOCK_PG_A")
        assertThat(payment.pgTransactionId).isNotNull()
    }

    @Test
    fun `멱등성 체크 - 같은 키로 재요청시 기존 결제 리턴`() {
        val first = paymentService.approve("order-2", "key-2", BigDecimal(5000), merchantId = 1L)
        val second = paymentService.approve("order-2", "key-2", BigDecimal(5000), merchantId = 1L)

        assertThat(first.id).isEqualTo(second.id)
    }

    @Test
    fun `중복 주문 시 예외 발생`() {
        paymentService.approve("order-3", "key-3", BigDecimal(3000), merchantId = 1L)

        assertThrows<PaymentException> {
            paymentService.approve("order-3", "key-4", BigDecimal(3000), merchantId = 1L)
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.DUPLICATE_ORDER)
        }
    }

    @Test
    fun `결제 취소 성공`() {
        paymentService.approve("order-4", "key-4", BigDecimal(7000), merchantId = 1L)
        val canceled = paymentService.cancel("order-4")

        assertThat(canceled.status).isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    fun `APPROVED 또는 CAPTURED 상태가 아니면 취소 불가`() {
        paymentService.approve("order-5", "key-5", BigDecimal(2000), merchantId = 1L)
        paymentService.cancel("order-5")

        assertThrows<PaymentException> {
            paymentService.cancel("order-5")
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

    @Test
    fun `승인 시 pgTransactionId가 저장된다`() {
        val payment = paymentService.approve("order-6", "key-6", BigDecimal(15000), merchantId = 1L)

        assertThat(payment.pgTransactionId).isNotNull()
        assertThat(payment.pgTransactionId).startsWith("mock-a-")
    }

    @Test
    fun `결제 조회 성공`() {
        val approved = paymentService.approve("order-7", "key-7", BigDecimal(8000), merchantId = 1L)
        val found = paymentService.getByOrderId("order-7")

        assertThat(found.id).isEqualTo(approved.id)
        assertThat(found.orderId).isEqualTo("order-7")
        assertThat(found.amount).isEqualByComparingTo(BigDecimal(8000))
        assertThat(found.status).isEqualTo(PaymentStatus.APPROVED)
    }

    @Test
    fun `존재하지 않는 orderId 조회 시 PAYMENT_NOT_FOUND`() {
        assertThrows<PaymentException> {
            paymentService.getByOrderId("non-existent-order")
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
        }
    }

    @Test
    fun `매입 성공`() {
        paymentService.approve("order-capture-1", "key-capture-1", BigDecimal(10000), merchantId = 1L)
        val captured = paymentService.capture("order-capture-1")

        assertThat(captured.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(captured.pgTransactionId).isNotNull()
    }

    @Test
    fun `APPROVED가 아닌 상태에서 매입 시 예외`() {
        paymentService.approve("order-capture-2", "key-capture-2", BigDecimal(5000), merchantId = 1L)
        paymentService.capture("order-capture-2")

        assertThrows<PaymentException> {
            paymentService.capture("order-capture-2")
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

    @Test
    fun `매입 후 취소 성공`() {
        paymentService.approve("order-capture-3", "key-capture-3", BigDecimal(8000), merchantId = 1L)
        paymentService.capture("order-capture-3")
        val canceled = paymentService.cancel("order-capture-3")

        assertThat(canceled.status).isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    fun `취소 후에도 pgTransactionId는 유지된다`() {
        val approved = paymentService.approve("order-8", "key-8", BigDecimal(20000), merchantId = 1L)
        val pgTxId = approved.pgTransactionId

        val canceled = paymentService.cancel("order-8")

        assertThat(canceled.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(canceled.pgTransactionId).isEqualTo(pgTxId)
    }

    @Test
    fun `부분 취소 성공 - 10000원 중 3000원`() {
        paymentService.approve("order-pc-1", "key-pc-1", BigDecimal(10000), merchantId = 1L)
        val canceled = paymentService.cancel("order-pc-1", BigDecimal(3000))

        assertThat(canceled.status).isEqualTo(PaymentStatus.PARTIAL_CANCELED)
        assertThat(canceled.canceledAmount).isEqualByComparingTo(BigDecimal(3000))
        assertThat(canceled.cancelableAmount).isEqualByComparingTo(BigDecimal(7000))
    }

    @Test
    fun `연속 부분 취소 - PARTIAL_CANCELED에서 추가 취소`() {
        paymentService.approve("order-pc-2", "key-pc-2", BigDecimal(10000), merchantId = 1L)
        paymentService.cancel("order-pc-2", BigDecimal(3000))
        val canceled = paymentService.cancel("order-pc-2", BigDecimal(2000))

        assertThat(canceled.status).isEqualTo(PaymentStatus.PARTIAL_CANCELED)
        assertThat(canceled.canceledAmount).isEqualByComparingTo(BigDecimal(5000))
        assertThat(canceled.cancelableAmount).isEqualByComparingTo(BigDecimal(5000))
    }

    @Test
    fun `잔액 전부 취소 시 CANCELED`() {
        paymentService.approve("order-pc-3", "key-pc-3", BigDecimal(10000), merchantId = 1L)
        paymentService.cancel("order-pc-3", BigDecimal(7000))
        val canceled = paymentService.cancel("order-pc-3", BigDecimal(3000))

        assertThat(canceled.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(canceled.cancelableAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `취소 금액 초과 시 에러`() {
        paymentService.approve("order-pc-4", "key-pc-4", BigDecimal(10000), merchantId = 1L)

        assertThrows<PaymentException> {
            paymentService.cancel("order-pc-4", BigDecimal(15000))
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELABLE)
        }
    }

    @Test
    fun `cancelAmount null이면 전체 취소`() {
        paymentService.approve("order-pc-5", "key-pc-5", BigDecimal(10000), merchantId = 1L)
        val canceled = paymentService.cancel("order-pc-5")

        assertThat(canceled.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(canceled.canceledAmount).isEqualByComparingTo(BigDecimal(10000))
    }

    @Test
    fun `부분 취소 후 cancelAmount null이면 잔액 전체 취소`() {
        paymentService.approve("order-pc-6", "key-pc-6", BigDecimal(10000), merchantId = 1L)
        paymentService.cancel("order-pc-6", BigDecimal(3000))
        val canceled = paymentService.cancel("order-pc-6")

        assertThat(canceled.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(canceled.canceledAmount).isEqualByComparingTo(BigDecimal(10000))
    }

    @Test
    fun `CANCELED 상태에서 추가 취소 불가`() {
        paymentService.approve("order-pc-7", "key-pc-7", BigDecimal(10000), merchantId = 1L)
        paymentService.cancel("order-pc-7")

        assertThrows<PaymentException> {
            paymentService.cancel("order-pc-7", BigDecimal(1000))
        }.also {
            assertThat(it.errorCode).isEqualTo(ErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

}
