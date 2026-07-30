package com.bank.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 최근 중계 이력 (관제 화면 표시용).
 * VAN은 거래를 저장하지 않는 중계자라, 시연·디버깅 용도의 인메모리 버퍼만 둔다.
 */
@Component
@Getter
public class RelayHistory {

    private static final int MAX = 50;
    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void record(String channel, String cardNum, Long amount, String merchantId,
                       String idempotencyKey, String responseCode, String message, boolean success,
                       String issuerCode) {
        entries.addFirst(new Entry(LocalDateTime.now(), channel, cardNum, amount, merchantId,
                idempotencyKey, responseCode, message, success, issuerCode));
        while (entries.size() > MAX) {
            entries.pollLast();
        }
    }

    public List<Entry> recent() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    @Getter
    @RequiredArgsConstructor
    public static class Entry {
        private final LocalDateTime at;
        /** TCP(전문) 또는 HTTP */
        private final String channel;
        private final String cardNum;
        private final Long amount;
        private final String merchantId;
        private final String idempotencyKey;
        private final String responseCode;
        private final String message;
        private final boolean success;
        /** 라우팅된 카드사 코드 */
        private final String issuerCode;
    }
}
