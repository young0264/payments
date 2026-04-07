package com.payments.payment.service

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PaymentEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    fun publishCaptured(orderId: String, amount: BigDecimal) {
        kafkaTemplate.send("payment.captured", orderId, "$orderId:$amount")
    }
}
