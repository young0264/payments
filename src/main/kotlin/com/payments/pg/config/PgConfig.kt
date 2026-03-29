package com.payments.pg.config

import com.payments.pg.connector.CircuitBreakerPgConnector
import com.payments.pg.connector.PgConnector
import com.payments.pg.router.PgRouter
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PgConfig {

    @Bean
    fun circuitBreakerPgConnectorA(
        @Qualifier("mockPgA") delegate: PgConnector,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
    ): CircuitBreakerPgConnector {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-mock-a")
        val retry = retryRegistry.retry("pg-mock-a")
        return CircuitBreakerPgConnector(delegate, circuitBreaker, retry)
    }

    @Bean
    fun circuitBreakerPgConnectorB(
        @Qualifier("mockPgB") delegate: PgConnector,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
    ): CircuitBreakerPgConnector {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-mock-b")
        val retry = retryRegistry.retry("pg-mock-b")
        return CircuitBreakerPgConnector(delegate, circuitBreaker, retry)
    }

    @Bean
    fun pgRouter(
        circuitBreakerPgConnectorA: CircuitBreakerPgConnector,
        circuitBreakerPgConnectorB: CircuitBreakerPgConnector,
    ): PgRouter {
        return PgRouter(listOf(circuitBreakerPgConnectorA, circuitBreakerPgConnectorB))
    }
}
