package team.codingforest.moyeota.user.profile.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.users.domain.enums.Gender;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MaskedRealNameServiceUnitTest {
    private static final Instant BIRTH = Instant.parse("2000-01-01T00:00:00Z");

    @Mock
    private UserProfiles userProfiles;
    @InjectMocks
    private MaskedRealNameService service;

    @DisplayName("find - 실명을 가려 userId 별로 돌려준다(2자는 뒤를, 3자 이상은 가운데를 가린다)")
    @Test
    void find() {

        /*
         * settings
         */
        doReturn(List.of(profile(1L, "김민수"), profile(2L, "이훈"), profile(3L, "남궁민수")))
                .when(userProfiles).findAllByUserIds(eq(List.of(1L, 2L, 3L)));

        /*
         * test
         */
        Map<Long, String> names = service.find(List.of(1L, 2L, 3L));

        /*
         * verify
         */
        assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "김*수", 2L, "이*", 3L, "남**수"));
    }

    @DisplayName("find - 프로필이 없는 사용자(탈퇴 등)는 결과에서 빠진다")
    @Test
    void find_missingProfile() {

        /*
         * settings
         */
        doReturn(List.of(profile(1L, "김민수"))).when(userProfiles).findAllByUserIds(eq(List.of(1L, 9L)));

        /*
         * test
         */
        Map<Long, String> names = service.find(List.of(1L, 9L));

        /*
         * verify
         */
        assertThat(names).containsOnlyKeys(1L);
    }

    @DisplayName("find - 빈 목록이면 조회하지 않고 빈 맵")
    @Test
    void find_empty() {

        /*
         * test
         */
        Map<Long, String> names = service.find(List.of());

        /*
         * verify
         */
        assertThat(names).isEmpty();
        verifyNoInteractions(userProfiles);
    }

    private static UserProfile profile(Long userId, String name) {
        return UserProfile.restore(userId, name, BIRTH, "0101234" + String.format("%04d", userId), Gender.MALE, userId + "@example.com", BIRTH, BIRTH);
    }
}
