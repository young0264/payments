package com.payments.pg.connector

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class CircuitBreakerPgConnector(
    private val delegate: PgConnector,
    private val circuitBreaker: CircuitBreaker,
) : PgConnector {

    private val log = LoggerFactory.getLogger(javaClass)

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
