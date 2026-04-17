package com.payments.payment.service

import com.payments.payment.dto.PaymentCapturedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, PaymentCapturedEvent>
) {
    fun publishCaptured(event: PaymentCapturedEvent) {
        kafkaTemplate.send("payment.captured", event.orderId, event)
    }
}
