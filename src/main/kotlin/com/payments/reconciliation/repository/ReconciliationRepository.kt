package com.payments.reconciliation.repository

import com.payments.reconciliation.domain.ReconciliationResult
import org.springframework.data.jpa.repository.JpaRepository

interface ReconciliationRepository : JpaRepository<ReconciliationResult, Long>
