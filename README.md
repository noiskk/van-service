# 💳 VAN Service (전문 중계)

> 전체 시스템 개요·아키텍처·실행 방법 → **[card-payment-system](https://github.com/noiskk/card-payment-system)**
> 관련 저장소: [pos-client](https://github.com/noiskk/pos-client) · [card-service](https://github.com/noiskk/card-service) · [bank-service](https://github.com/noiskk/bank-service)

## 📖 개요

가맹점 단말과 카드사를 잇는 중계사(VAN)다. POS가 보낸 ISO 8583 전문을 수신해 검증하고 카드사로 라우팅한 뒤, 카드사 응답을 다시 POS 규격으로 변환해 전달한다.

**승인 판단은 하지 않는다.** 명백히 잘못된 요청만 자체 거절하고, 나머지는 카드사의 판정을 그대로 relay한다.

---

## 🔀 중계 경로

```
POS 단말 :6060
   │  ISO 8583 (MTI 0200) / TCP :7777
   ▼
VAN
   │  HTTP  POST /api/card/payments/process
   ▼
card-gateway :9000        ← 카드사가 공개한 유일한 주소
```

VAN은 **카드사 내부에 서비스가 몇 개인지 알지 못한다.** 게이트웨이 하나만 알고 있어서, 카드사가 내부 구조를 바꿔도 영향받지 않는다.

```java
/**
 * 카드사 승인 요청 클라이언트.
 * VAN은 카드사 내부 구조(FDS·승인·원장이 어떻게 나뉘어 있는지)를 알지 못하고,
 * 카드사가 공개한 게이트웨이 하나만 호출한다.
 */
@FeignClient(name = "card-issuer", url = "${card.issuer.url}")
public interface CardIssuerClient {

    @PostMapping("/api/card/payments/process")
    FdsInspectResponse requestApproval(@RequestBody FdsInspectRequest request);
}
```

## 🌐 API Endpoints

| Method | URI | Description |
|---|---|---|
| `POST` | `/api/van/payments` | 결제 승인 요청 수신 (HTTP 경로) |
| TCP | `:7777` | POS의 ISO 8583 전문 수신 (MTI 0200 → 0210 응답) |
| `GET` | `/` | 중계 모니터 화면 |

---

## 📌 팀 프로젝트 → 개인 고도화

| | 팀 프로젝트 | 개인 고도화 |
|---|---|---|
| 카드사 호출 대상 | `card-fds-service`(9090) 직접 호출 | **`card-gateway`(9000)** — 내부 구조를 알지 않게 |
| 예외 처리 | 컨트롤러 `try-catch` + Feign 에러 바디 직접 파싱 | Domain/Business/System 계층 + 전역 핸들러 |
| 멱등키 | 없음 | **STAN(field 11)을 읽어 `merchantId-STAN`으로 조합해 전파** |
| 응답코드 | `else` 분기에서 `51` 하드코딩 | 카드사 코드를 그대로 보존 (61·94·14 등) |
| 타임아웃 | 없음 (무한 대기) | Feign 연결 2s / 응답 3s |
| 화면 | 없음 | 중계 모니터 |

---

## ✨ 주요 기능

### 1. 자체 유효성 검증

VAN은 중계자이므로 결제 판단은 하지 않는다. 형식적으로 잘못된 요청만 거절한다.

```java
// VAN 자체 입력 검증 (VAN은 중계자라 결제 판단은 안 하고, 명백히 잘못된 요청만 거절)
if (request.getAmount() == null || request.getAmount() <= 0) {
    throw new InvalidRequestException("결제 금액은 0보다 커야 합니다");
}
```

예외를 던지면 `GlobalExceptionHandler`가 응답을 조립한다. 팀 프로젝트에서는 컨트롤러가 직접 `try-catch`로 에러 응답을 만들었는데, 이를 전역 핸들러로 옮겨 컨트롤러에서 예외 처리 코드를 제거했다.

### 2. 멱등키 조합 (STAN)

POS가 전문에 실어 보낸 **STAN(System Trace Audit Number, ISO 8583 field 11)**을 읽어 멱등키를 만든다. STAN은 6자리라 단말 하나 안에서만 유일하고 순환·재사용되므로, 가맹점ID와 조합해 전역 유일성을 확보한다.

```java
String stan = isoReq.getObjectValue(11);
String idempotencyKey = merchantId + "-" + stan;
```

재시도가 와도 같은 값이라 카드사가 중복 결제를 걸러낼 수 있다.

### 3. 응답 relay — 카드사 코드를 보존한다

카드사의 비즈니스 거절은 HTTP 200 + 응답코드로 오므로 Feign이 예외를 던지지 않고 그대로 relay된다. 카드사가 진짜로 죽었을 때만 `FeignException`이 발생해 시스템 실패로 전파된다.

```java
FdsInspectResponse approvalResponse;
try {
    approvalResponse = cardIssuerClient.requestApproval(approvalRequest);
} catch (FeignException e) {
    throw new DownstreamCallFailedException(request.getAmount(), e);
}
```

> 팀 프로젝트에서는 컨트롤러의 `else` 분기가 응답코드를 `51`로 하드코딩해서, 1회 한도 초과(`61`)나 중복 거래(`94`)가 전부 `51`로 뭉개져 POS에 도달했다. TCP 게이트웨이에서도 `isSuccess ? "00" : "51"`로 다시 덮어쓰고 있었다. 두 지점을 모두 고쳐 카드사 코드가 POS까지 보존되게 했다.

### 4. Feign 타임아웃

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 2000
            readTimeout: 3000
```

타임아웃이 없으면 응답 없는 카드사에 무한정 매달려 POS도 함께 멈춘다.

---

## 🔢 응답 코드

카드사가 내려준 코드를 그대로 전달한다.

| 코드 | 의미 |
|---|---|
| `00` | 승인 |
| `14` | 카드 상태 문제 |
| `51` | 잔액 부족 / 신용 한도 초과 |
| `61` | 1회 결제 한도 초과 |
| `94` | 중복 거래 의심 |
| `96` | 시스템 오류 (VAN 자체 검증 실패 포함) |

---

## ▶️ 실행

```bash
sh gradlew bootRun
```

카드사 게이트웨이 주소는 환경변수로 바꿀 수 있다.

```bash
export CARD_ISSUER_URL=http://localhost:9000   # 기본값
```

**중계 모니터**: http://localhost:7070 — 중계 내역, TCP/HTTP 채널 구분, 조합한 멱등키, 승인율

VAN은 거래를 저장하지 않는 중계자이므로 이 목록은 최근 50건만 메모리에 보관한다(재기동하면 사라진다).
