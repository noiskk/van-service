package com.bank.gateway;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoType;
import com.solab.iso8583.MessageFactory;
import com.solab.iso8583.parse.ConfigParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * POS↔VAN 사이 ISO 8583 전문이 왕복(조립→전송→파싱)되는지 검증한다.
 *
 * 이 테스트가 없어서 오래 못 잡은 버그가 있었다. j8583은 파싱 템플릿에 정의되지 않은 필드가
 * 전문에 들어 있으면 파싱 자체를 실패시키는데, 응답(0210) 템플릿에 STAN(11번)이 빠져 있었다.
 * createResponse()가 요청의 11번을 응답에 복사하므로, 거절 응답을 TCP로 내려보낼 때마다
 * POS가 전문을 해석하지 못했다. 승인 경로만 HTTP(curl)로 검증해서 드러나지 않았던 문제다.
 */
class IsoMessageRoundTripTest {

    private MessageFactory<IsoMessage> newFactory() throws Exception {
        MessageFactory<IsoMessage> factory = ConfigParser.createDefault();
        factory.setCharacterEncoding("UTF-8");
        return factory;
    }

    private IsoMessage parsedRequest(MessageFactory<IsoMessage> factory, String stan) throws Exception {
        IsoMessage request = factory.newMessage(0x0200);
        request.setValue(2, "7777771111111111", IsoType.LLVAR, 16);
        request.setValue(4, "500000", IsoType.NUMERIC, 12);
        request.setValue(11, stan, IsoType.NUMERIC, 6);
        request.setValue(42, "MERCHANT-001", IsoType.ALPHA, 15);
        return factory.parseMessage(request.writeData(), 0);
    }

    @Test
    @DisplayName("거절 응답(0210)에 한글 사유가 실려도 POS가 파싱할 수 있다")
    void rejectResponseWithKoreanMessageIsParseable() throws Exception {
        MessageFactory<IsoMessage> factory = newFactory();
        IsoMessage request = parsedRequest(factory, "047917");

        IsoMessage response = factory.createResponse(request);
        response.setValue(39, "15", IsoType.ALPHA, 2);
        response.setValue(120, "중계할 수 있는 카드사가 없습니다 (미등록 BIN: 777777**********)",
                IsoType.LLLVAR, 200);

        byte[] wire = response.writeData();

        assertThatCode(() -> factory.parseMessage(wire, 0)).doesNotThrowAnyException();

        IsoMessage parsed = factory.parseMessage(wire, 0);
        assertThat(parsed.<String>getObjectValue(39)).isEqualTo("15");
        assertThat(parsed.<String>getObjectValue(120))
                .isEqualTo("중계할 수 있는 카드사가 없습니다 (미등록 BIN: 777777**********)");
    }

    @Test
    @DisplayName("응답은 요청의 STAN을 그대로 돌려준다 — 단말이 요청·응답을 짝지을 수 있어야 한다")
    void responseEchoesRequestStan() throws Exception {
        MessageFactory<IsoMessage> factory = newFactory();
        IsoMessage request = parsedRequest(factory, "123456");

        IsoMessage response = factory.createResponse(request);
        response.setValue(39, "00", IsoType.ALPHA, 2);
        response.setValue(120, "승인 완료", IsoType.LLLVAR, 200);

        IsoMessage parsed = factory.parseMessage(response.writeData(), 0);

        assertThat(parsed.<String>getObjectValue(11)).isEqualTo("123456");
        assertThat(parsed.<String>getObjectValue(39)).isEqualTo("00");
    }
}
