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
VAN ── 카드번호 앞 6자리(BIN)로 발급 카드사 판별
   │
   ├─ 411111·555555·… ──HTTP──▶ 에이카드 게이트웨이 :9000
   └─ 622222          ──HTTP──▶ 비카드 게이트웨이 :9100
```

VAN은 **카드사 내부에 서비스가 몇 개인지 알지 못한다.** 카드사마다 게이트웨이 하나씩만 알고 있어서, 카드사가 내부 구조를 바꿔도 영향받지 않는다.

---

## 🌐 API Endpoints

| Method | URI | Description |
|---|---|---|
| `POST` | `/api/van/payments` | 결제 승인 요청 수신 (HTTP 경로) |
| TCP | `:7777` | POS의 ISO 8583 전문 수신 (MTI 0200 → 0210 응답) |
| `GET` | `/` | 중계 모니터 화면 |

---

## ✨ 주요 기능

### 1. BIN 기반 카드사 라우팅

VAN의 존재 이유는 "가맹점은 VAN 한 곳만 연동하면 모든 카드사에 닿는다"는 것이다. 그러려면 **카드번호로 어느 카드사가 발급한 카드인지 판별**해야 한다. 카드번호 앞 6자리(BIN, Bank Identification Number)가 그 정보를 담고 있다.

라우팅 테이블은 코드가 아니라 설정에 둔다. 카드사 추가·BIN 대역 변경은 일상적인 일이라 그때마다 배포할 수 없기 때문이다.

```yaml
van:
  routing:
    issuers:
      - code: A_CARD
        name: 에이카드
        url: ${CARD_ISSUER_URL:http://localhost:9000}
        enabled: true
        bins: ["411111", "555555", "601111", "353011", "510510", "401288"]

      - code: B_CARD
        name: 비카드
        url: http://localhost:9100
        enabled: true
        bins: ["622222"]
```

**가장 긴 BIN이 우선한다.** BIN은 6자리가 기본이지만 발급량이 늘면 8자리로 세분화되어, `411111` 대역 안의 `41111199`만 다른 카드사로 갈라지는 일이 실제로 생긴다. 등록 순서에 결과가 좌우되면 안 되므로 최장 일치로 정한다.

```java
private Optional<CardIssuer> findByBin(String cardNum) {
    return properties.getIssuers().stream()
            .filter(CardIssuer::isEnabled)
            .flatMap(issuer -> issuer.getBins().stream()
                    .filter(cardNum::startsWith)
                    .map(bin -> new Match(bin.length(), issuer)))
            .max(Comparator.comparingInt(Match::binLength))
            .map(Match::issuer);
}
```

`enabled: false`는 연동 중단(계약 종료·점검)을 뜻한다. 설정에서 지우지 않고 끄기만 하는 이유는, 지워버리면 그 BIN이 "미등록"과 구분되지 않아 장애 원인을 추적할 수 없기 때문이다.

### 2. 보낼 카드사가 없으면 중계하지 않고 자체 거절 (15)

```java
CardIssuer issuer = cardIssuerRouter.route(request.getCardNum());  // 실패 시 UnsupportedIssuerException
```

미등록 BIN을 아무 카드사에나 보내면 **다른 카드사에 남의 거래 전문이 도달한다.** 보낼 곳을 못 찾으면 중계 자체를 하지 않는다.

응답코드는 시스템 오류(`96`)가 아니라 ISO 8583의 "No such issuer"에 해당하는 `15`를 쓴다. VAN이 고장난 게 아니라 연동 대상이 아니라는 뜻이어서, 가맹점이 원인을 구분할 수 있어야 한다.

거절 사유 메시지에는 카드번호를 그대로 남기지 않고 BIN 6자리만 남긴다(`미등록 BIN: 777777**********`). 라우팅 테이블 누락을 판단하는 데 필요한 건 BIN뿐이고, 전체 카드번호는 로그에 남으면 안 되는 정보다.

### 3. 카드사 주소를 호출 시점에 정한다

Feign은 보통 `url`을 고정해 쓰지만, VAN은 여러 카드사와 연동하므로 대상이 요청마다 달라진다. 첫 파라미터가 `URI`면 Feign이 그 호스트를 대상으로 삼는 성질을 이용했다.

```java
@FeignClient(name = "card-issuer", url = "http://unused")   // 실제 주소는 호출 시점에 주입된다
public interface CardIssuerClient {

    @PostMapping("/api/card/payments/process")
    FdsInspectResponse requestApproval(URI issuerUrl, @RequestBody FdsInspectRequest request);
}
```

```java
cardIssuerClient.requestApproval(URI.create(issuer.getUrl()), approvalRequest);
```

"카드사 수만큼 Feign 인터페이스를 만든다"는 선택지도 있었지만, 그러면 카드사 추가가 곧 코드 추가가 되어 설정으로 뺀 의미가 사라진다.

### 4. 자체 유효성 검증

VAN은 중계자이므로 결제 판단은 하지 않는다. 형식적으로 잘못된 요청만 거절한다.

```java
// VAN 자체 입력 검증 (VAN은 중계자라 결제 판단은 안 하고, 명백히 잘못된 요청만 거절)
if (request.getAmount() == null || request.getAmount() <= 0) {
    throw new InvalidRequestException("결제 금액은 0보다 커야 합니다");
}
```

예외를 던지면 `GlobalExceptionHandler`가 응답을 조립한다. 컨트롤러에서 에러 응답을 직접 만들지 않으므로 중계 로직만 남는다.

### 5. 멱등키 조합 (STAN)

POS가 전문에 실어 보낸 **STAN(System Trace Audit Number, ISO 8583 field 11)**을 읽어 멱등키를 만든다. STAN은 6자리라 단말 하나 안에서만 유일하고 순환·재사용되므로, 가맹점ID와 조합해 전역 유일성을 확보한다.

```java
String stan = isoReq.getObjectValue(11);
String idempotencyKey = merchantId + "-" + stan;
```

재시도가 와도 같은 값이라 카드사가 중복 결제를 걸러낼 수 있다.

### 6. 응답 relay — 카드사 코드를 보존한다

카드사의 비즈니스 거절은 HTTP 200 + 응답코드로 오므로 Feign이 예외를 던지지 않고 그대로 relay된다. 카드사가 진짜로 죽었을 때만 `FeignException`이 발생해 시스템 실패로 전파된다.

```java
FdsInspectResponse approvalResponse;
try {
    approvalResponse = cardIssuerClient.requestApproval(URI.create(issuer.getUrl()), approvalRequest);
} catch (FeignException e) {
    throw new DownstreamCallFailedException(request.getAmount(), e);
}
```

> 응답코드는 경계를 넘을 때마다 유실되기 쉽다. 컨트롤러의 `else` 분기가 `51`을 하드코딩하거나 TCP 게이트웨이가 `isSuccess ? "00" : "51"`로 덮어쓰면, 1회 한도 초과(`61`)나 중복 거래(`94`)가 전부 `51`로 뭉개져 POS에 도달한다. 각 변환 지점에서 카드사 코드를 그대로 보존하도록 했다.

### 7. Feign 타임아웃

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
| `15` | 연동 카드사 없음 (VAN이 라우팅 단계에서 자체 거절) |
| `51` | 잔액 부족 / 신용 한도 초과 |
| `61` | 1회 결제 한도 초과 |
| `94` | 중복 거래 의심 |
| `96` | 시스템 오류 (VAN 자체 검증 실패 포함) |

---

## ▶️ 실행

```bash
sh gradlew bootRun
```

주 카드사(A_CARD) 주소는 환경변수로 바꿀 수 있다. 나머지 카드사는 `application.yml`의 라우팅 테이블에서 관리한다.

```bash
export CARD_ISSUER_URL=http://localhost:9000   # 기본값
```

**중계 모니터**: http://localhost:7070 — 라우팅 테이블(카드사별 BIN 대역·연동 상태), 중계 내역, 라우팅된 카드사, TCP/HTTP 채널 구분, 조합한 멱등키, 승인율

카드사에 닿지 못한 거래(미등록 BIN·연동 끊김)도 이력에 남긴다. 성공 건만 보이면 정작 봐야 할 유입과 장애가 화면에서 사라지기 때문이다.

VAN은 거래를 저장하지 않는 중계자이므로 이 목록은 최근 50건만 메모리에 보관한다(재기동하면 사라진다).
