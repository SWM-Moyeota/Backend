package team.codingforest.moyeota.auth.dto;

//구글 로그인 후 리다이렉트 URL의 ?code= 값을 그대로 담아 보낸다.
public record ExchangeRequest(String code) {
}
