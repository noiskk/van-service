package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * VAN-service의 모든 도메인 예외의 최상위 클래스.
 * errorCode/httpStatus를 들고 있어서 GlobalExceptionHandler가 예외 타입만 보고
 * HTTP 응답과 PaymentGatewayResponse 바디를 조립할 수 있다.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Long amount;

    protected DomainException(String message, String errorCode, HttpStatus httpStatus, Long amount) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.amount = amount;
    }

    protected DomainException(String message, Throwable cause, String errorCode, HttpStatus httpStatus, Long amount) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.amount = amount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Long getAmount() {
        return amount;
    }
}
