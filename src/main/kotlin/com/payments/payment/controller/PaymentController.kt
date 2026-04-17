package com.payments.payment.controller

import com.payments.payment.dto.ApproveRequest
import com.payments.payment.dto.CancelRequest
import com.payments.payment.dto.CaptureRequest
import com.payments.payment.dto.PaymentResponse
import com.payments.payment.service.PaymentService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {

    @PostMapping("/approve")
    fun approve(@Valid @RequestBody request: ApproveRequest): ResponseEntity<PaymentResponse> {
        val payment = paymentService.approve(
            orderId = request.orderId,
            idempotencyKey = request.idempotencyKey,
            amount = request.amount,
            merchantId = request.merchantId,
        )
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }

    @GetMapping("/{orderId}")
    fun getPayment(@PathVariable orderId: String): ResponseEntity<PaymentResponse> {
        val payment = paymentService.getByOrderId(orderId)
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }

    @PostMapping("/capture")
    fun capture(@Valid @RequestBody request: CaptureRequest): ResponseEntity<PaymentResponse> {
        val payment = paymentService.capture(orderId = request.orderId)
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }

    @PostMapping("/cancel")
    fun cancel(@Valid @RequestBody request: CancelRequest): ResponseEntity<PaymentResponse> {
        val payment = paymentService.cancel(
            orderId = request.orderId,
            cancelAmount = request.cancelAmount,
            cancelReason = request.cancelReason,
        )
        return ResponseEntity.ok(PaymentResponse.from(payment))
    }
}
