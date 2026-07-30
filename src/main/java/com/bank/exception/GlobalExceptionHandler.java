package com.bank.exception;

import com.bank.dto.PaymentGatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<EntityModel<PaymentGatewayResponse>> handleBusiness(BusinessException ex) {
        log.warn("요청 거절(비즈니스 사유): code={}, msg={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(EntityModel.of(toResponse(ex)));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<EntityModel<PaymentGatewayResponse>> handleSystem(SystemException ex) {
        log.error("결제 중계 중 시스템 오류: code={}, msg={}", ex.getErrorCode(), ex.getMessage(), ex);
        return ResponseEntity.status(ex.getHttpStatus()).body(EntityModel.of(toResponse(ex)));
    }

    /**
     * 없는 정적 리소스 요청(favicon.ico 등)은 404로 끝낸다.
     * catch-all(Exception)에 걸리면 500 + ERROR 로그가 남아 실제 장애와 구분되지 않는다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EntityModel<PaymentGatewayResponse>> handleUnknown(Exception ex) {
        log.error("예상하지 못한 오류", ex);
        PaymentGatewayResponse response = PaymentGatewayResponse.builder()
                .success(false)
                .responseCode("96")
                .responseMessage("시스템 오류가 발생했습니다")
                .build();
        return ResponseEntity.internalServerError().body(EntityModel.of(response));
    }

    private PaymentGatewayResponse toResponse(DomainException ex) {
        return PaymentGatewayResponse.builder()
                .success(false)
                .amount(ex.getAmount())
                .responseCode(ex.getErrorCode())
                .responseMessage(ex.getMessage())
                .build();
    }
}
