# 💳 VAN Service (결제 게이트웨이)

## 📖 개요
POS(APIDOG)의 결제 요청을 받아 Card사에 결제 승인을 요청하는 서비스 역할
<br>
VAN은 결제 요청이 유효성을 검증한다.
<br>
카드사의 응답을 POS에 전달한다.

<img width="863" height="586" alt="van-service 플로우" src="https://github.com/user-attachments/assets/30502bbe-400c-4779-9140-316a4a724d8e" />


## 🌐 API Endpoints
| Method | URI | Description |
|---|---|---|
| `POST` | `/api/van/payments` | POS 단말기로부터 결제 승인 요청 수신 및 FDS 전달 |


## ✨ 주요 기능
**1. 결제 유효성 검증**
* POS로부터 들어온 결제 요청 파라미터 검증
* 결제 요청 금액이 0원 이하인 경우 비정상 거래로 간주하여 즉시 반려 (Bad Request)
* [PaymentGatewayController.java](https://github.com/fisa-msa-project/VAN-Service/blob/main/src/main/java/com/bank/controller/PaymentGatewayController.java#L35-L45)
```java
@Operation(summary = "카드 거래 요청", description = "카드사 승인 요청을 보냅니다.")
    @PostMapping("/payments")
    public ResponseEntity<EntityModel<PaymentGatewayResponse>> requestPayment(@RequestBody PaymentGatewayRequest request) {
        log.info("카드 거래 요청 수신: cardNum={}, amount={}, merchantId={}",
                request.getCardNum(), request.getAmount(), request.getMerchantId());

        try {
            if (request.getAmount() == null || request.getAmount() <= 0) {
                log.warn("요청 실패: 유효하지 않은 금액");
                return buildErrorResponse("96", "결제 금액은 0보다 커야 합니다", HttpStatus.BAD_REQUEST);
            }
		    }
}
```

**2. 요청/응답 객체 변환 (DTO 매핑)**
* 가맹점 요청 데이터(`PaymentGatewayRequest`)를 카드사 규격(`FdsInspectRequest`)으로 변환
* 카드사 응답(`FdsInspectResponse`)에 원본 결제 금액을 조합하여 최종 가맹점 응답(`PaymentGatewayResponse`) 생성
* [PaymentGatewayController.java](https://github.com/fisa-msa-project/VAN-Service/blob/main/src/main/java/com/bank/controller/PaymentGatewayController.java#L47-L54)
```java
// 1. 요청 객체 변환
FdsInspectRequest fdsRequest = paymentGatwayService.createFdsRequest(request);

// 2. FDS 통신
var fdsResponse = cardFdsClient.inspect(fdsRequest);

// 3. 응답 객체 변환 (원래 결제 금액도 같이 넘겨줌)
PaymentGatewayResponse response = paymentGatwayService.createResponse(fdsResponse, request.getAmount());
```

* [PaymentGatewayService.java](https://github.com/fisa-msa-project/VAN-Service/blob/main/src/main/java/com/bank/service/PaymentGatwayService.java#L14-L35)
```java
public class PaymentGatwayService {

    public FdsInspectRequest createFdsRequest(PaymentGatewayRequest request) {
        return FdsInspectRequest.builder()
                .cardNum(request.getCardNum())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .cardType(request.getCardType())
                .build();
    }

    // FDS의 응답(FdsInspectResponse)과 원래 요청 금액(amount)을 합쳐서 최종 응답을 만듭니다.
    public PaymentGatewayResponse createResponse(FdsInspectResponse fdsResponse, Long originalAmount){
        return PaymentGatewayResponse.builder()
                .success(fdsResponse.isSuccess())
                .transactionId(fdsResponse.getTransactionId())
                .amount(originalAmount) // 최종 응답에 금액 포함
                .responseCode(fdsResponse.getResponseCode())
                .responseMessage(fdsResponse.getResponseMessage())
                .build();
    }
}
```

**3. 외부 시스템 라우팅 (OpenFeign)**
* `Spring Cloud OpenFeign`을 활용하여 다른 포트(9090)에서 동작 중인 CARD-FDS 서버로 HTTP POST 요청 전송
* [CardFdsClient.java](https://github.com/fisa-msa-project/VAN-Service/blob/main/src/main/java/com/bank/api/CardFdsClient.java#L9-L15)
```java
@FeignClient(name = "card-fds-service", url = "http://localhost:9090")
public interface CardFdsClient {

    @PostMapping("/api/fds/inspect")

    FdsInspectResponse inspect(@RequestBody FdsInspectRequest request);
}
```
* 의존성 추가
* [build.gradle](https://github.com/fisa-msa-project/VAN-Service/blob/main/build.gradle#L24-L54)
```xml
ext {
    set('springCloudVersion', "2023.0.0")
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}
```
