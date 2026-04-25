package com.payments.payment.service

import com.payments.payment.dto.PaymentCapturedEvent
import com.payments.pg.repository.PgFeePolicyRepository
import com.payments.settlement.domain.Settlement
import com.payments.settlement.repository.SettlementRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode

@Service
class SettlementConsumer(
    private val pgFeePolicyRepository: PgFeePolicyRepository,
    private val settlementRepository: SettlementRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    @KafkaListener(topics = ["payment.captured"], groupId = "settlement-group")
    fun consume(event: PaymentCapturedEvent) {
        if (settlementRepository.existsByOrderId(event.orderId)) {
            logger.warn("[settlement] 중복 이벤트 무시: orderId=${event.orderId}")
            return
        }

        val feePolicy = pgFeePolicyRepository.findByPgProvider(event.pgProvider)
            ?: run {
                logger.error("[settlement] PG 수수료 정책 없음: pgProvider=${event.pgProvider}")
                return
            }

        val feeAmount = event.amount.multiply(feePolicy.feeRate).setScale(2, RoundingMode.HALF_UP)
        val netAmount = event.amount.subtract(feeAmount)

        settlementRepository.save(
            Settlement(
                orderId = event.orderId,
                merchantId = event.merchantId,
                pgProvider = event.pgProvider,
                amount = event.amount,
                feeAmount = feeAmount,
                netAmount = netAmount,
            )
        )

        logger.info("[settlement] 정산 생성: orderId=${event.orderId}, amount=${event.amount}, fee=${feeAmount}, net=${netAmount}")
    }
}
