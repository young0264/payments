package com.payments.settlement.controller

import com.payments.settlement.dto.SettlementResponse
import com.payments.settlement.service.SettlementService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/settlements")
class SettlementController(
    private val settlementService: SettlementService,
) {
    @GetMapping("/{merchantId}")
    fun getByMerchant(@PathVariable merchantId: Long): ResponseEntity<List<SettlementResponse>> {
        return ResponseEntity.ok(settlementService.getByMerchantId(merchantId))
    }
}
