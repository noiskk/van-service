package com.bank.api;

import com.bank.dto.CardApprovalRequest;
import com.bank.dto.CardApprovalResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;

/**
 * 카드사 승인 요청 클라이언트.
 *
 * 보낼 주소를 고정하지 않고 호출 시점에 받는다. VAN은 여러 카드사와 연동하므로
 * 카드번호(BIN)로 판별한 카드사의 주소로 그때그때 보내야 하기 때문이다.
 * (Feign은 첫 파라미터가 URI면 그 호스트를 대상으로 삼는다)
 *
 * VAN은 카드사 내부 구조를 알지 못하고, 카드사가 공개한 게이트웨이 하나만 호출한다.
 */
@FeignClient(name = "card-issuer", url = "http://unused")
public interface CardIssuerClient {

    @PostMapping("/api/card/payments/process")
    CardApprovalResponse requestApproval(URI issuerUrl, @RequestBody CardApprovalRequest request);
}
