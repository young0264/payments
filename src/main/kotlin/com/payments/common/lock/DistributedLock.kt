package com.payments.common.lock

import com.payments.common.exception.ErrorCode
import com.payments.common.exception.PaymentException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

@Component
class DistributedLock(
    private val redisTemplate: StringRedisTemplate,
) {
    fun <T> withLock(
        key: String,
        timeout: Duration = Duration.ofSeconds(5),
        action: () -> T,
    ): T {
        val lockKey = "lock:$key"
        val lockValue = UUID.randomUUID().toString()
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, timeout) ?: false

        if (!acquired) {
            throw PaymentException(ErrorCode.IDEMPOTENCY_CONFLICT)
        }

        try {
            return action()
        } finally {
            val current = redisTemplate.opsForValue().get(lockKey)
            if (current == lockValue) {
                redisTemplate.delete(lockKey)
            }
        }
    }
}
