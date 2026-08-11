package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import team.codingforest.moyeota.auth.entity.enums.LoginType;
import team.codingforest.moyeota.auth.entity.enums.Role;

import java.util.UUID;

/*
사용자의 "본체" 테이블.
로그인 수단(social_user / local_user)과 프로필(user_profile)은 자기 PK를 따로 만들지 않고
이 테이블의 user_id를 그대로 가져다 쓴다(공유 기본키). 그래서 세 테이블의 user_id는 항상 같은 값이다.

주의: user는 PostgreSQL 예약어라 따옴표 없이는 create table이 실패한다.
백틱으로 감싸두면 Hibernate가 DB에 맞는 따옴표("user")를 붙여준다.
psql에서 직접 조회할 때도 select * from "user"; 처럼 따옴표가 필요하다.
*/
@Entity
@Table(name = "`user`")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    //밖(토큰·API 응답)으로 내보내는 식별자.
    //userId(1,2,3...)를 노출하면 가입자 수가 새어나가고 남의 id를 추측할 수 있어서
    //추측이 불가능한 UUID를 따로 둔다.
    @Column(unique = true, nullable = false, updatable = false)
    private UUID publicId;

    //다른 사용자에게 보이는 이름. 가입할 때 NicknameGenerator가 무작위로 채워주고,
    //이후 사용자가 마이페이지에서 바꿀 수 있다.
    //실명은 여기가 아니라 user_profile.name에 있다. 그쪽은 본인 확인용이라 밖으로 내보내지 않는다.
    private String nickname;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private LoginType loginType;

    @Enumerated(EnumType.STRING)
    private Role role;
}
