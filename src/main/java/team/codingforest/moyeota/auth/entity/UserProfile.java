package team.codingforest.moyeota.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import team.codingforest.moyeota.auth.entity.enums.Gender;

import java.time.LocalDate;
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

    private String age;

    private LocalDate birthDate;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Gender gender;
}
