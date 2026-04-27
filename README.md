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

### Redis 운영 시 주의사항

#### 메모리 정책 (maxmemory-policy)

Redis 메모리가 가득 차면 `maxmemory-policy` 설정에 따라 동작이 달라진다.

| 정책 | 동작 |
|------|------|
| `noeviction` | 쓰기 요청 거부 (기본값) |
| `allkeys-lru` | 전체 키 중 LRU로 제거 |
| `volatile-lru` | TTL 있는 키 중 LRU로 제거 |
| `allkeys-lfu` | 전체 키 중 사용 빈도 낮은 것 제거 |
| `volatile-ttl` | TTL 짧은 것부터 제거 |

**분산 락 관점에서 주의할 점**: 락 키는 TTL이 있으므로 `volatile-lru` 정책에서는 메모리 부족 시 락 키가 강제 제거될 수 있다. 락이 풀린 것처럼 동작해 중복 결제 위험이 생긴다.
→ 락 용도 Redis는 `noeviction` 정책 + 메모리 사용량 알림 설정이 안전하다.

#### 캐시 Redis와 락 Redis 분리

캐시와 락을 같은 Redis 인스턴스에서 운영하면 캐시 데이터가 메모리를 가득 채워 락 키가 영향받을 수 있다. 운영 환경에서는 용도별로 Redis 인스턴스를 분리하는 것이 모범 사례다.

#### 락 해제의 원자성

현재 구현은 `GET` → `DELETE` 두 단계로 락을 해제한다. 두 연산 사이에 TTL이 만료되고 다른 서버가 락을 획득하면 엉뚱한 락을 해제하는 문제가 생길 수 있다. Lua 스크립트로 원자적으로 처리해야 한다.

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
end
```

#### 고가용성

Redis 단일 장애점 → Sentinel(자동 failover) 또는 Cluster 구성 필요. Sentinel failover 과정에서 락이 유실될 수 있으므로 DB 레벨의 안전장치(unique 제약)와 함께 사용해야 한다.

#### TTL 설정과 Watchdog

락 TTL이 너무 짧으면 작업 완료 전에 락이 만료되어 다른 서버가 락을 획득할 수 있다. 반대로 너무 길면 장애 시 락이 오래 유지된다.

- **고정 TTL 방식**: 현재 구현. 작업 시간이 TTL을 초과하면 중복 처리 위험.
- **Watchdog 방식** (Redisson 등): 락을 보유한 스레드가 살아있는 동안 주기적으로 TTL을 연장. 작업이 끝나면 명시적으로 해제. 장애 시에는 TTL이 만료되어 자동 해제.

#### 재시도 전략

락 획득 실패 시 바로 에러를 반환할지, 일정 횟수 재시도할지 결정해야 한다.

| 전략 | 동작 | 적합한 상황 |
|------|------|-------------|
| 즉시 거부 | 락 없으면 바로 실패 | 사용자 직접 요청 (빠른 응답 필요) |
| 스핀 락 | 짧은 간격으로 반복 시도 | 락 보유 시간이 매우 짧은 경우 |
| 지수 백오프 | 재시도 간격을 점진적으로 늘림 | 배치, 백그라운드 작업 |

현재 구현은 즉시 거부 방식. 결제는 사용자가 직접 요청하는 시나리오이므로 적합하다.

#### Redlock 알고리즘

단일 Redis 노드 장애 또는 Sentinel failover 중 동일 락이 두 클라이언트에 동시 부여될 수 있다. Redis 창시자 Salvatore Sanfilippo가 제안한 Redlock은 N개(보통 5개)의 독립 Redis 인스턴스 중 과반수(3개 이상)에서 락을 획득해야 유효한 락으로 인정한다.

```
클라이언트 → Redis-1, 2, 3, 4, 5에 동시 락 요청
3개 이상 성공 + 총 소요 시간 < TTL → 락 획득 성공
```

다만 NTP 시계 동기화 오차, 프로세스 GC pause 등으로 완전한 안전성을 보장하지 않는다는 반론(Martin Kleppmann)도 있다. 중요한 결제 시스템에서는 DB unique 제약과 함께 이중 안전장치로 사용하는 것이 현실적이다.

#### 모니터링

Redis 분산 락 운영 시 추적해야 할 지표:

| 지표 | 확인 방법 | 임계값 예시 |
|------|-----------|-------------|
| 메모리 사용률 | `INFO memory` → `used_memory_rss` | 70% 초과 시 알림 |
| 락 획득 실패율 | 애플리케이션 메트릭 | 1% 초과 시 알림 |
| 연결 수 | `INFO clients` → `connected_clients` | 최대 연결 수 80% 초과 시 |
| 명령어 지연 | `INFO stats` → `instantaneous_ops_per_sec` | latency spike 감지 |
| TTL 없는 키 | `INFO keyspace` | 락 키에 TTL 누락 감지 |

```bash
# Redis 메모리 사용량 확인
redis-cli INFO memory | grep used_memory_human

