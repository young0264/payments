package com.payments.pg.connector

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.pg.mock.MockPgConnector
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.math.BigDecimal

// 데코레이터 패턴으로 서킷브레이커 적용
@Component
@Primary
class CircuitBreakerPgConnector(
    private val delegate: MockPgConnector,
    circuitBreakerRegistry: CircuitBreakerRegistry,
) : PgConnector {

    private val log = LoggerFactory.getLogger(javaClass)
    private val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-connector")

    override val providerName: String get() = delegate.providerName

    override fun approve(orderId: String, amount: BigDecimal): PgResponse {
        return executeWithCircuitBreaker("approve") {
            delegate.approve(orderId, amount)
        }
    }

    override fun capture(pgTransactionId: String, amount: BigDecimal): PgResponse {
        return executeWithCircuitBreaker("capture") {
            delegate.capture(pgTransactionId, amount)
        }
    }

    override fun cancel(pgTransactionId: String, amount: BigDecimal): PgResponse {
        return executeWithCircuitBreaker("cancel") {
            delegate.cancel(pgTransactionId, amount)
        }
    }

    private fun executeWithCircuitBreaker(operation: String, supplier: () -> PgResponse): PgResponse {
        return try {
            circuitBreaker.executeSupplier { supplier() }
        } catch (e: CallNotPermittedException) {
            log.warn("Circuit breaker OPEN - {} 요청 차단됨: {}", operation, e.message)
            throw PaymentException(ErrorCode.PG_CIRCUIT_BREAKER_OPEN)
        }
    }
}
