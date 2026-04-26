package com.payments.reconciliation.controller

import com.payments.reconciliation.dto.ReconciliationResultResponse
import com.payments.reconciliation.service.ReconciliationService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/reconciliations")
class ReconciliationController(
    private val reconciliationService: ReconciliationService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun reconcile(@RequestParam file: MultipartFile): ResponseEntity<List<ReconciliationResultResponse>> {
        val results = reconciliationService.reconcile(file)
        return ResponseEntity.ok(results.map { ReconciliationResultResponse.from(it) })
    }
}
