package com.payments.settlement.service

import com.payments.settlement.repository.SettlementRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementBatch(
    private val settlementRepository: SettlementRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // TODO: 실제 PG 정산 API 비동기 호출 + 웹훅 수신 후 COMPLETED 변경으로 전환
    @Transactional
    @Scheduled(cron = "0 0 0 * * *")
    fun settle() {
        val count = settlementRepository.completeAllPending()
        logger.info("[batch] 정산 완료 처리: ${count}건")
    }
}
