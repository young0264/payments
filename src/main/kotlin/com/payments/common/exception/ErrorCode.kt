package com.payments.common.exception

enum class ErrorCode(val status: Int, val message: String) {
    PAYMENT_NOT_FOUND(404, "결제 정보를 찾을 수 없습니다"),
    DUPLICATE_ORDER(409, "이미 존재하는 주문입니다"),
    INVALID_PAYMENT_STATUS(400, "결제 상태가 올바르지 않습니다"),
    PG_APPROVE_FAILED(502, "PG 승인에 실패했습니다"),
    PG_CAPTURE_FAILED(502, "PG 매입에 실패했습니다"),
    PG_CANCEL_FAILED(502, "PG 취소에 실패했습니다"),
    IDEMPOTENCY_CONFLICT(409, "동일한 멱등성 키로 다른 요청이 처리 중입니다"),
}
