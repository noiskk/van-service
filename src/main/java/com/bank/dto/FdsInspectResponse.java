package com.bank.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    // payment 서비스는 이 필드를 "message"라는 이름으로 내려준다.
    // @JsonAlias로 "message" 이름표도 이 상자에 담기게 해서 거절 사유가 유실되지 않게 한다.
    @JsonAlias({"message"})
    private String responseMessage;   // 결과 메시지
}