package com.payments.common.exception

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(PaymentException::class)
    fun handlePaymentException(e: PaymentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.name, e.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception: {}", e.message, e)
        return ResponseEntity
            .status(500)
            .body(ErrorResponse("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다"))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
)
