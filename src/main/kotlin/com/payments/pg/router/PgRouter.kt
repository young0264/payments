package com.payments.pg.router

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.pg.connector.CircuitBreakerPgConnector
import com.payments.pg.connector.PgConnector
import com.payments.pg.connector.PgResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@Primary
class PgRouter(
    private val connectors: List<CircuitBreakerPgConnector>,
) : PgConnector {

    private val log = LoggerFactory.getLogger(javaClass)

    override val providerName: String get() = "PG_ROUTER"

    override fun approve(orderId: String, amount: BigDecimal): PgResponse {
        for (connector in connectors) {
            try {
                val response = connector.approve(orderId, amount)
                return response.copy(providerName = connector.providerName)
            } catch (e: PaymentException) {
                if (e.errorCode == ErrorCode.PG_CIRCUIT_BREAKER_OPEN) {
                    log.warn("PG {} 서킷 OPEN, 다음 PG로 fallback", connector.providerName)
                    continue
                }
                throw e
            }
        }
        throw PaymentException(ErrorCode.ALL_PG_UNAVAILABLE)
    }

    override fun capture(pgTransactionId: String, amount: BigDecimal): PgResponse {
        throw UnsupportedOperationException("capture는 getConnector()로 특정 PG를 지정해서 호출하세요")
    }

    override fun cancel(pgTransactionId: String, amount: BigDecimal): PgResponse {
        throw UnsupportedOperationException("cancel은 getConnector()로 특정 PG를 지정해서 호출하세요")
    }

    fun getConnector(providerName: String): PgConnector {
        return connectors.find { it.providerName == providerName }
            ?: throw PaymentException(ErrorCode.PG_PROVIDER_NOT_FOUND)
    }
}
