package com.payments.pg.router

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.pg.connector.CircuitBreakerPgConnector
import com.payments.pg.connector.PgResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal

class PgRouterTest {

    private lateinit var connectorA: CircuitBreakerPgConnector
    private lateinit var connectorB: CircuitBreakerPgConnector
    private lateinit var sut: PgRouter

    @BeforeEach
    fun setUp() {
        connectorA = mock()
        connectorB = mock()
        whenever(connectorA.providerName).thenReturn("MOCK_PG_A")
        whenever(connectorB.providerName).thenReturn("MOCK_PG_B")
        sut = PgRouter(listOf(connectorA, connectorB))
    }

    @Test
    fun `정상 호출 시 primary PG로 라우팅`() {
        val expected = PgResponse(true, "mock-a-123", "승인 완료", BigDecimal(1000))
        whenever(connectorA.approve(any(), any())).thenReturn(expected)

        val result = sut.approve("order-1", BigDecimal(1000))

        assertEquals("MOCK_PG_A", result.providerName)
        verify(connectorA).approve(any(), any())
        verify(connectorB, never()).approve(any(), any())
    }

    @Test
    fun `primary PG 서킷 OPEN 시 fallback PG로 라우팅`() {
        whenever(connectorA.approve(any(), any()))
            .thenThrow(PaymentException(ErrorCode.PG_CIRCUIT_BREAKER_OPEN))
        val expected = PgResponse(true, "mock-b-123", "승인 완료", BigDecimal(1000))
        whenever(connectorB.approve(any(), any())).thenReturn(expected)

        val result = sut.approve("order-1", BigDecimal(1000))

        assertEquals("MOCK_PG_B", result.providerName)
    }

    @Test
    fun `모든 PG 서킷 OPEN 시 ALL_PG_UNAVAILABLE`() {
        whenever(connectorA.approve(any(), any()))
            .thenThrow(PaymentException(ErrorCode.PG_CIRCUIT_BREAKER_OPEN))
        whenever(connectorB.approve(any(), any()))
            .thenThrow(PaymentException(ErrorCode.PG_CIRCUIT_BREAKER_OPEN))

        val ex = assertThrows(PaymentException::class.java) {
            sut.approve("order-1", BigDecimal(1000))
        }
        assertEquals(ErrorCode.ALL_PG_UNAVAILABLE, ex.errorCode)
    }

    @Test
    fun `approve 응답에 providerName 포함`() {
        val response = PgResponse(true, "mock-a-123", "승인 완료", BigDecimal(1000))
        whenever(connectorA.approve(any(), any())).thenReturn(response)

        val result = sut.approve("order-1", BigDecimal(1000))

        assertNotNull(result.providerName)
    }

    @Test
    fun `getConnector로 특정 PG 조회`() {
        val connector = sut.getConnector("MOCK_PG_B")

        assertEquals("MOCK_PG_B", connector.providerName)
    }

    @Test
    fun `존재하지 않는 PG 조회 시 PG_PROVIDER_NOT_FOUND`() {
        val ex = assertThrows(PaymentException::class.java) {
            sut.getConnector("INVALID_PG")
        }
        assertEquals(ErrorCode.PG_PROVIDER_NOT_FOUND, ex.errorCode)
    }
}
