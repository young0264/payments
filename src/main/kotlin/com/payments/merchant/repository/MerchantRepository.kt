package com.payments.merchant.repository

import com.payments.merchant.domain.Merchant
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantRepository : JpaRepository<Merchant, Long>
