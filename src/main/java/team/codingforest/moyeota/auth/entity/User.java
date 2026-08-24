package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import team.codingforest.moyeota.auth.entity.enums.LoginType;

import java.time.LocalDateTime;
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

    /*
    프로필에 내걸어 둔 대표 뱃지. 가진 뱃지 중 하나를 사용자가 골라 둔 것이다.

    null 이 정상값이다. 가입 직후에는 딴 뱃지가 하나도 없으니 고를 수도 없고,
    뱃지를 가진 뒤에도 "아무것도 안 걸어둔 상태"를 고를 수 있어야 하기 때문이다.
    그래서 이 값을 읽는 쪽은 항상 null 을 함께 다뤄야 한다.

    지금은 badge 테이블이 없어서 @ManyToOne 이 아니라 id 값만 들고 있다.
    badge 엔티티가 생기면 이 필드를 연관관계로 바꾸고 FK 제약을 걸어주는 것이 맞다.
    (그전까지는 없는 뱃지 id 가 들어가도 DB가 막아주지 못한다)
    */
    private Long badgeId;

    /*
    마지막으로 이 행이 바뀐 시각. update가 나갈 때마다 Hibernate가 현재 시각으로 덮어쓴다.

    insert 때도 함께 채워지므로 회원가입한 순간부터 값이 들어가 있다.
    (@UpdateTimestamp는 "수정될 때만" 채우는 것이 아니라 insert/update 양쪽에서 채운다.
     그래서 가입만 하고 아무것도 안 고친 사용자도 이 값이 null 이 아니다)

    user 테이블에는 created_at 이 따로 없다. 가입 시각은 user_profile.createdAt 쪽에 있다.

    이미 돌아가던 DB에 이 컬럼을 추가할 때 주의할 점은 LocalUser.createdAt 쪽에 적어두었다.
    이 컬럼은 docs/schema-user-badge-updated-at.sql 로 추가하면 된다.
    */
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
