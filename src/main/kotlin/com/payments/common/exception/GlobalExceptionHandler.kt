package com.payments.common.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(PaymentException::class)
    fun handlePaymentException(e: PaymentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.name, e.message))
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        return ResponseEntity
            .status(status)
            .headers(headers)
            .body(ErrorResponse("INTERNAL_ERROR", ex.message ?: "알 수 없는 오류가 발생했습니다"))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .status(status)
            .body(ErrorResponse("VALIDATION_ERROR", message))
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
