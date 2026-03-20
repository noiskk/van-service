package com.bank.controller;

import com.bank.api.CardFdsClient; // 패키지 경로에 맞게 확인해주세요!
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
                log.warn("요청 실패: 유효하지 않은 금액");
                return buildErrorResponse("96", "결제 금액은 0보다 커야 합니다", HttpStatus.BAD_REQUEST);
            }

            // 1. 요청 객체 변환
            FdsInspectRequest fdsRequest = paymentGatwayService.createFdsRequest(request);

            // 2. FDS 통신
            var fdsResponse = cardFdsClient.inspect(fdsRequest);

            // 3. 응답 객체 변환 (원래 결제 금액도 같이 넘겨줌)
            PaymentGatewayResponse response = paymentGatwayService.createResponse(fdsResponse, request.getAmount());

            log.info("요청이 정상적으로 처리 되었습니다.: cardNum={}, amount={}, merchantId={}",
                    request.getCardNum(), request.getAmount(), request.getMerchantId());

            EntityModel<PaymentGatewayResponse> entityModel = EntityModel.of(response);
            WebMvcLinkBuilder selfLink = linkTo(methodOn(PaymentGatewayController.class).requestPayment(request));
            entityModel.add(selfLink.withSelfRel());

            return ResponseEntity.ok(entityModel);

        } catch (FeignException e) {
            // 외부 FDS 서버가 응답이 없거나 통신 에러가 난 경우
            log.error("FDS 서버 통신 오류: status={}, message={}", e.status(), e.getMessage());
            return buildErrorResponse("96", "카드사 네트워크 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

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