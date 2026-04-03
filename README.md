# Payments

여러 PG사를 통합 연동하는 결제 오케스트레이션 시스템.
가맹점은 하나의 API만 연동하면 토스페이먼츠, KG이니시스 등 다양한 PG를 사용할 수 있다.

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Kotlin |
| Framework | Spring Boot 3.5.0 |
| DB | MySQL 8.0 |
| Cache | Redis |
| Migration | Flyway |
| Build | Gradle (Kotlin DSL) |
| JDK | 21 |
| Docs | Swagger (springdoc-openapi) |
| Resilience | Resilience4j |

## 아키텍처

```
가맹점 → Payments (결제 오케스트레이터) → PG사 → 카드사
```

```
payments/
├── payment/
│   ├── controller/    # REST API
│   ├── service/       # 결제 승인/취소 로직
│   ├── domain/        # Payment 엔티티, PaymentStatus 상태머신
│   ├── dto/           # Request/Response DTO
│   └── repository/    # JPA Repository
├── pg/
│   ├── connector/     # PG 커넥터 인터페이스 (추상화)
│   └── mock/          # 테스트용 Mock PG
└── common/
    └── exception/     # ErrorCode, 예외 처리
```

### 결제 상태 머신

```
READY → APPROVED → CAPTURED
  ↓        ↓          ↓
FAILED  CANCELED   CANCELED / PARTIAL_CANCELED
```

### 금액 위변조 검증

approve 시 가맹점이 요청한 금액과 PG가 실제 승인한 금액을 비교한다. 불일치 시 자동으로 PG 취소 후 FAILED 처리하여 잘못된 금액의 결제가 확정되는 것을 방지한다.

```
가맹점 → approve(10000원) → PG 승인(9000원) → 금액 불일치 → PG 자동 취소 → FAILED
```

### 서비스 클래스 분리 (PaymentService / PaymentTransactionService)

Spring의 `@Transactional`은 프록시 기반이라, 같은 클래스 내부에서 메서드를 호출하면 트랜잭션이 적용되지 않는다 (self-invocation 문제). 이를 해결하기 위해 분산 락 담당(`PaymentService`)과 트랜잭션 처리 담당(`PaymentTransactionService`)을 분리했다.

```
PaymentService (분산 락) → PaymentTransactionService (@Transactional)
```

### 동시성 제어 (분산 락)

같은 orderId로 동시에 요청이 들어올 때 중복 결제를 방지하기 위해 Redis 분산 락을 사용한다.

```
서버A → Redis: "lock:payment:order-1" 획득 → 결제 처리
서버B → Redis: "lock:payment:order-1" 획득 시도 → 이미 있음 → 거부
```

- **왜 Redis인가?**: 애플리케이션 레벨 락(synchronized, ReentrantLock)은 한 서버 안에서만 동작한다. 서버가 여러 대면 각 서버의 락이 독립적이라 동시 요청을 막을 수 없다. 외부 저장소(Redis)에 락을 두면 모든 서버가 같은 곳을 바라보기 때문에 서버 수와 관계없이 동시성 제어가 가능하다.
- **SETNX**: Redis의 원자적 연산. 키가 없을 때만 값을 설정하므로 동시 요청 중 하나만 성공한다.
- **TTL**: 락에 만료 시간을 설정하여 서버 장애 시 락이 영원히 안 풀리는 것을 방지한다.

### 멱등성 처리

가맹점이 네트워크 타임아웃 등으로 같은 결제를 재시도할 때 중복 결제를 방지한다.

```
1. 최초 요청: orderId="order-1", idempotencyKey="key-1" → 결제 처리
2. 재시도:    orderId="order-1", idempotencyKey="key-1" → 기존 결과 리턴 (중복 결제 X)
3. 새 시도:   orderId="order-1", idempotencyKey="key-2" → FAILED 상태면 재시도 허용
```

- `idempotencyKey`는 가맹점이 생성하여 요청에 포함. DB에 UNIQUE 제약으로 유일성 보장.
- 같은 키로 재요청 시 기존 결제를 그대로 리턴하고, 다른 키로 같은 주문 요청 시 DUPLICATE_ORDER 에러.

### Circuit Breaker (PG 장애 격리)

PG사 장애 시 타임아웃까지 대기하며 연쇄 장애가 발생하는 것을 방지한다. Resilience4j Circuit Breaker를 데코레이터 패턴으로 적용하여 기존 서비스 코드 변경 없이 PG 호출을 보호한다.

```
정상: PaymentTransactionService → CircuitBreakerPgConnector → MockPgConnector → 응답
장애: PaymentTransactionService → CircuitBreakerPgConnector → 서킷 OPEN → 즉시 503 응답
```

- 최근 10건 중 실패율 50% 초과 시 서킷 OPEN
- 30초 후 HALF_OPEN으로 전환, 3건 테스트 호출로 복구 판단
- PG 비즈니스 실패(잔액 부족 등)는 서킷에 영향 없음

### DB 락 전략 (낙관적 락 vs 비관적 락)

결제 상태 변경처럼 동시 접근 가능성이 높은 경우 비관적 락, 충돌이 드문 경우 낙관적 락을 선택한다.

