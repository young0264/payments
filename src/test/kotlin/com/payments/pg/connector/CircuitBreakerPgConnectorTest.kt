package com.payments.pg.connector

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.pg.mock.MockPgConnector
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

class CircuitBreakerPgConnectorTest {

    private lateinit var mockPgConnector: MockPgConnector
    private lateinit var sut: CircuitBreakerPgConnector

    @BeforeEach
    fun setUp() {
        mockPgConnector = mock()
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50f)
            .build()
        val registry = CircuitBreakerRegistry.of(config)
        sut = CircuitBreakerPgConnector(mockPgConnector, registry)
    }

    @Test
    fun `정상 호출 시 PG 응답 그대로 반환`() {
        val expected = PgResponse(true, "pg-123", "승인 완료", BigDecimal(1000))
        whenever(mockPgConnector.approve(any(), any())).thenReturn(expected)

        val result = sut.approve("order-1", BigDecimal(1000))

        assertEquals(expected, result)
    }

    @Test
    fun `실패율 초과 시 서킷 OPEN - PG_CIRCUIT_BREAKER_OPEN 에러`() {
        whenever(mockPgConnector.approve(any(), any()))
            .thenThrow(RuntimeException("PG 타임아웃"))

        // minimumNumberOfCalls(2) + failureRate(50%) → 2번 실패로 서킷 OPEN
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
        whenever(mockPgConnector.approve(any(), any())).thenReturn(failResponse)

        repeat(5) {
            val result = sut.approve("order-1", BigDecimal(1000))
            assertFalse(result.success)
        }

        // 서킷이 열리지 않고 정상 호출 계속 가능
        val result = sut.approve("order-1", BigDecimal(1000))
        assertFalse(result.success)
        verify(mockPgConnector, times(6)).approve(any(), any())
    }
}
