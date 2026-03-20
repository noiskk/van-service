package com.bank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "FDS 승인 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FdsInspectResponse {
    private boolean success;          // boolean으로 변경
    private String transactionId;     // FDS가 생성한 트랜잭션 ID
    private String responseCode;      // 결과 코드
    private String responseMessage;   // 결과 메시지
}