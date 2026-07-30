package com.bank.routing;

import com.bank.exception.UnsupportedIssuerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

/**
 * 카드번호로 발급 카드사를 판별한다. VAN의 핵심 역할이다.
 *
 * VAN은 단순 프록시가 아니라 "이 카드가 어느 카드사 건지 판별해 그리로 보내는" 중계자다.
 * 판별 기준은 카드번호 앞자리인 BIN이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CardIssuerRouter {

    private final RoutingProperties properties;

    /**
     * @throws UnsupportedIssuerException 연동하지 않는 카드사거나 BIN이 등록되지 않은 경우.
     *         계약되지 않은 카드사로는 전문을 보낼 수 없으므로 VAN이 자체 거절한다.
     */
    public CardIssuer route(String cardNum) {
        if (cardNum == null || cardNum.isBlank()) {
            throw new UnsupportedIssuerException("카드번호가 없습니다");
        }

        CardIssuer issuer = findByBin(cardNum)
                .orElseThrow(() -> new UnsupportedIssuerException(
                        "미등록 BIN: " + mask(cardNum)));

        log.info("카드사 라우팅 - BIN: {}, 카드사: {}({})",
                mask(cardNum), issuer.getName(), issuer.getCode());
        return issuer;
    }

    /**
     * 가장 긴 BIN 접두사를 우선 매칭한다.
     *
     * BIN은 6자리로 시작했지만 대역이 부족해 8자리로 세분화되는 추세다.
     * 그래서 6자리 대역 안에 다른 카드사의 8자리 대역이 들어있을 수 있고,
     * 이때는 더 구체적인 쪽이 이겨야 한다.
     */
    private Optional<CardIssuer> findByBin(String cardNum) {
        return properties.getIssuers().stream()
                .filter(CardIssuer::isEnabled)
                .flatMap(issuer -> issuer.getBins().stream()
                        .filter(cardNum::startsWith)
                        .map(bin -> new Match(bin.length(), issuer)))
                .max(Comparator.comparingInt(Match::binLength))
                .map(Match::issuer);
    }

    /** 로그에 카드번호를 그대로 남기지 않는다 */
    private String mask(String cardNum) {
        if (cardNum.length() <= 6) {
            return cardNum;
        }
        return cardNum.substring(0, 6) + "*".repeat(cardNum.length() - 6);
    }

    private record Match(int binLength, CardIssuer issuer) {
    }
}
