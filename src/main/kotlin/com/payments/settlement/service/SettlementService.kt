package com.payments.settlement.service

import com.payments.settlement.dto.SettlementResponse
import com.payments.settlement.repository.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementService(
    private val settlementRepository: SettlementRepository,
) {
    @Transactional(readOnly = true)
    fun getByMerchantId(merchantId: Long): List<SettlementResponse> {
        return settlementRepository.findAllByMerchantId(merchantId)
            .map { SettlementResponse.from(it) }
    }
}