# TTL 없는 키 확인 (운영 환경에서는 SCAN 사용)
redis-cli --scan --pattern "lock:*" | xargs -I{} redis-cli TTL {}
```

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

### 인증/인가 (가맹점 API Key)

가맹점별 고유한 API Key를 발급하여 요청 주체를 식별한다. 토스페이먼츠도 동일한 방식을 사용한다([참고](https://toss.tech/article/payments-legacy-4)).

```
가맹점 → X-API-KEY: {apiKey} 헤더 포함 → API 서버 → DB에서 가맹점 조회 → 인증 성공/실패
```

- API Key는 가맹점 생성 시 발급, DB에 UNIQUE 제약으로 유일성 보장
- 유효하지 않은 Key → 401 Unauthorized 반환
- 인증 성공 시 가맹점 정보를 요청 컨텍스트에 주입

**추가 보안 계층** (토스페이먼츠 참고):
- TLS 1.3 — 통신 채널 암호화
- Rate Limiting — 악의적 사용 방지
- 웹훅 서명 검증 (`webhook-signature`) — PG → 가맹점 웹훅의 위변조 방지

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
- [x] PG 라우팅 (장애 시 다른 PG로 전환)
- [x] 재시도 전략
- [x] 부분 취소

### Phase 3 — 원장/대사
- [x] 복식부기 원장
- [x] 대사 (Reconciliation)

### Phase 4 — 정산/빌링
- [x] Settlement 엔티티 + 마이그레이션
- [x] PaymentCapturedEvent DTO (JSON 직렬화)
- [x] SettlementConsumer — PgFeePolicy 조회 → Settlement 생성
- [x] SettlementBatch — 매일 자정 PENDING → COMPLETED 처리
- [x] 가맹점 정산금 조회 API

### Phase 5 — 운영
- [ ] 모니터링 (Micrometer + Prometheus + Grafana 대시보드)
- [ ] Redis 락 획득 실패율 메트릭 수집
- [ ] Redis 메모리 사용량 알림 + TTL 누락 키 감지
- [ ] 웹훅 (PG → 가맹점 비동기 알림)
- [ ] 인증/인가 (가맹점 API Key)
- [ ] 테스트는 Testcontainers로 변경

### Phase 6 — 개선/고도화
- [ ] ShedLock (Redis 기반) — 다중 인스턴스 환경에서 배치 중복 실행 방지
- [ ] Lua 스크립트로 락 해제 원자성 보장
- [ ] Watchdog 방식 TTL 자동 연장
- [ ] Redlock 알고리즘 — 다중 Redis 노드 기반 락 안전성
- [ ] Redis 캐시/락 인스턴스 분리

### Phase 7 — SDK/MSA
- [ ] 가맹점용 SDK (Kotlin/Java 클라이언트 라이브러리)
- [ ] MSA 분리 (결제/정산/대사 서비스 분리)


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
- [토스페이먼츠 인증/인가 (API Key)](https://toss.tech/article/payments-legacy-4)
- [토스페이먼츠 웹훅 헤더 스펙](https://docs.tosspayments.com/reference/using-api/webhook-events#%EC%9B%B9%ED%9B%85-%ED%97%A4%EB%8D%94)
- [토스페이먼츠 JS SDK](https://docs.tosspayments.com/sdk/v2/js)