package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 출금 응답 DTO
 * 요구사항: 4.6
 */
@Schema(description = "출금 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayResponse {
    private boolean success;
    private String transactionId; // 승인 번호
    private Long amount;          // 결제 금액
    private String responseCode;  // "00": 성공, "51": 잔액부족, "FDS": 이상거래차단 등
    private String responseMessage;
}