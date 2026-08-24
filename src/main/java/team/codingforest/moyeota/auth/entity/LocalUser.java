package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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

    /*
    가입한 시각. insert가 나갈 때 Hibernate가 현재 시각을 넣어준다.
    updatable = false 라서 이후 어떤 수정에도 값이 바뀌지 않는다.
    (가입 시각은 한 번 정해지면 바뀌면 안 되는 값이다)

    [이미 돌아가던 DB에 이 컬럼을 추가할 때]
    Hibernate는 @CreationTimestamp 컬럼을 not null로 만들려 하는데,
    행이 이미 있는 테이블에는 not null 컬럼을 그냥 붙일 수 없어서 ddl-auto=update가 조용히 실패한다
    (앱은 뜨지만 컬럼이 없어서 회원가입이 500으로 떨어진다).
    그때는 docs/schema-timestamps.sql 을 한 번 실행해 컬럼을 만들어주면 된다.
    DB를 새로 받는 사람은 create 시점에 그대로 만들어지므로 할 일이 없다.
    */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    //마지막으로 이 행이 바뀐 시각. update가 나갈 때마다 Hibernate가 현재 시각으로 덮어쓴다.
    //insert 때도 함께 채워지므로 가입 직후에는 createdAt과 같은 값이다.
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
