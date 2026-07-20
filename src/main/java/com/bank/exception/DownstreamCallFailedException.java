package com.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * 하위 서비스(card-fds-service)가 응답하지 못했음(다운/타임아웃)을 나타내는 예외.
 *
 * FDS가 이상거래를 "차단"하는 것은 이제 200 응답으로 오므로 여기 해당하지 않는다.
 * 오직 FDS가 진짜로 5xx를 내거나 연결 자체가 실패(FeignException)할 때만 발생한다.
 */
public class DownstreamCallFailedException extends SystemException {
    public DownstreamCallFailedException(Long amount, Throwable cause) {
        super("카드사 연동에 실패했습니다", cause, "96", HttpStatus.SERVICE_UNAVAILABLE, amount);
    }
}
