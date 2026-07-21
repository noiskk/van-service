package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출금 요청 DTO
 * 요구사항: 4.6
 */
@Schema(description = "결제 요청")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayRequest {
    private String cardNum;
    private Long amount;
    private String merchantId;
    private String idempotencyKey;
}