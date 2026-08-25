package com.bank.controller;

import com.bank.api.CardIssuerClient;
import com.bank.dto.CardApprovalRequest;
import com.bank.dto.CardApprovalResponse;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import com.bank.exception.DownstreamCallFailedException;
import com.bank.exception.InvalidRequestException;
import com.bank.exception.UnsupportedIssuerException;
import com.bank.routing.CardIssuer;
import com.bank.routing.CardIssuerRouter;
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

import java.net.URI;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Tag(name = "결제 요청 처리", description = "가맹점 결제 요청 수신 및 카드사 라우팅 API")
@RestController
@RequestMapping("/api/van")
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController {

    private final PaymentGatwayService paymentGatwayService;
    private final CardIssuerRouter cardIssuerRouter;
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

        // 1. 카드번호(BIN)로 보낼 카드사를 판별한다 — VAN의 핵심 역할
        //    연동하지 않는 카드사면 여기서 자체 거절(15)한다. 보낼 곳이 없기 때문.
        CardIssuer issuer;
        try {
            issuer = cardIssuerRouter.route(request.getCardNum());
        } catch (UnsupportedIssuerException e) {
            // 라우팅 실패도 중계 시도 이력이다. 미등록 BIN 유입은 관제 화면에서 봐야 하는 신호라
            // (라우팅 테이블 누락인지 잘못된 카드인지 판단해야 한다) 남기고 다시 던진다.
            recordFailure(request, e.getErrorCode(), e.getMessage(), null);
            throw e;
        }

        // 2. 요청 객체를 카드사 규격으로 변환
        CardApprovalRequest approvalRequest = paymentGatwayService.createApprovalRequest(request);

        // 3. 판별된 카드사로 중계
        //    이상거래 차단/한도초과 등 비즈니스 결과는 200으로 오므로 그대로 relay된다.
        //    카드사가 진짜로 죽었을 때만 FeignException -> 시스템 실패로 전파.
        CardApprovalResponse approvalResponse;
        try {
            approvalResponse = cardIssuerClient.requestApproval(URI.create(issuer.getUrl()), approvalRequest);
        } catch (FeignException e) {
            DownstreamCallFailedException failure = new DownstreamCallFailedException(request.getAmount(), e);
            // 어느 카드사와의 연동이 끊겼는지가 곧 장애 범위다. 카드사 코드까지 같이 남긴다.
            recordFailure(request, failure.getErrorCode(), failure.getMessage(), issuer.getCode());
            throw failure;
        }

        // 4. 카드사 응답을 POS 규격으로 변환하여 relay (응답코드/메시지 보정은 서비스가 담당)
        PaymentGatewayResponse response = paymentGatwayService.createResponse(approvalResponse, request.getAmount());

        relayHistory.record(channel(), request.getCardNum(), request.getAmount(), request.getMerchantId(),
                request.getIdempotencyKey(), response.getResponseCode(), response.getResponseMessage(),
                response.isSuccess(), issuer.getCode());

        if (response.isSuccess()) {
            log.info("✅ 정상 승인: cardNum={}, amount={}, 카드사={}",
                    request.getCardNum(), request.getAmount(), issuer.getCode());
        } else {
            log.warn("❌ 결제 거절: code={}, msg={}", response.getResponseCode(), response.getResponseMessage());
        }

        EntityModel<PaymentGatewayResponse> entityModel = EntityModel.of(response);
        entityModel.add(linkTo(methodOn(PaymentGatewayController.class).requestPayment(request)).withSelfRel());
        return ResponseEntity.ok(entityModel);
    }

    /** 카드사에 닿기 전/중에 끊긴 거래도 중계 이력에 남긴다. 응답 조립은 GlobalExceptionHandler가 계속 맡는다. */
    private void recordFailure(PaymentGatewayRequest request, String responseCode, String message, String issuerCode) {
        relayHistory.record(channel(), request.getCardNum(), request.getAmount(), request.getMerchantId(),
                request.getIdempotencyKey(), responseCode, message, false, issuerCode);
    }

    /** POS가 TCP로 보낸 전문은 이 컨트롤러를 직접 호출하므로 HTTP 요청 컨텍스트가 없다. 그걸로 채널을 구분한다. */
    private String channel() {
        return RequestContextHolder.getRequestAttributes() != null ? "HTTP" : "TCP";
    }
}