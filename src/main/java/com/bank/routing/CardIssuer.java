package com.bank.routing;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * VAN이 연동 중인 카드사 하나.
 *
 * 실제 VAN은 카드사마다 별도 회선·전문 규격·운영 시간을 갖는다.
 * 여기서는 라우팅에 필요한 최소 정보(식별자·엔드포인트·담당 BIN 대역)만 둔다.
 */
@Getter
@Setter
public class CardIssuer {

    /** 카드사 코드 (로그·이력에 남는 식별자) */
    private String code;

    private String name;

    /** 승인 요청을 보낼 주소 */
    private String url;

    /**
     * 이 카드사가 발급하는 BIN(Bank Identification Number) 목록.
     * 카드번호 앞자리로, 실무에서는 6자리를 쓰다가 대역이 모자라 8자리로 확장하는 추세다.
     */
    private List<String> bins = new ArrayList<>();

    /** 연동 중단(장애·계약 종료) 시 라우팅에서 제외 */
    private boolean enabled = true;
}
