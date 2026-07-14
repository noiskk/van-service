package com.bank.controller;

import com.bank.api.CardFdsClient;
import com.bank.dto.FdsInspectRequest;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import com.bank.service.PaymentGatwayService;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Tag(name = "결제 요청 처리", description = "가맹점 결제 요청 수신 및 카드사 라우팅 API")
@RestController
@RequestMapping("/api/van")
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController {

    private final PaymentGatwayService paymentGatwayService;
    private final CardFdsClient cardFdsClient;

    @Operation(summary = "카드 거래 요청", description = "카드사 승인 요청을 보냅니다.")
    @PostMapping("/payments")
    public ResponseEntity<EntityModel<PaymentGatewayResponse>> requestPayment(@RequestBody PaymentGatewayRequest request) {
        log.info("카드 거래 요청 수신: cardNum={}, amount={}, merchantId={}",
                request.getCardNum(), request.getAmount(), request.getMerchantId());

        try {
            if (request.getAmount() == null || request.getAmount() <= 0) {
                return buildErrorResponse("96", "결제 금액은 0보다 커야 합니다", HttpStatus.BAD_REQUEST);
            }

            // 1. 요청 객체 변환
            FdsInspectRequest fdsRequest = paymentGatwayService.createFdsRequest(request);

            // 2. FDS 통신
            var fdsResponse = cardFdsClient.inspect(fdsRequest);

            // 3. 응답 객체 변환
            PaymentGatewayResponse response = paymentGatwayService.createResponse(fdsResponse, request.getAmount());

            // Service에서 넘어온 결과 상태를 체크해서 로그와 데이터를 분기
            if (response.isSuccess()) {
                log.info("✅ 정상 승인 처리 되었습니다: cardNum={}, amount={}", request.getCardNum(), request.getAmount());
            } else {
                // 거절: payment가 내려준 실제 응답코드(51/61/96 등)를 그대로 보존한다.
                String errMsg = (response.getResponseMessage() != null && !response.getResponseMessage().isBlank())
                        ? response.getResponseMessage()
                        : "결제가 거절되었습니다. (카드사 문의)";
                log.warn("❌ 결제 거절(비즈니스 사유): code={}, msg={}", response.getResponseCode(), errMsg);

                response = PaymentGatewayResponse.builder()
                        .success(false)
                        .transactionId(response.getTransactionId())
                        .amount(response.getAmount())
                        .responseCode(response.getResponseCode())   // 51 하드코딩 제거 → 실제 코드 유지
                        .responseMessage(errMsg)
                        .build();
            }

            EntityModel<PaymentGatewayResponse> entityModel = EntityModel.of(response);
            WebMvcLinkBuilder selfLink = linkTo(methodOn(PaymentGatewayController.class).requestPayment(request));
            entityModel.add(selfLink.withSelfRel());

            return ResponseEntity.ok(entityModel);

        } catch (FeignException e) {
            log.error("FDS 차단 발생: status={}, body={}", e.status(), e.contentUTF8());

            String errorMessage = "카드사 연동 오류";
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                errorMessage = mapper.readTree(e.contentUTF8()).path("message").asText();
            } catch (Exception parseEx) {
                errorMessage = e.contentUTF8();
            }

            return buildErrorResponse("51", errorMessage, HttpStatus.OK);
        } catch (Exception e) {
            log.error("결제 처리 실패 - 시스템 오류: {}", e.getMessage(), e);
            return buildErrorResponse("96", "시스템 내부 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<EntityModel<PaymentGatewayResponse>> buildErrorResponse(String code, String message, HttpStatus status) {
        PaymentGatewayResponse errorResponse = PaymentGatewayResponse.builder()
                .success(false)
                .responseCode(code)
                .responseMessage(message)
                .build();
        return ResponseEntity.status(status).body(EntityModel.of(errorResponse));
    }
}