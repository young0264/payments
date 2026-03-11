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
- [ ] 매입(CAPTURED) 처리
- [ ] 금액 위변조 검증

### Phase 2 — 내결함성
- [ ] Circuit Breaker
- [ ] PG 라우팅 (장애 시 다른 PG로 전환)
- [ ] 재시도 전략

### Phase 3 — 원장/대사
- [ ] 복식부기 원장
- [ ] 대사 (Reconciliation)

### Phase 4 — 정산/빌링
- [ ] Kafka 기반 정산 배치
- [ ] 가맹점 정산금 계산


## 참고

### 오픈소스
| 프로젝트 | 스택 | 참고 영역 |
|----------|------|-----------|
| [Hyperswitch](https://github.com/juspay/hyperswitch) | Rust | PG 커넥터 추상화, 상태머신, PG 라우팅, 대사 |
| [Kill Bill](https://github.com/killbill/killbill) | Java | 구독 빌링/결제 |
| [Blnk](https://github.com/blnkfinance/blnk) | Go | 복식부기 원장, 자동 대사 |
| [samchon/payments](https://github.com/samchon/payments) | TypeScript | PG 통합 연동, 웹훅, 빌링 |

### 문서
- [토스페이먼츠 API 문서](https://toss.im/payments/docs)
- [토스페이먼츠 멱등성 문서](https://docs.tosspayments.com/blog/what-is-idempotency)
- [토스페이먼츠 승인/매입 문서](https://www.tosspayments.com/blog/articles/33907)