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
                .idempotencyKey(request.getIdempotencyKey())
                .build();
    }

    public PaymentGatewayResponse createResponse(FdsInspectResponse fdsResponse, Long originalAmount){
        String code = fdsResponse.getResponseCode();

        // 1. 성공 여부 판단: 코드가 "00"이거나 success가 true일 때만 성공
        boolean isReallySuccess = "00".equals(code) || fdsResponse.isSuccess();

        // 2. 메시지와 코드 보정 (null일 경우만)
        String message = fdsResponse.getResponseMessage();
        if (message == null || message.trim().isEmpty()) {
            message = isReallySuccess ? "결제가 정상적으로 승인되었습니다." : "결제 거절 (사유 미상)";
        }
        if (code == null || code.trim().isEmpty()) {
            code = isReallySuccess ? "00" : "51";
        }

        return PaymentGatewayResponse.builder()
                .success(isReallySuccess)
                .transactionId(fdsResponse.getTransactionId())
                .amount(originalAmount)
                .responseCode(code)
                .responseMessage(message)
                .build();
    }
}