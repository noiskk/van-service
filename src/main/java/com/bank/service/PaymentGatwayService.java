package com.bank.service;

import com.bank.dto.FdsInspectRequest;
import com.bank.dto.FdsInspectResponse;
import com.bank.dto.PaymentGatewayRequest;
import com.bank.dto.PaymentGatewayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentGatwayService {

    public FdsInspectRequest createFdsRequest(PaymentGatewayRequest request) {
        return FdsInspectRequest.builder()
                .cardNum(request.getCardNum())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .cardType(request.getCardType())
                .build();
    }

    public PaymentGatewayResponse createResponse(FdsInspectResponse fdsResponse, Long originalAmount){
        // 방어 로직: FDS 서버가 응답 메시지를 빼먹고 보냈을 경우를 대비
        String message = fdsResponse.getResponseMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "결제가 정상적으로 승인되었습니다.";
        }

        String code = fdsResponse.getResponseCode();
        if (code == null || code.trim().isEmpty()) {
            code = "00"; // 기본 승인 코드
        }

        return PaymentGatewayResponse.builder()
                .success(true)
                .transactionId(fdsResponse.getTransactionId())
                .amount(originalAmount)
                .responseCode(code)
                .responseMessage(message)
                .build();
    }
}