package com.payments.pg.router

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import com.payments.pg.connector.CircuitBreakerPgConnector
import com.payments.pg.connector.PgConnector
import com.payments.pg.connector.PgResponse
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class PgRouter(
    private val connectors: List<CircuitBreakerPgConnector>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun approve(orderId: String, amount: BigDecimal): PgResponse {
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

    fun getConnector(providerName: String): PgConnector {
        return connectors.find { it.providerName == providerName }
            ?: throw PaymentException(ErrorCode.PG_PROVIDER_NOT_FOUND)
    }
}
