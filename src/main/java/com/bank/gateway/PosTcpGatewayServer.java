package com.bank.gateway;

import com.bank.controller.PaymentGatewayController;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.parse.ConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

@Slf4j
@Component
@RequiredArgsConstructor
public class PosTcpGatewayServer {

    private final PaymentGatewayController paymentGatewayController;
    private static final int TCP_PORT = 7777;
    private MessageFactory<IsoMessage> messageFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void startTcpServer() {
        try {
            messageFactory = ConfigParser.createDefault();

            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                    log.info("🔥 [VAN TCP Gateway] {}번 포트에서 POS 연결 대기 중...", TCP_PORT);

                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        handlePosClient(clientSocket);
                    }
                } catch (Exception e) {
                    log.error("[VAN TCP Gateway] 서버 소켓 에러", e);
                }
            }).start();

        } catch (Exception e) {
            log.error("MessageFactory 초기화 실패! j8583.xml을 찾을 수 없습니다.", e);
        }
    }

    private void handlePosClient(Socket socket) {
        new Thread(() -> {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                log.info("💳 [VAN] POS 단말기 연결됨! (IP: {})", socket.getInetAddress());

                byte[] buffer = new byte[2048];
                int bytesRead = in.read(buffer);

                if (bytesRead > 0) {
                    IsoMessage isoReq = messageFactory.parseMessage(buffer, 0);
                    log.info("[VAN] ISO 8583 전문 수신 완료! (MTI: {})", String.format("%04x", isoReq.getType()));

                    String cardNumber = isoReq.getObjectValue(2);
                    String amountStr = isoReq.getObjectValue(4);
                    Long amount = Long.parseLong(amountStr) / 100;
                    String stan = isoReq.getObjectValue(11);
                    String merchantId = isoReq.getObjectValue(42);

                    // 멱등키 = 가맹점ID + STAN (원점 POS가 만든 거래추적번호)
                    String idempotencyKey = merchantId + "-" + stan;

                    log.info("[VAN] 추출된 데이터 - 카드: {}, 금액: {}, 가맹점: {}, 멱등키: {}",
                            cardNumber, amount, merchantId, idempotencyKey);

                    PaymentGatewayRequest pgRequest = PaymentGatewayRequest.builder()
                            .cardNum(cardNumber)
                            .amount(amount)
                            .merchantId(merchantId)
                            .idempotencyKey(idempotencyKey)
                            .build();

                    ResponseEntity<EntityModel<PaymentGatewayResponse>> responseEntity =
                            paymentGatewayController.requestPayment(pgRequest);

                    PaymentGatewayResponse pgResponse = responseEntity.getBody().getContent();
                    boolean isSuccess = pgResponse.isSuccess();
                    // payment가 내려준 실제 코드를 그대로 POS로 전달 (없을 때만 00/51로 보정)
                    String responseCode = (pgResponse.getResponseCode() != null && !pgResponse.getResponseCode().isBlank())
                            ? pgResponse.getResponseCode()
                            : (isSuccess ? "00" : "51");

                    // 메시지가 null이면 기본 텍스트 세팅
                    String responseMsg = pgResponse.getResponseMessage();
                    if (responseMsg == null || responseMsg.isEmpty()) {
                        responseMsg = isSuccess ? "승인 완료" : "결제 거절 (사유 미상)";
                    }

                    IsoMessage isoRes = messageFactory.createResponse(isoReq);
                    isoRes.setValue(39, responseCode, IsoType.ALPHA, 2);

                    // 거절이거나, 성공이더라도 메시지가 있으면 120번에 담아 전송
                    isoRes.setValue(120, responseMsg, IsoType.LLLVAR, 200);

                    out.write(isoRes.writeData());
                    out.flush();
                    log.info("[VAN] POS로 0210 승인 응답 발송 완료! (응답코드: {}, 메시지: {})", responseCode, responseMsg);
                }

            } catch (Exception e) {
                log.error("[VAN] POS 요청 처리 중 에러 발생", e);
            }
        }).start();
    }
}