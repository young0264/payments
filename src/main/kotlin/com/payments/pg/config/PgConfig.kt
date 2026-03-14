package com.payments.pg.config

import com.payments.pg.connector.CircuitBreakerPgConnector
import com.payments.pg.connector.PgConnector
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PgConfig {

    @Bean
    fun circuitBreakerPgConnectorA(
        @Qualifier("mockPgA") delegate: PgConnector,
        circuitBreakerRegistry: CircuitBreakerRegistry,
    ): CircuitBreakerPgConnector {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-mock-a")
        return CircuitBreakerPgConnector(delegate, circuitBreaker)
    }

    @Bean
    fun circuitBreakerPgConnectorB(
        @Qualifier("mockPgB") delegate: PgConnector,
        circuitBreakerRegistry: CircuitBreakerRegistry,
    ): CircuitBreakerPgConnector {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-mock-b")
        return CircuitBreakerPgConnector(delegate, circuitBreaker)
    }
}
