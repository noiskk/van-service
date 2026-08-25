package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "승인 요청 결과")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardApprovalRequest {
    private String cardNum;
    private Long amount;
    private String merchantId;
    private String cardType;
    private String idempotencyKey;
}