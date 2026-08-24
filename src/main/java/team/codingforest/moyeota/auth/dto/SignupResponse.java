package team.codingforest.moyeota.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/*
로컬 회원가입 응답. 성공했는지 여부만 담는다.

토큰은 들어 있지 않다. 가입은 계정을 만들기만 하고, 로그인(토큰 발급)은 POST /login이 따로 한다.
그래서 이 응답을 받은 프론트는 곧바로 로그인 화면(또는 로그인 요청)으로 넘어가야 한다.

사용자를 가리키는 값(userId, publicId)도 내보내지 않는다.
프론트가 다음에 할 일은 로그인뿐이고, 로그인은 loginId/password로 하지 이 값들로 하지 않기 때문이다.
사용자 정보가 필요해지는 시점은 로그인해서 토큰을 받은 뒤이고, 그때는 GET /api/mypage가 알려준다.
*/
public record SignupResponse(

        @Schema(description = "가입 성공 여부. 실패는 이 필드가 아니라 HTTP 상태코드로 온다(400/409)",
                example = "true")
        boolean success) {
}
