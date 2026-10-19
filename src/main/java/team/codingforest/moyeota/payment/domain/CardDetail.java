package team.codingforest.moyeota.payment.domain;

/**
 *  사용자에게 보여줄 카드 표시 정보 (값 객체). 빌링키 자체는 PaymentMethod 가 든다.
 *  number 는 포트원 조회 응답을 어댑터에서 다시 마스킹한 표시용 값만 저장한다. 앱으로부터 원본 번호를 받지 않는다.
 */
public record CardDetail(String name, String number, String brand) {

    /** 카드명이 없으면 브랜드로 대신 표시 */
    public String label() {
        return name != null ? name : brand;
    }
}
