package team.codingforest.moyeota.payment.dto;

/**
 *  결제사 조회 응답에서 추린 카드 표시 정보. Card 엔티티에 그대로 저장한다.
 *  number 는 PortOneClient 가 다시 마스킹한 표시용 값이다. 앱으로부터 원본 번호를 받지 않는다.
 */
public record CardDetail(String name, String number, String brand) {
}
