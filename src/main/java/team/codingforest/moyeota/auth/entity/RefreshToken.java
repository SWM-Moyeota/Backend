package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/*
재발급용 refresh 토큰의 보관소.

JWT는 서명만 맞으면 서버가 취소할 수 없다.
그래서 "아직 살아 있는 refresh인가"를 따로 기록해두고 재발급 때 대조한다.
로그아웃은 이 행을 지우는 것으로 처리한다(= 더 이상 재발급되지 않는다).

user_id를 PK로 쓴다(공유 기본키). local_user / social_user / user_profile과 같은 방식이다.
그 결과 한 사용자당 행이 최대 하나이므로, 동시에 살아 있는 로그인 세션도 하나다.
다른 기기에서 로그인하면 이 행이 덮어써지고 먼저 쓰던 기기는 재발급에 실패한다.
여러 기기 동시 로그인을 허용하려면 PK를 자체 id로 돌리고 user_id는 FK로만 두어야 한다.
*/
@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_refresh_token_public_id",
        columnNames = "public_id"))
@Getter
@Setter
public class RefreshToken {

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

    /*
    이 행의 주인인 사용자의 publicId. user.public_id와 항상 같은 값이다.

    같은 값을 두 테이블에 두는 것이므로 원래는 user를 타고 가면 되는 값이다(getUser().getPublicId()).
    그럼에도 여기에 두는 이유는 토큰을 다룰 때 손에 쥐고 있는 것이 userId가 아니라 publicId이기 때문이다.
    JWT의 subject가 publicId라서, refresh를 publicId로 찾거나 지우려면 매번 user를 먼저 조회해야 한다.
    이 컬럼이 있으면 그 조회 없이 refresh_token만 보고 처리할 수 있다.

    값이 채워지는 시점은 이 행이 처음 만들어질 때, 즉 TokenService.issue()가 부르는 순간이다.
    지금 구조에서는 회원가입이 곧바로 issue()를 부르므로 가입하는 순간 refresh_token 행과 함께 채워진다.

    updatable=false를 걸지 않은 이유:
    이 변경 전에 만들어진 행은 이 값이 비어 있는데, issue()가 매번 다시 넣어주면 다음 로그인 때 저절로 채워진다.
    user_id와 public_id의 짝은 바뀌지 않으므로(User.publicId가 updatable=false다) 몇 번을 덮어써도 같은 값이다.

    unique 제약을 거는 이유:
    user_id가 PK라 사용자당 행이 하나뿐이므로 public_id도 당연히 유일해야 한다.
    코드가 실수로 남의 publicId를 넣는 일을 DB가 막아주고, 덤으로 이 컬럼으로 찾을 때 쓸 인덱스가 생긴다.
    */
    @Column(nullable = false)
    private UUID publicId;

    //JWT 문자열 전체를 그대로 담는다. 재발급 때 이 값과 문자열이 같은지로 유효성을 판단한다.
    //기본 255로는 모자라므로 512로 잡는다(현재 발급되는 토큰은 230자 안팎).
    @Column(length = 512, nullable = false)
    private String refreshToken;

    private String expiration;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    //마지막으로 이 행이 바뀐 시각. update가 나갈 때마다 Hibernate가 현재 시각으로 덮어쓴다.
    //재발급으로 토큰이 회전될 때마다 갱신되므로 "마지막 재발급 시각"으로 읽으면 된다.
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
