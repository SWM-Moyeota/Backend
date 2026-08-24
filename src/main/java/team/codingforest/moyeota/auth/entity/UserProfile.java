package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import team.codingforest.moyeota.auth.entity.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/*
사용자의 프로필. user 테이블의 user_id를 그대로 자기 PK로 쓴다(공유 기본키).

소셜 로그인으로 받아오는 값(name, email)이 여기에 들어간다.
로그인할 때마다 소셜 계정의 최신 값으로 덮어쓰므로 사용자가 직접 고치는 값은 아니다.
사용자가 고치는 이름은 User.nickname 쪽이다.

[주의] gender는 team.codingforest.moyeota.domain.entity.enums.Gender 여야 한다.
com.nimbusds.openid.connect.sdk.claims.Gender 는 enum이 아니라 클래스여서
@Enumerated를 붙이면 EntityManagerFactory 생성이 실패하고 앱이 아예 뜨지 않는다.
*/
@Entity
@Getter
@Setter
public class UserProfile {

    //@GeneratedValue를 붙이지 않는다. 이 값은 스스로 만드는 것이 아니라
    //아래 @MapsId가 user에서 복사해 오는 값이기 때문이다.
    @Id
    private Long userId;

    //@MapsId : user_id 컬럼 하나가 PK이자 FK 역할을 동시에 한다.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private UUID passCi; //고유식별

    //실명. 본인 확인용이라 남에게 보여주는 값이 아니다.
    //다른 사용자에게 보이는 이름은 User.nickname 쪽이다.
    private String name;

    //소셜 로그인에서 받은 이메일
    private String email;

    private LocalDate birthDate;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    /*
    이 프로필이 만들어진 시각 = 가입한 시각. insert가 나갈 때 Hibernate가 현재 시각을 넣어준다.
    updatable = false 라서 이후 어떤 수정에도 값이 바뀌지 않는다.

    이미 돌아가던 DB에 이 컬럼을 추가할 때 주의할 점은 LocalUser.createdAt 쪽에 적어두었다.
    */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    //마지막으로 이 행이 바뀐 시각. update가 나갈 때마다 Hibernate가 현재 시각으로 덮어쓴다.
    //소셜 로그인은 로그인할 때마다 name/email을 덮어쓰므로 그때도 이 값이 갱신된다.
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
