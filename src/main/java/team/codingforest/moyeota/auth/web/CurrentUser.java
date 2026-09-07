package team.codingforest.moyeota.auth.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
컨트롤러 파라미터에 붙이면 "지금 요청한 사용자"가 채워진다.

    @GetMapping("/reservations")
    public List<Res> my(@CurrentUser LoginUser me) { ... }

받을 수 있는 타입은 두 가지다.
  LoginUser : userId와 publicId만 필요할 때(대부분의 API)
  User      : 사용자 본체가 실제로 필요할 때(마이페이지 조회·수정 등)

프론트가 보낸 파라미터는 쳐다보지 않고 토큰만 본다.
그래서 ?userId=2 를 붙여도 남의 데이터에 닿을 방법이 없다.

RUNTIME이어야 실행 중에 읽을 수 있다. 기본값(CLASS)이면 리졸버가 이 표시를 못 본다.
*/
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
