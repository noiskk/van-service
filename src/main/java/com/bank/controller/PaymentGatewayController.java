package com.bank.controller;

import com.bank.api.CardIssuerClient;
import com.bank.dto.FdsInspectRequest;
import com.bank.dto.FdsInspectResponse;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import com.bank.exception.DownstreamCallFailedException;
import com.bank.exception.InvalidRequestException;
import com.bank.service.PaymentGatwayService;
import com.bank.service.RelayHistory;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Tag(name = "결제 요청 처리", description = "가맹점 결제 요청 수신 및 카드사 라우팅 API")
@RestController
@RequestMapping("/api/van")
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController {

    private final PaymentGatwayService paymentGatwayService;
    private final CardIssuerClient cardIssuerClient;
    private final RelayHistory relayHistory;

    @Operation(summary = "카드 거래 요청", description = "카드사 승인 요청을 보냅니다.")
    @PostMapping("/payments")
    public ResponseEntity<EntityModel<PaymentGatewayResponse>> requestPayment(@RequestBody PaymentGatewayRequest request) {
        log.info("카드 거래 요청 수신: cardNum={}, amount={}, merchantId={}",
                request.getCardNum(), request.getAmount(), request.getMerchantId());

        // VAN 자체 입력 검증 (VAN은 중계자라 결제 판단은 안 하고, 명백히 잘못된 요청만 거절)
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new InvalidRequestException("결제 금액은 0보다 커야 합니다");
        }

        // 1. 요청 객체를 카드사 규격으로 변환
        FdsInspectRequest approvalRequest = paymentGatwayService.createFdsRequest(request);

        // 2. 카드사 게이트웨이로 중계
        //    이상거래 차단/한도초과 등 비즈니스 결과는 200으로 오므로 그대로 relay된다.
        //    카드사가 진짜로 죽었을 때만 FeignException -> 시스템 실패로 전파.
        FdsInspectResponse approvalResponse;
        try {
            approvalResponse = cardIssuerClient.requestApproval(approvalRequest);
        } catch (FeignException e) {
            throw new DownstreamCallFailedException(request.getAmount(), e);
        }

        // 3. 카드사 응답을 POS 규격으로 변환하여 relay (응답코드/메시지 보정은 서비스가 담당)
        PaymentGatewayResponse response = paymentGatwayService.createResponse(approvalResponse, request.getAmount());

        // POS가 TCP로 보낸 전문은 이 메서드를 직접 호출하므로 HTTP 요청 컨텍스트가 없다. 그걸로 채널을 구분한다.
        String channel = (RequestContextHolder.getRequestAttributes() != null) ? "HTTP" : "TCP";
        relayHistory.record(channel, request.getCardNum(), request.getAmount(), request.getMerchantId(),
                request.getIdempotencyKey(), response.getResponseCode(), response.getResponseMessage(),
                response.isSuccess());

        if (response.isSuccess()) {
            log.info("✅ 정상 승인: cardNum={}, amount={}", request.getCardNum(), request.getAmount());
        } else {
            log.warn("❌ 결제 거절: code={}, msg={}", response.getResponseCode(), response.getResponseMessage());
        }

        EntityModel<PaymentGatewayResponse> entityModel = EntityModel.of(response);
        entityModel.add(linkTo(methodOn(PaymentGatewayController.class).requestPayment(request)).withSelfRel());
        return ResponseEntity.ok(entityModel);
    }
}