package team.codingforest.moyeota.common.bank;

import lombok.Getter;

/**
 * 지급 계좌 은행. 상수 이름은 포트원 V2 Bank 값과 같고, tossCode 는 토스페이먼츠 두 자리 은행 코드
 * 토스 지급대행이 지원하지 않는 홍콩상하이은행은 넣지 않음
 */
@Getter
public enum BankCode {
    IBK("기업은행", "03"),
    KOOKMIN("국민은행", "06"),
    K_BANK("케이뱅크", "89"),
    KAKAO("카카오뱅크", "90"),
    TOSS("토스뱅크", "92"),
    SHINHAN("신한은행", "88"),
    WOORI("우리은행", "20"),
    HANA("하나은행", "81"),
    NONGHYUP("NH농협은행", "11"),
    LOCAL_NONGHYUP("지역농축협", "12"),
    STANDARD_CHARTERED("SC제일은행", "23"),
    CITI("씨티은행", "27"),
    KDB("한국산업은행", "02"),
    SUHYUP("수협은행", "07"),
    DAEGU("iM뱅크(대구)", "31"),
    BUSAN("부산은행", "32"),
    KWANGJU("광주은행", "34"),
    JEJU("제주은행", "35"),
    JEONBUK("전북은행", "37"),
    KYONGNAM("경남은행", "39"),
    KFCC("새마을금고", "45"),
    SHINHYUP("신협", "48"),
    SAVINGS_BANK("저축은행", "50"),
    NFCF("산림조합", "64"),
    POST("우체국", "71");

    private final String displayName;
    private final String tossCode;

    BankCode(String displayName, String tossCode) {
        this.displayName = displayName;
        this.tossCode = tossCode;
    }
}
