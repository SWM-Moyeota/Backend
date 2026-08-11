package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/*
로컬(아이디/비밀번호) 로그인 수단. SocialUser와 짝을 이루는 테이블이다.
"이 사용자의 로그인 아이디와 비밀번호는 무엇인가"만 담는다.
이름·나이·이메일 같은 사람 정보는 user_profile이 갖는다.

login_id에 unique 제약을 건다.
로그인은 login_id로 사용자를 찾으므로 중복 행이 생기면 같은 아이디가 두 사람이 되어버린다.
동시에 들어온 회원가입 요청은 애플리케이션 코드("조회 -> 없으면 저장")만으로는 막을 수 없고,
이 제약이 있어야 DB가 두 번째 insert를 실패시켜준다.
*/
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_local_user_login_id",
        columnNames = "login_id"))
@Getter
@Setter
public class LocalUser {

    //user 테이블의 PK를 그대로 자기 PK로 쓴다(공유 기본키).
    //@GeneratedValue가 없는 이유는 이 값을 스스로 만들지 않고 아래 @MapsId가 user에서 복사해 오기 때문이다.
    @Id
    private Long userId;

    //@MapsId : user_id 컬럼 하나가 PK이자 FK 역할을 동시에 한다.
    //setUser(user)만 해주면 userId는 JPA가 알아서 채운다.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String loginId;

    //BCrypt 해시가 들어간다. 평문은 절대 저장하지 않는다.
    //해시 길이는 60자로 고정이지만, 나중에 알고리즘을 바꿀 수 있으므로 기본 255를 그대로 둔다.
    @Column(nullable = false)
    private String password;
}
