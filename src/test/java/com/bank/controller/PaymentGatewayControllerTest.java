package com.bank.controller;

import com.bank.api.CardIssuerClient;
import com.bank.routing.CardIssuer;
import com.bank.routing.CardIssuerRouter;
import com.bank.service.RelayHistory;
import com.bank.dto.CardApprovalResponse;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import com.bank.exception.GlobalExceptionHandler;
import com.bank.exception.UnsupportedIssuerException;
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

import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentGatewayController 예외처리 & 응답코드 relay 검증.
 * VAN이 스스로 던지는 예외는 InvalidRequest(잘못된 요청)/UnsupportedIssuer(보낼 카드사 없음)/
 * DownstreamCallFailed(카드사 다운) 셋뿐이고, 카드사의 정상 거절(61 등)은 예외가 아니라 그대로 relay된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentGatewayController - 예외처리 & 응답코드 relay 테스트")
class PaymentGatewayControllerTest {

    @Mock
    private PaymentGatwayService paymentGatwayService;

    @Mock
    private CardIssuerClient cardIssuerClient;

    @Mock
    private CardIssuerRouter cardIssuerRouter;

    private MockMvc mockMvc;
    private RelayHistory relayHistory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        relayHistory = new RelayHistory();
        PaymentGatewayController controller = new PaymentGatewayController(
                paymentGatwayService, cardIssuerRouter, cardIssuerClient, relayHistory);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** 라우팅은 별도 테스트에서 검증하므로 여기서는 통과시킨다 */
    private void routesTo(String url) {
        CardIssuer issuer = new CardIssuer();
        issuer.setCode("A_CARD");
        issuer.setName("에이카드");
        issuer.setUrl(url);
        when(cardIssuerRouter.route(any())).thenReturn(issuer);
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
        routesTo("http://localhost:9000");
        when(cardIssuerClient.requestApproval(any(URI.class), any())).thenReturn(CardApprovalResponse.builder().build());
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
        routesTo("http://localhost:9000");
        when(cardIssuerClient.requestApproval(any(URI.class), any())).thenThrow(mock(FeignException.class));

        mockMvc.perform(post("/api/van/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(50_000L)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.responseCode").value("96"));
    }

    @Test
    @DisplayName("미등록 BIN은 카드사로 보내지 않고 15로 자체 거절한다")
    void unknownBin_rejectedWith15() throws Exception {
        when(cardIssuerRouter.route(any()))
                .thenThrow(new UnsupportedIssuerException("미등록 BIN: 777777**********"));

        // 카드사가 거절한 게 아니라 보낼 곳을 못 찾은 것이므로 통신은 정상(200)이고 응답코드로만 알린다
        mockMvc.perform(post("/api/van/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(50_000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("15"));

        verifyNoInteractions(cardIssuerClient);
    }

    @Test
    @DisplayName("카드사에 닿지 못한 거래도 중계 이력에 남는다")
    void failuresAreRecordedInRelayHistory() throws Exception {
        // 관제 화면에 성공 건만 남으면 '미등록 BIN 유입'이나 '특정 카드사 연동 끊김'을 볼 수 없다
        when(cardIssuerRouter.route(any()))
                .thenThrow(new UnsupportedIssuerException("미등록 BIN: 111122**********"));

        mockMvc.perform(post("/api/van/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(50_000L)));

        assertThat(relayHistory.recent())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getResponseCode()).isEqualTo("15");
                    assertThat(entry.isSuccess()).isFalse();
                    // 보낼 카드사를 못 정했으므로 카드사 칸은 비어 있어야 한다
                    assertThat(entry.getIssuerCode()).isNull();
                });
    }

    @Test
    @DisplayName("연동이 끊긴 거래는 어느 카드사였는지까지 이력에 남는다")
    void downstreamFailureRecordsIssuer() throws Exception {
        // 장애 범위를 판단하려면 '어느 카드사와의 회선이 끊겼는지'가 필요하다
        routesTo("http://localhost:9100");
        when(cardIssuerClient.requestApproval(any(URI.class), any())).thenThrow(mock(FeignException.class));

        mockMvc.perform(post("/api/van/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson(50_000L)));

        assertThat(relayHistory.recent())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getResponseCode()).isEqualTo("96");
                    assertThat(entry.getIssuerCode()).isEqualTo("A_CARD");
                });
    }
}
