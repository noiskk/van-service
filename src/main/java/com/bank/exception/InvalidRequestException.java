package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * VAN이 받은 결제 요청 자체가 형식적으로 잘못됐음을 나타내는 예외.
 * (예: 결제 금액이 0 이하)
 *
 * 진짜 malformed request이므로 400. (FDS/카드사의 정상 거절은 예외가 아니라 relay다.)
 */
public class InvalidRequestException extends BusinessException {
    public InvalidRequestException(String message) {
        super(message, "96", HttpStatus.BAD_REQUEST, null);
    }
}
