package com.payments.payment.service

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class SettlementConsumer {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @KafkaListener(topics = ["payment.captured"], groupId = "settlement-group")
    fun consume(message: String) {
        logger.info("[consumer] 정산 이벤트 수신: $message")
    }
}
