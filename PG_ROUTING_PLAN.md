# PG 라우팅 구현 플랜

## Context
PG가 하나뿐이면 장애 시 결제가 완전히 불가능하다.
PG 라우팅을 적용하여 primary PG 장애 시 fallback PG로 자동 전환하여 결제 가용성을 높인다.
Hyperswitch의 Priority-based 라우팅 + Pre-determined 패턴을 참고한다.

## 현재 구조
```
PaymentTransactionService → CircuitBreakerPgConnector(@Primary) → MockPgConnector
```

## 목표 구조
```
PaymentTransactionService → PgRouter(@Primary)
                              ├→ CircuitBreakerPgConnector("pg-mock-a") → MockPgConnectorA
                              └→ CircuitBreakerPgConnector("pg-mock-b") → MockPgConnectorB
```

- approve: 우선순위 순서대로 시도, 실패 시 다음 PG로 fallback (Hyperswitch Retryable 패턴)
- capture/cancel: 승인한 PG로만 요청 (Hyperswitch Pre-determined 패턴)

## 변경 파일 및 작업 내용

### 1. PgResponse에 providerName 추가 + ErrorCode 추가
**파일**: `src/main/kotlin/com/payments/pg/connector/PgConnector.kt`
- `PgResponse`에 `providerName: String? = null` 필드 추가
- approve 시 어떤 PG가 처리했는지 알아야 capture/cancel에서 해당 PG를 지정할 수 있음

**파일**: `src/main/kotlin/com/payments/common/exception/ErrorCode.kt`
- `ALL_PG_UNAVAILABLE(503, "모든 PG가 이용 불가 상태입니다")` 추가
- `PG_PROVIDER_NOT_FOUND(500, "지정된 PG 프로바이더를 찾을 수 없습니다")` 추가

### 2. MockPgConnectorA, MockPgConnectorB 생성
**파일 (신규)**: `src/main/kotlin/com/payments/pg/mock/MockPgConnectorA.kt`
**파일 (신규)**: `src/main/kotlin/com/payments/pg/mock/MockPgConnectorB.kt`
- 기존 `MockPgConnector`를 기반으로 A/B 두 개 생성
- providerName: "MOCK_PG_A", "MOCK_PG_B"
- pgTransactionId prefix: "mock-a-", "mock-b-"
- `@Component("mockPgA")`, `@Component("mockPgB")`로 빈 이름 지정

**파일 (삭제)**: `src/main/kotlin/com/payments/pg/mock/MockPgConnector.kt`
- A/B로 분리 후 기존 파일 삭제

### 3. CircuitBreakerPgConnector 리팩토링
**파일**: `src/main/kotlin/com/payments/pg/connector/CircuitBreakerPgConnector.kt`
- `@Component`, `@Primary` 제거 → PgConfig에서 수동 빈 등록
- 생성자: `MockPgConnector` 구체 타입 → `PgConnector` 인터페이스로 변경
- 생성자: `CircuitBreakerRegistry` → `CircuitBreaker` 인스턴스를 직접 주입받도록 변경
- 순수 데코레이터로 변경 (빈 등록은 Configuration에서 담당)

### 4. PgConfig 생성
**파일 (신규)**: `src/main/kotlin/com/payments/pg/config/PgConfig.kt`
- `@Configuration` 클래스
- CircuitBreakerPgConnector 빈 2개 수동 등록 (mockPgA + pg-mock-a 서킷, mockPgB + pg-mock-b 서킷)
- `@Qualifier`로 각 Mock PG를 주입

### 5. application.yml 서킷 브레이커 설정 변경
**파일**: `src/main/resources/application.yml`
- 기존 `pg-connector` 단일 인스턴스 → `pg-default` 공통 설정 템플릿으로 변경
- `pg-mock-a`, `pg-mock-b` 인스턴스가 `base-config: pg-default`로 상속
- PG별 독립적인 서킷 브레이커 동작

### 6. PgRouter 구현
**파일 (신규)**: `src/main/kotlin/com/payments/pg/router/PgRouter.kt`
- `PgConnector` 구현 + `@Component` + `@Primary`
- `List<CircuitBreakerPgConnector>`를 주입받아 우선순위 순서대로 시도
- approve: fallback 가능 (executeWithFallback)
- capture/cancel: `getConnector(providerName)`으로 특정 PG 지정, fallback 없음
- 모든 PG 실패 시 `ALL_PG_UNAVAILABLE` 에러

### 7. PaymentTransactionService 수정
**파일**: `src/main/kotlin/com/payments/payment/service/PaymentTransactionService.kt`
- `PgConnector` → `PgRouter` 타입으로 주입 변경
- approve: `pgRouter.approve()` 사용 (라우팅됨), `pgResponse.providerName`으로 pgProvider 저장
- capture/cancel: `pgRouter.getConnector(payment.pgProvider!!)`로 특정 PG 지정

### 8. 테스트 수정 및 추가
**기존 테스트 수정**:
- `CircuitBreakerPgConnectorTest`: 생성자 변경에 맞춰 수정
- `PaymentServiceTest`: pgProvider 값 "MOCK_PG" → "MOCK_PG_A" 변경
- `AmountVerificationTest`: `@MockitoBean PgConnector` → `@MockitoBean PgRouter` 변경

**신규 테스트 (신규)**: `src/test/kotlin/com/payments/pg/router/PgRouterTest.kt`
- 정상 호출 시 primary PG로 라우팅
- primary PG 서킷 OPEN 시 fallback PG로 라우팅
- 모든 PG 서킷 OPEN 시 ALL_PG_UNAVAILABLE 에러
- approve 시 응답에 providerName 포함
- capture/cancel 시 지정된 PG로만 요청
- 존재하지 않는 PG 조회 시 PG_PROVIDER_NOT_FOUND

### 9. README 업데이트
**파일**: `README.md`
- 아키텍처 섹션에 PG 라우팅 설명 추가
- 로드맵 Phase 2에서 PG 라우팅 체크

## 구현 순서
```
Step 1~2: 데이터 클래스/ErrorCode 변경 + Mock PG 생성
Step 3~5: CircuitBreakerPgConnector 리팩토링 + PgConfig + yml 변경 (동시 진행)
Step 6: PgRouter 구현
Step 7: PaymentTransactionService 수정
Step 8: 기존 MockPgConnector 삭제
Step 9: 테스트 수정 및 추가
Step 10: README 업데이트
```

## 검증
- `./gradlew clean test` 전체 테스트 통과 확인
