package com.bank.routing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 카드사 라우팅 테이블.
 *
 * 설정으로 둔 이유: 카드사 추가·BIN 대역 변경은 VAN에서 일상적으로 일어나는 일이고,
 * 그때마다 코드를 고쳐 배포하면 안 된다. 카드사 하나를 붙이는 데 설정 한 블록이면 되게 했다.
 */
@Component
@ConfigurationProperties(prefix = "van.routing")
@Getter
@Setter
public class RoutingProperties {

    private List<CardIssuer> issuers = new ArrayList<>();
}
