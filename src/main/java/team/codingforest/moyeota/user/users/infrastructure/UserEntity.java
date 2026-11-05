package team.codingforest.moyeota.user.users.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanction;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import java.time.Instant;
import java.util.UUID;

// no_show_count·no_show_count_updated_at(V27)은 테이블에 남아 있지만 매핑하지 않는다 - 노쇼 횟수는 user_no_show 에서 센다. 컬럼은 다음 릴리스에서 지운다
// 바뀐 컬럼만 UPDATE 한다 - 프로필 편집과 노쇼 제재가 같은 행을 따로 고쳐도, 한쪽이 읽어 둔 옛 값으로 다른 쪽 컬럼을 덮어쓰지 않게
@DynamicUpdate
@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")   // "user" 는 PostgreSQL·H2 예약어라 insert 자체가 실패한다
public class UserEntity extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 필터가 매 요청 publicId 로 조회하므로 unique 인덱스 필요
    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId;

    // null 허용 - 소셜 가입 직후는 비어 있다. PostgreSQL/H2 모두 null 끼리는 unique 충돌 없음
    @Column(unique = true)
    private String nickname;

    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoginType loginType;

    private String fcmToken;

    // null 이면 정상 회원. 탈퇴해도 행은 남긴다 (soft delete)
    private Instant deletedAt;

    // 노쇼 제재(V27). 바꾸는 길은 NoShowSanctionJpa 하나뿐이고 update(User) 는 건드리지 않는다
    private Instant restrictedUntil;

    private UserEntity(UUID publicId, String nickname, String imageUrl, LoginType loginType, String fcmToken) {
        this.publicId = publicId;
        this.nickname = nickname;
        this.imageUrl = imageUrl;
        this.loginType = loginType;
        this.fcmToken = fcmToken;
    }

    public static UserEntity from(User user) {
        return new UserEntity(user.getPublicId(), user.getNickname(), user.getImageUrl(), user.getLoginType(), user.getFcmToken());
    }

    public void update(User user) {
        this.nickname = user.getNickname();
        this.imageUrl = user.getImageUrl();
        this.fcmToken = user.getFcmToken();
        this.deletedAt = user.getDeletedAt();
    }

    public NoShowSanction noShowSanction() {
        return new NoShowSanction(restrictedUntil);
    }

    public void applyNoShowSanction(NoShowSanction sanction) {
        this.restrictedUntil = sanction.restrictedUntil();
    }

    public User toDomain() {
        return User.restore(
                getId(),
                publicId,
                nickname,
                imageUrl,
                loginType,
                fcmToken,
                getUpdatedAt(),
                deletedAt
        );
    }

}