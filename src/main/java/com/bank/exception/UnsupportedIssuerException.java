package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 연동하지 않는 카드사이거나 BIN이 라우팅 테이블에 없어 중계할 수 없음.
 *
 * 응답코드 15는 ISO 8583의 "No such issuer"에 해당한다.
 * 카드사가 거절한 게 아니라 VAN 단계에서 보낼 곳을 찾지 못한 것이므로,
 * 시스템 오류(96)가 아니라 별도 코드로 구분한다 — 그래야 가맹점이 원인을 알 수 있다.
 */
public class UnsupportedIssuerException extends BusinessException {

    public UnsupportedIssuerException(String message) {
        super("중계할 수 있는 카드사가 없습니다 (" + message + ")", "15", HttpStatus.OK, null);
    }
}
