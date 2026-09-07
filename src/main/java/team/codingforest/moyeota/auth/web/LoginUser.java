package team.codingforest.moyeota.auth.web;

import java.util.UUID;

/*
로그인한 사용자의 신원. 컨트롤러가 서비스에 넘길 값만 담는다.

User 엔티티를 그대로 넘기지 않는 이유:
컨트롤러가 영속 객체를 들고 다니면 응답 직전에 지연 로딩이 터지거나
의도치 않은 변경이 트랜잭션 밖에서 새는 일이 생긴다.
필요한 것은 "누구인가" 두 값뿐이므로 그것만 준다.

  userId   : 팀 서비스들이 받는 값. DB의 PK다.
  publicId : 밖으로 내보내거나 로그에 남길 때 쓰는 값.
             user_id(순번)를 로그에 남기면 다른 사용자를 추측할 수 있으므로 이쪽을 쓴다.
*/
public record LoginUser(Long userId, UUID publicId) {
}
