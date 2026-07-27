package com.bank.controller;

import com.bank.api.CardIssuerClient;
import com.bank.service.RelayHistory;
import com.bank.dto.FdsInspectResponse;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import com.bank.exception.GlobalExceptionHandler;
import com.bank.service.PaymentGatwayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentGatewayController 예외처리 & 응답코드 relay 검증.
 * VAN은 중계자라 예외가 InvalidRequest(잘못된 요청)/DownstreamCallFailed(FDS 다운) 둘뿐이고,
 * 카드사의 정상 거절(61 등)은 예외가 아니라 그대로 relay된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentGatewayController - 예외처리 & 응답코드 relay 테스트")
class PaymentGatewayControllerTest {

    @Mock
    private PaymentGatwayService paymentGatwayService;

    @Mock
    private CardIssuerClient cardIssuerClient;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PaymentGatewayController controller = new PaymentGatewayController(paymentGatwayService, cardIssuerClient, new RelayHistory());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String requestJson(Long amount) throws Exception {
        PaymentGatewayRequest req = PaymentGatewayRequest.builder()
                .cardNum("1111222233334444")
                .amount(amount)
                .merchantId("M1")
                .build();
        return objectMapper.writeValueAsString(req);
    }

    @Test
    @DisplayName("카드사 거절코드(61)가 51로 뭉개지지 않고 그대로 relay된다 (0단계 회귀)")
    void declineCode61_isPreserved() throws Exception {
        when(cardIssuerClient.requestApproval(any())).thenReturn(FdsInspectResponse.builder().build());
        when(paymentGatwayService.createResponse(any(), eq(999_999L))).thenReturn(
                PaymentGatewayResponse.builder()
                        .success(false)
                        .responseCode("61")
                        .responseMessage("1회 결제 한도 초과")
                        .transactionId("tx-1")
                        .amount(999_999L)
                        .build());

        mockMvc.perform(post("/api/van/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(999_999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("61"))
                .andExpect(jsonPath("$.responseMessage").value("1회 결제 한도 초과"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("금액 0 이하(InvalidRequestException) -> HTTP 400")
    void invalidAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/van/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(0L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.responseCode").value("96"));
    }

    @Test
    @DisplayName("FDS 다운(FeignException) -> HTTP 503 (시스템 실패로 전파)")
    void fdsDown_returns503() throws Exception {
        when(cardIssuerClient.requestApproval(any())).thenThrow(mock(FeignException.class));

        mockMvc.perform(post("/api/van/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(50_000L)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.responseCode").value("96"));
    }
}