| 방식 | 적용 시점 | 이유 |
|------|-----------|------|
| 비관적 락 (`SELECT FOR UPDATE`) | 결제 상태 변경 | 이벤트성 트래픽에서 충돌이 잦아 충돌 후 롤백 비용이 큼 |
| 낙관적 락 (`@Version`) | 가맹점 정보 업데이트 등 | 충돌 빈도가 낮고 재시도 비용이 적음 |

비관적 락은 조회 시점에 행에 락을 걸어, 다른 트랜잭션이 동시에 상태를 변경하지 못하게 한다.

```sql
SELECT * FROM payments WHERE order_id = ? FOR UPDATE
```

낙관적 락은 `@Version` 컬럼으로 충돌을 감지하고, 충돌 시 `OptimisticLockingFailureException`을 발생시켜 재시도하게 한다. 락을 선점하지 않으므로 읽기가 많고 충돌이 드문 상황에서 성능상 유리하다.

- **왜 결제 상태 변경에는 비관적 락인가?**: 이벤트성 트래픽에서 같은 주문에 동시 요청이 집중될 수 있다. 이 경우 낙관적 락은 충돌 후 롤백 → 재시도를 반복해 DB 부하가 오히려 커진다. 충돌이 잦을 것이 예상될 때는 처음부터 락을 선점하는 비관적 락이 적합하다.

### 비동기 분리 (PG 웹훅 처리)

PG사의 웹훅 콜백을 동기로 처리하면 PG 장애가 결제 흐름 전체를 블로킹한다. 메시지 큐(Kafka)로 비동기 분리하면 PG 장애가 결제 확정에 영향을 주지 않는다.

```
[PG 웹훅] → Kafka Topic("pg.webhook") → Consumer → 결제 상태 업데이트 → 가맹점 웹훅 발송
```

- **장애 격리**: PG 장애 시 메시지가 큐에 쌓이고, 복구 후 순서대로 처리. PG 장애가 주문 완료 응답에 영향을 주지 않는다.
- **책임 분리**: 가맹점 웹훅 발송 실패 시 Consumer에서 독립적으로 재시도. 외부 API 실패가 결제 코어 로직으로 전파되지 않는다.
- **사용자 경험**: 주문 완료 응답은 즉시 반환하고, 결제 최종 상태는 웹훅 또는 폴링으로 안내한다.

## API 스펙

### 결제 승인
```
POST /api/v1/payments/approve
```
```json
{
  "orderId": "order-001",
  "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 10000
}
```

### 결제 매입
```
POST /api/v1/payments/capture
```
```json
{
  "orderId": "order-001"
}
```

### 결제 취소
```
POST /api/v1/payments/cancel
```
```json
{
  "orderId": "order-001"
}
```

### Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

## 실행 방법

### 1. Docker로 MySQL/Redis 실행
```bash
docker compose up -d
```

### 2. 앱 실행
```bash
./gradlew bootRun
```

### 환경 정보
| 서비스 | 포트 |
|--------|------|
| Spring Boot | 8080 |
| MySQL (Docker) | 3307 |
| Redis (Docker) | 6379 |

## 로드맵

### Phase 1 — 결제 핵심 (현재)
- [x] 결제 승인/취소 API
- [x] 결제 상태 머신
- [x] 멱등성 처리 (idempotencyKey)
- [x] PG 커넥터 추상화 + Mock PG
- [x] Flyway 마이그레이션
- [x] 동시성 제어 (Redis 분산 락)
- [x] 결제 조회 API
- [x] 매입(CAPTURED) 처리
- [x] 금액 위변조 검증 (PG 승인 금액 불일치 시 자동 취소)

### Phase 2 — 내결함성
- [x] Circuit Breaker
- [ ] PG 라우팅 (장애 시 다른 PG로 전환)
- [ ] 재시도 전략
- [ ] 부분 취소

### Phase 3 — 원장/대사
- [ ] 복식부기 원장
- [ ] 대사 (Reconciliation)

### Phase 4 — 정산/빌링
- [ ] Kafka 기반 정산 배치
- [ ] 가맹점 정산금 계산

### Phase 5 — 운영
- [ ] 모니터링 (메트릭 수집, 알림)
- [ ] 웹훅 (PG → 가맹점 비동기 알림)
- [ ] 인증/인가 (가맹점 API Key)
- [ ] 테스트는 Testcontainers로 변경


## 참고

### 오픈소스
| 프로젝트 | 스택 | 참고 영역 |
|----------|------|-----------|
| [Hyperswitch](https://github.com/juspay/hyperswitch) | Rust | PG 커넥터 추상화, 상태머신, PG 라우팅, 대사 |
| [Kill Bill](https://github.com/killbill/killbill) | Java | 구독 빌링/결제 |
| [Blnk](https://github.com/blnkfinance/blnk) | Go | 복식부기 원장, 자동 대사 |
| [samchon/payments](https://github.com/samchon/payments) | TypeScript | PG 통합 연동, 웹훅, 빌링 |

### 문서
- [토스페이먼츠 블로그(용어사전/가이드/샌드박스)](https://docs.tosspayments.com/blog)
- [토스페이먼츠 멱등성 문서](https://docs.tosspayments.com/blog/what-is-idempotency)
- [토스페이먼츠 승인/매입 문서](https://www.tosspayments.com/blog/articles/33907)