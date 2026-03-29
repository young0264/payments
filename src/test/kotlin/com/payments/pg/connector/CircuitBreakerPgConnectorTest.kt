package com.payments.pg.connector

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Duration

class CircuitBreakerPgConnectorTest {

    private lateinit var delegate: PgConnector
    private lateinit var sut: CircuitBreakerPgConnector

    @BeforeEach
    fun setUp() {
        delegate = mock()

        val cbConfig = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50f)
            .build()
        val circuitBreaker = CircuitBreakerRegistry.of(cbConfig)
            .circuitBreaker("test")

        val retryConfig = RetryConfig.custom<PgResponse>()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(10))
            .retryExceptions(RuntimeException::class.java)
            .ignoreExceptions(PaymentException::class.java)
            .build()
        val retry = RetryRegistry.of(retryConfig)
            .retry("test")

        sut = CircuitBreakerPgConnector(delegate, circuitBreaker, retry)
    }

    @Test
    fun `정상 호출 시 PG 응답 그대로 반환`() {
        val expected = PgResponse(true, "pg-123", "승인 완료", BigDecimal(1000))
        whenever(delegate.approve(any(), any())).thenReturn(expected)

        val result = sut.approve("order-1", BigDecimal(1000))

        assertEquals(expected, result)
        verify(delegate, times(1)).approve(any(), any())
    }

    @Test
    fun `실패율 초과 시 서킷 OPEN - PG_CIRCUIT_BREAKER_OPEN 에러`() {
        whenever(delegate.approve(any(), any()))
            .thenThrow(RuntimeException("PG 타임아웃"))

        // minimumNumberOfCalls(2) + failureRate(50%)
        // 재시도 3회 × 2번 = delegate 6번 호출, 서킷에 실패 2건 기록 → OPEN
        repeat(2) {
            assertThrows(RuntimeException::class.java) {
                sut.approve("order-1", BigDecimal(1000))
            }
        }

        // 서킷 OPEN 상태에서 호출 → PG_CIRCUIT_BREAKER_OPEN
        val ex = assertThrows(PaymentException::class.java) {
            sut.approve("order-1", BigDecimal(1000))
        }
        assertEquals(ErrorCode.PG_CIRCUIT_BREAKER_OPEN, ex.errorCode)
    }

    @Test
    fun `PG 비즈니스 실패(success=false)는 서킷에 영향 없음`() {
        val failResponse = PgResponse(false, null, "잔액 부족")
        whenever(delegate.approve(any(), any())).thenReturn(failResponse)

        repeat(5) {
            val result = sut.approve("order-1", BigDecimal(1000))
            assertFalse(result.success)
        }

        val result = sut.approve("order-1", BigDecimal(1000))
        assertFalse(result.success)
        verify(delegate, times(6)).approve(any(), any())
    }

    @Test
    fun `재시도 후 성공하면 최종 성공 반환`() {
        val success = PgResponse(true, "pg-123", "승인 완료", BigDecimal(1000))
        whenever(delegate.approve(any(), any()))
            .thenThrow(RuntimeException("일시적 오류"))
            .thenThrow(RuntimeException("일시적 오류"))
            .thenReturn(success)

        val result = sut.approve("order-1", BigDecimal(1000))

        assertEquals(success, result)
        verify(delegate, times(3)).approve(any(), any())
    }

    @Test
    fun `재시도 3회 모두 실패하면 예외 전파`() {
        whenever(delegate.approve(any(), any()))
            .thenThrow(RuntimeException("PG 타임아웃"))

        assertThrows(RuntimeException::class.java) {
            sut.approve("order-1", BigDecimal(1000))
        }
        verify(delegate, times(3)).approve(any(), any())
    }

    @Test
    fun `PaymentException은 재시도하지 않음`() {
        whenever(delegate.approve(any(), any()))
            .thenThrow(PaymentException(ErrorCode.PG_APPROVE_FAILED))

        assertThrows(PaymentException::class.java) {
            sut.approve("order-1", BigDecimal(1000))
        }
        verify(delegate, times(1)).approve(any(), any())
    }
}
