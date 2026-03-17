package com.payments.payment.repository

import com.payments.payment.domain.PaymentCancelHistory
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentCancelHistoryRepository : JpaRepository<PaymentCancelHistory, Long>
