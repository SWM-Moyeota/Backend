package team.codingforest.moyeota.faresplit.domain;

/**
 *  받을 계좌의 은행. 이름이 fare_split.bank_code 에 저장
 */
public enum Bank {
    KB("KB국민은행", "KB국민"),
    SHINHAN("신한은행", "신한"),
    WOORI("우리은행", "우리"),
    HANA("하나은행", "하나"),
    NH("NH농협은행", "NH농협"),
    IBK("IBK기업은행", "IBK기업"),
    KAKAO("카카오뱅크", "카카오뱅크"),
    TOSS("토스뱅크", "토스뱅크"),
    K_BANK("케이뱅크", "케이뱅크"),
    SC("SC제일은행", "SC제일"),
    CITI("한국씨티은행", "씨티"),
    POST("우체국", "우체국"),
    SAEMAUL("새마을금고", "새마을"),
    SUHYUP("Sh수협은행", "수협"),
    BUSAN("부산은행", "부산"),
    DAEGU("iM뱅크(대구은행)", "iM뱅크"),
    GWANGJU("광주은행", "광주"),
    JEONBUK("전북은행", "전북"),
    GYEONGNAM("경남은행", "경남"),
    JEJU("제주은행", "제주"),
    SHINHYUP("신협", "신협"),
    KDB("KDB산업은행", "산업");

    private final String displayName;
    private final String tossName;

    Bank(String displayName, String tossName) {
        this.displayName = displayName;
        this.tossName = tossName;
    }

    /** 앱에 보여 줄 이름 */
    public String displayName() {
        return displayName;
    }

    public String tossName() {
        return tossName;
    }
}
