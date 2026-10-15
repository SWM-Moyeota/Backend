package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccessServiceTest {
    private static final String CDN = "https://cdn.example.com/";
    private static final String 기본_이미지_URL = CDN + "profile/presets/default.png";

    private final FakeUsers users = new FakeUsers();
    private final UserAccessService service = new UserAccessService(users, new ProfileImages(
            new ProfileImageProperties(CDN, "profile/presets/default.png", List.of("profile/presets/01.png"))));

    @Test
    void 요청한_유저만_userId_키로_돌려준다() {
        // matching 이 PartyMember.memberId 로 짝을 맞추므로 키는 내부 userId, 값에는 publicId 만 실린다
        User a = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));
        User b = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));

        Map<Long, MemberSummary> result = service.findMemberSummaries(List.of(a.getId(), 999L));

        assertThat(result).containsOnlyKeys(a.getId());
        assertThat(result.get(a.getId()).publicId()).isEqualTo(a.getPublicId());
        assertThat(result).doesNotContainKey(b.getId());
    }

    @Test
    void 닉네임_미설정_유저는_nickname이_null로_나간다() {
        // 소셜 가입 직후(프로필 설정 전) 상태 - 프론트가 null 처리해야 한다
        User a = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));

        assertThat(service.findMemberSummaries(List.of(a.getId())).get(a.getId()).nickname()).isNull();
    }

    @Test
    void 탈퇴한_유저는_탈퇴한_사용자로_보이고_배지를_숨긴다() {
        // 채팅방·합승 상세에서 탈퇴자를 닉네임 미설정(null)과 구분해 보여준다
        User a = users.save(User.restore(null, UUID.randomUUID(), "모여타", "img.png", LoginType.LOCAL, 7L, null, Instant.now(), null));
        a.delete(Instant.now());
        users.save(a);

        MemberSummary summary = service.findMemberSummaries(List.of(a.getId())).get(a.getId());

        assertThat(summary.publicId()).as("메시지 보낸 사람 구분용이라 남긴다").isEqualTo(a.getPublicId());
        assertThat(summary.nickname()).isEqualTo(User.DELETED_DISPLAY_NAME);
        assertThat(summary.imageUrl()).as("고른 이미지는 지우고 기본 이미지로 보인다").isEqualTo(기본_이미지_URL);
        assertThat(summary.badgeId()).isNull();
    }

    @Test
    void 고른_이미지는_키가_아니라_전체_URL_로_나간다() {
        // DB 에는 키만 저장한다. 다른 도메인은 URL 을 조합할 필요 없이 그대로 그린다
        User a = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "모여타"));
        a.updateProfile(null, "profile/presets/01.png");
        users.save(a);

        assertThat(service.findMemberSummaries(List.of(a.getId())).get(a.getId()).imageUrl())
                .isEqualTo(CDN + "profile/presets/01.png");
    }

    @Test
    void 이미지를_고른_적_없으면_기본_이미지_URL_이_나간다() {
        User a = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "모여타"));

        assertThat(service.findMemberSummaries(List.of(a.getId())).get(a.getId()).imageUrl()).isEqualTo(기본_이미지_URL);
    }

    @Test
    void 탈퇴한_유저의_닉네임_조회도_탈퇴한_사용자다() {
        User a = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "모여타"));
        a.delete(Instant.now());
        users.save(a);

        assertThat(service.findNickname(a.getId())).contains(User.DELETED_DISPLAY_NAME);
    }

    @Test
    void 빈_목록이면_빈_결과다() {
        assertThat(service.findMemberSummaries(List.of())).isEmpty();
    }
}
