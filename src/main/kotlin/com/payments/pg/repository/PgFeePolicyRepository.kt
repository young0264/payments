package com.payments.pg.repository

import com.payments.pg.domain.PgFeePolicy
import org.springframework.data.jpa.repository.JpaRepository

interface PgFeePolicyRepository : JpaRepository<PgFeePolicy, Long> {
    fun findByPgProvider(pgProvider: String): PgFeePolicy?
}
