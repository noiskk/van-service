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

    // FDS의 응답(FdsInspectResponse)과 원래 요청 금액(amount)을 합쳐서 최종 응답을 만듭니다.
    public PaymentGatewayResponse createResponse(FdsInspectResponse fdsResponse, Long originalAmount){
        return PaymentGatewayResponse.builder()
                .success(fdsResponse.isSuccess())
                .transactionId(fdsResponse.getTransactionId())
                .amount(originalAmount) // 최종 응답에 금액 포함
                .responseCode(fdsResponse.getResponseCode())
                .responseMessage(fdsResponse.getResponseMessage())
                .build();
    }
}