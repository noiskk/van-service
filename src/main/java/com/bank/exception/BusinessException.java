package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * VAN이 받은 요청 자체가 잘못되어 거절함을 나타내는 예외.
 * (VAN은 중계자라 결제 판단은 안 하고, 명백히 잘못된 요청만 자체 거절한다.)
 */
public abstract class BusinessException extends DomainException {
    protected BusinessException(String message, String errorCode, HttpStatus httpStatus, Long amount) {
        super(message, errorCode, httpStatus, amount);
    }
}