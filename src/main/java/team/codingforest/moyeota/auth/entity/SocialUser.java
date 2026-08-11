package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import team.codingforest.moyeota.auth.entity.enums.SocialType;

/*
소셜 로그인 수단. "이 사용자는 구글의 어느 계정인가"만 담는다.

(social_type, social_id) 조합에 unique 제약을 건다.
로그인은 이 조합으로 사용자를 찾으므로 중복 행이 생기면 같은 구글 계정이 두 사람이 되어버린다.
동시에 들어온 로그인 요청은 애플리케이션 코드("조회 -> 없으면 저장")만으로는 막을 수 없고,
이 제약이 있어야 DB가 두 번째 insert를 실패시켜준다.
*/
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_social_user_type_social_id",
        columnNames = {"social_type", "social_id"}))
@Getter
@Setter
public class SocialUser {

    //user 테이블의 PK를 그대로 자기 PK로 쓴다.
    //@GeneratedValue가 없는 이유는 이 값을 스스로 만들지 않고 아래 @MapsId가 user에서 복사해 오기 때문이다.
    @Id
    private Long userId;

    //@MapsId : user_id 컬럼 하나가 PK이자 FK 역할을 동시에 한다.
    //setUser(user)만 해주면 userId는 JPA가 알아서 채운다.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    //구글이 준 sub 값. 이메일과 달리 바뀌지 않아서 계정 식별자로 쓸 수 있다.
    @Column(nullable = false)
    private String socialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialType socialType;
}
