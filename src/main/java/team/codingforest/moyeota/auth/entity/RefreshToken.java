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

[PK를 자체 id로 둔다]
예전에는 user_id를 그대로 PK로 쓰는 공유 기본키(@MapsId)였다.
그 구조는 한 사용자당 행이 물리적으로 하나뿐이라, 나중에 기기별 로그인을 허용하려면
PK를 바꾸는 마이그레이션이 필요했다. 지금은 자체 id를 두고 user_id는 FK로만 둔다.
local_user / social_user / user_profile은 사용자와 1:1로 고정된 정보라 공유 기본키가 맞지만,
refresh 토큰은 "로그인 한 번"마다 생기는 기록이라 성격이 다르다.

[다만 지금은 여전히 사용자당 하나다]
아래 user_id에 unique 제약을 걸어 기존 동작을 그대로 유지한다.
다른 기기에서 로그인하면 그 행이 새 토큰으로 덮어써지고, 먼저 쓰던 기기는 재발급에 실패한다.
여러 기기 동시 로그인을 허용하려면 이 unique 제약(uk_refresh_token_user_id)만 떼면 되고,
그때 public_id의 unique도 함께 떼야 한다(같은 사용자의 행이 여럿이면 값이 겹치므로).
*/
@Entity
@Table(uniqueConstraints = {
        //사용자당 살아 있는 refresh는 하나. PK가 user_id였을 때 공짜로 보장되던 것을 명시적으로 옮겨온 것이다.
        @UniqueConstraint(name = "uk_refresh_token_user_id", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_refresh_token_public_id", columnNames = "public_id")})
@Getter
@Setter
public class RefreshToken {

    //이 행 자체의 식별자. 사용자와 무관하게 DB가 만들어준다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
    이 토큰의 주인.

    @ManyToOne인 이유는 위 unique 제약을 떼는 순간 "한 사용자 : 여러 토큰"이 되기 때문이다.
    지금은 제약 덕분에 1:1처럼 동작하지만, 연관관계 자체는 여러 행을 담을 수 있는 쪽으로 열어둔다.
    (@OneToOne으로 두면 나중에 제약을 뗄 때 매핑까지 같이 고쳐야 한다)

    LAZY인 이유는 토큰을 다루는 대부분의 경로에서 User 본체가 필요 없기 때문이다.
    재발급은 refresh 문자열만 대조하고, 로그아웃은 그 행을 지우기만 한다.
    */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /*
    이 행의 주인인 사용자의 publicId. user.public_id와 항상 같은 값이다.

    같은 값을 두 테이블에 두는 것이므로 원래는 user를 타고 가면 되는 값이다(getUser().getPublicId()).
    그럼에도 여기에 두는 이유는 토큰을 다룰 때 손에 쥐고 있는 것이 userId가 아니라 publicId이기 때문이다.
    JWT의 subject가 publicId라서, refresh를 publicId로 찾거나 지우려면 매번 user를 먼저 조회해야 한다.
    이 컬럼이 있으면 그 조회 없이 refresh_token만 보고 처리할 수 있다.

    updatable=false를 걸지 않은 이유:
    이 값이 비어 있는 옛 행도 issue()가 매번 다시 넣어주면 다음 로그인 때 저절로 채워진다.
    user_id와 public_id의 짝은 바뀌지 않으므로(User.publicId가 updatable=false다) 몇 번을 덮어써도 같은 값이다.
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
