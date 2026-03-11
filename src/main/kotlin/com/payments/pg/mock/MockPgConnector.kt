package com.payments.pg.mock

import com.payments.pg.connector.PgConnector
import com.payments.pg.connector.PgResponse
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class MockPgConnector : PgConnector {

    override val providerName: String = "MOCK_PG"

    override fun approve(orderId: String, amount: BigDecimal): PgResponse {
        return PgResponse(
            success = true,
            pgTransactionId = "mock-${UUID.randomUUID()}",
            message = "승인 완료",
        )
    }

    override fun capture(pgTransactionId: String, amount: BigDecimal): PgResponse {
        return PgResponse(
            success = true,
            pgTransactionId = pgTransactionId,
            message = "매입 완료",
        )
    }

    override fun cancel(pgTransactionId: String, amount: BigDecimal): PgResponse {
        return PgResponse(
            success = true,
            pgTransactionId = pgTransactionId,
            message = "취소 완료",
        )
    }
}
