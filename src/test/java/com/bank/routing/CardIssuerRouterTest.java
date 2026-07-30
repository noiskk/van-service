package com.bank.routing;

import com.bank.exception.UnsupportedIssuerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 카드사 라우팅 테스트.
 *
 * VAN의 핵심 역할이 "카드번호로 발급 카드사를 판별해 그리로 보내는 것"이므로,
 * 판별이 틀리면 다른 카드사로 전문이 가는 사고가 된다.
 */
@DisplayName("카드사 라우팅 테스트")
class CardIssuerRouterTest {

    private CardIssuerRouter router;

    private CardIssuer issuer(String code, String url, boolean enabled, String... bins) {
        CardIssuer i = new CardIssuer();
        i.setCode(code);
        i.setName(code);
        i.setUrl(url);
        i.setEnabled(enabled);
        i.setBins(List.of(bins));
        return i;
    }

    @BeforeEach
    void setUp() {
        RoutingProperties props = new RoutingProperties();
        props.setIssuers(List.of(
                issuer("A_CARD", "http://localhost:9000", true, "411111", "555555"),
                issuer("B_CARD", "http://localhost:9100", true, "622222"),
                // A_CARD의 411111 대역 안에 있는 더 좁은 8자리 대역
                issuer("C_CARD", "http://localhost:9200", true, "41111199"),
                issuer("DEAD_CARD", "http://localhost:9300", false, "999999")
        ));
        router = new CardIssuerRouter(props);
    }

    @Test
    @DisplayName("BIN으로 발급 카드사를 찾는다")
    void routesByBin() {
        assertThat(router.route("4111111111111111").getCode()).isEqualTo("A_CARD");
        assertThat(router.route("6222221111111111").getCode()).isEqualTo("B_CARD");
    }

    @Test
    @DisplayName("판별한 카드사의 주소를 돌려준다")
    void returnsIssuerUrl() {
        assertThat(router.route("6222221111111111").getUrl()).isEqualTo("http://localhost:9100");
    }

    @Test
    @DisplayName("더 긴 BIN 대역이 우선한다")
    void longestPrefixWins() {
        // 41111199…는 A_CARD(411111)에도 걸리지만 더 구체적인 C_CARD(41111199)로 가야 한다.
        // BIN이 6자리에서 8자리로 세분화되면서 실제로 생기는 상황이다.
        assertThat(router.route("4111119912345678").getCode()).isEqualTo("C_CARD");
        // 같은 6자리 대역이라도 8자리 조건에 안 맞으면 원래 카드사로
        assertThat(router.route("4111110012345678").getCode()).isEqualTo("A_CARD");
    }

    @Test
    @DisplayName("연동 중단된 카드사로는 라우팅하지 않는다")
    void skipsDisabledIssuer() {
        assertThatThrownBy(() -> router.route("9999991111111111"))
                .isInstanceOf(UnsupportedIssuerException.class);
    }

    @Test
    @DisplayName("등록되지 않은 BIN은 중계하지 않고 자체 거절한다")
    void unknownBinRejected() {
        assertThatThrownBy(() -> router.route("7777771111111111"))
                .isInstanceOf(UnsupportedIssuerException.class)
                .hasMessageContaining("중계할 수 있는 카드사가 없습니다");
    }

    @Test
    @DisplayName("거절 사유에 카드번호를 그대로 남기지 않는다")
    void masksCardNumberInMessage() {
        assertThatThrownBy(() -> router.route("7777771234567890"))
                .isInstanceOf(UnsupportedIssuerException.class)
                .hasMessageContaining("777777")
                .hasMessageNotContaining("1234567890");
    }

    @Test
    @DisplayName("카드번호가 없으면 거절한다")
    void nullCardRejected() {
        assertThatThrownBy(() -> router.route(null)).isInstanceOf(UnsupportedIssuerException.class);
        assertThatThrownBy(() -> router.route("")).isInstanceOf(UnsupportedIssuerException.class);
    }
}
