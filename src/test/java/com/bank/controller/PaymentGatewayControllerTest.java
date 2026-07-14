package com.bank.controller;

import com.bank.api.CardFdsClient;
import com.bank.dto.FdsInspectResponse;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import com.bank.service.PaymentGatwayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * VAN 응답코드 보존(fidelity) 회귀 테스트.
 * 예전에는 거절 시 응답코드가 무조건 "51"로 덮여 61/96 이 사라졌다.
 * 이 테스트는 payment가 내려준 실제 코드가 그대로 보존되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VAN 응답코드 보존 테스트")
class PaymentGatewayControllerTest {

    @Mock
    private PaymentGatwayService paymentGatwayService;

    @Mock
    private CardFdsClient cardFdsClient;

    @InjectMocks
    private PaymentGatewayController controller;

    @BeforeEach
    void setUp() {
        // 컨트롤러가 HATEOAS self-link를 만들 때 "현재 웹 요청" 정보가 필요하다.
        // 단위 테스트에는 진짜 요청이 없으니 가짜 요청을 하나 끼워준다.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("한도초과(61) 거절 시 응답코드가 51로 뭉개지지 않고 61 그대로 유지된다")
    void declineCode61_isPreserved() {
        PaymentGatewayRequest req = PaymentGatewayRequest.builder()
                .cardNum("1111222233334444")
                .amount(999_999L)
                .merchantId("M1")
                .build();

        // FDS 통신은 통과했다고 가정
        when(cardFdsClient.inspect(any())).thenReturn(FdsInspectResponse.builder().build());
        // 카드사(payment)가 "61 한도초과"로 거절한 상황을 흉내
        when(paymentGatwayService.createResponse(any(), eq(999_999L))).thenReturn(
                PaymentGatewayResponse.builder()
                        .success(false)
                        .responseCode("61")
                        .responseMessage("1회 결제 한도 초과")
                        .transactionId("tx-1")
                        .amount(999_999L)
                        .build());

        ResponseEntity<EntityModel<PaymentGatewayResponse>> res = controller.requestPayment(req);
        PaymentGatewayResponse body = res.getBody().getContent();

        // 예전엔 여기서 51로 뭉개졌다. 이제 61이 유지되어야 한다.
        assertThat(body.getResponseCode()).isEqualTo("61");
        assertThat(body.getResponseMessage()).isEqualTo("1회 결제 한도 초과");
        assertThat(body.isSuccess()).isFalse();
    }
}
