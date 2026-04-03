# Payments 프로젝트 — Claude 설정

## 프로젝트 목적

**Payment 시스템 심층 학습**이 핵심 목적.

1. 회사에서 사용하는 결제 시스템을 이해하고 재현
2. 재현한 기능을 더 고도화하면서 payment 도메인 지식을 쌓음
3. 단순 클론이 아니라, 각 설계 결정(분산 락, 원장, 대사 등)의 이유를 직접 구현하며 체득

회사 코드와 이 프로젝트를 함께 참고하면서 작업하는 경우가 많음. (경로는 `.claude/CLAUDE.local.md`)

## 회사 코드 구조

회사 시스템은 **부동산 관리(Property Management) + 임대 결제 처리** 시스템.
결제 관련 핵심 모듈 경로는 `.claude/CLAUDE.local.md` 참고.

### 회사 결제 흐름

```
청구서(Invoice) 생성 → 결제 방법 지정 (GMO/은행이체/현금)
    → 입금(Transaction) 발생 → 원장(Ledger) 기록 → 청구서 수납(settle)
    → 대사(Reconciliation): GMO/SMBC CSV 업로드로 청구서-입금 매칭
```

### 회사 핵심 도메인 개념

- **Invoice (청구서)**: 임대료, 청소비 등 청구 항목. 상태: `DRAFT → YET → PAID`
  - `ClaimType`: RENT, DEPOSIT, CLEANING 등
  - `ClaimStatus`: DRAFT, YET, OVERDUE, PARTIAL, PAID
  - 연체료(`lateFee`) 계산 내장
  - GMO 결제 URL 생성 (`GmoPayment`)

- **Transaction (입출금)**: 실제 돈이 들어오고 나간 기록
  - `TransactionSource`: GMO, SMBC(은행), CASH, MANUAL
  - `TransactionType`: DEPOSIT(입금), WITHDRAWAL(출금)
  - `balance` 필드로 잔액 추적

- **Ledger (원장)**: 복식부기. 모든 금융 이벤트를 `LedgerEvent`로 기록
  - 이벤트: `CLAIM_CREATED`, `CLAIM_SETTLED`, `LEASE_ACCOUNT_TRANSFER_IN` 등
  - 취소 이벤트 쌍으로 관리 (`CLAIM_SETTLED` ↔ `CANCEL_CLAIM_SETTLE`)
  - `groupId`로 관련 원장 항목 묶음
  - `isCancellation` / `hasBeenCancelled` 플래그로 취소 이력 관리

- **Reconciliation (대사)**: PG/은행 CSV 업로드 → 청구서와 자동/수동 매칭
  - GMO CSV, SMBC CSV 파싱
  - `BulkReconciliationLog`로 대사 이력 관리

## 기술 스택

- **언어**: Kotlin (JDK 21)
- **프레임워크**: Spring Boot 3.5.0
- **DB**: MySQL 8.0 (Docker, 포트 3307)
- **캐시/락**: Redis (포트 6379)
- **마이그레이션**: Flyway
- **빌드**: Gradle Kotlin DSL
- **문서**: Swagger (springdoc-openapi)
- **내결함성**: Resilience4j

## 로컬 실행

```bash
docker compose up -d   # MySQL + Redis
./gradlew bootRun
./gradlew test
```

## 디렉토리 구조

```
src/main/kotlin/com/payments/
├── payment/
│   ├── controller/    # REST API
│   ├── service/       # PaymentService (분산락), PaymentTransactionService (@Transactional)
│   ├── domain/        # Payment 엔티티, PaymentStatus 상태머신
│   ├── dto/           # Request/Response DTO
│   └── repository/    # JPA Repository
├── pg/
│   ├── connector/     # PgConnector 인터페이스
│   └── mock/          # MockPgConnector (테스트용)
└── common/
    └── exception/     # ErrorCode, GlobalExceptionHandler
```

## 결제 상태 머신

```
READY → APPROVED → CAPTURED
  ↓        ↓          ↓
FAILED  CANCELED   CANCELED / PARTIAL_CANCELED
```

## 핵심 코드 패턴

### 동시성 제어
```
분산 락 (PaymentService) → @Transactional (PaymentTransactionService)
```
락이 트랜잭션을 감싸는 구조. self-invocation 문제 회피를 위해 두 클래스로 분리.

### PG 연동
`PgConnector` 인터페이스 + 구현체. CircuitBreaker는 데코레이터로 래핑.

## 코딩 컨벤션

- DTO: Request/Response 분리, validation은 `@field:NotBlank` 등 사용
- 에러: `ErrorCode` enum으로 관리
- DB 마이그레이션: `src/main/resources/db/migration/` Flyway SQL 추가
- 테스트: Mockito-Kotlin 사용

## 주의사항

- MySQL 포트 3307 (로컬 3306 충돌 방지)
- 분산 락 추가 시 반드시 PaymentService(락) / PaymentTransactionService(트랜잭션) 분리 유지
- 새 기능은 회사 코드의 설계를 먼저 이해한 뒤 재현/고도화 순서로 진행
