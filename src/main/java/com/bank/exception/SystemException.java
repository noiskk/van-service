package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 중계하려는 하위 서비스(FDS/카드사)가 응답하지 못하는 등
 * 예상하지 못한 실패를 나타내는 예외. 원본 예외를 cause로 감싼다.
 */
public abstract class SystemException extends DomainException {
    protected SystemException(String message, Throwable cause, String errorCode, HttpStatus httpStatus, Long amount) {
        super(message, cause, errorCode, httpStatus, amount);
    }
}