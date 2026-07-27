package com.bank.api;

import com.bank.dto.FdsInspectRequest;
import com.bank.dto.FdsInspectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 카드사 승인 요청 클라이언트.
 *
 * VAN은 카드사 내부 구조(FDS·승인·원장이 어떻게 나뉘어 있는지)를 알지 못하고,
 * 카드사가 공개한 게이트웨이 하나만 호출한다. 카드사가 내부를 어떻게 바꾸든 VAN은 영향받지 않는다.
 */
@FeignClient(name = "card-issuer", url = "${card.issuer.url}")
public interface CardIssuerClient {

    @PostMapping("/api/card/payments/process")
    FdsInspectResponse requestApproval(@RequestBody FdsInspectRequest request);
}
