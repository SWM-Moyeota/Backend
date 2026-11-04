package team.codingforest.moyeota.user.deletion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;
import team.codingforest.moyeota.user.auth.application.AuthService;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.deletion.application.UserDeletionService;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.Gender;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 *  탈퇴 한 번으로 user·auth·sms·chat·place 가 모두 정리되는지 실제 DB·Redis·비동기 리스너로 본다
 *  chat·place 정리는 커밋 후 @ApplicationModuleListener(@Async) 라 await 로 기다린다
 */
@SpringBootTest
class UserDeletionFlowIntegrationTest {
    private static final String PASSWORD = "password1!";

    @Autowired UserDeletionService userDeletionService;
    @Autowired Users users;
    @Autowired LocalUsers localUsers;
    @Autowired UserProfiles userProfiles;
    @Autowired PasswordHasher passwordHasher;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ChatRooms chatRooms;
    @Autowired ChatRoomUsers chatRoomUsers;
    @Autowired AuthService authService;
    @Autowired JwtProvider jwtProvider;
    @Autowired SmsVerificationService smsVerificationService;

    @Test
    void 탈퇴하면_로그인_토큰_재가입_채팅방_즐겨찾기가_모두_정리된다() {
        long base = System.currentTimeMillis() % 100_000_000L;
        String phone = "010" + String.format("%08d", base);
        Instant now = Instant.now();

        User user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "del" + base % 1_000_000));
        localUsers.register(user.getId(), "del" + base, passwordHasher.hash(PASSWORD));
        userProfiles.save(UserProfile.of(user.getId(), "홍길동", now, phone, Gender.MALE, "del@moyeota.app"));
        // FavoritePlaceId 에 기본 생성자가 없어 JPA save 가 실패한다(별도 버그) - 준비 데이터는 SQL 로 넣는다
        jdbcTemplate.update("insert into favorite_place (user_id, place_name, road_name, latitude, longitude, place_sequence, created_at) values (?, '집', '테헤란로', 37.5, 127.0, 1, now())", user.getId());
        // 끝난 합승 방에 남아 있는 상황 - 진행 중인 합승은 없어서 탈퇴가 막히지 않는다
        ChatRoom room = chatRooms.save(ChatRoom.create(800_000_000L + base, "강남역", "판교역", now));
        chatRoomUsers.save(ChatRoomUser.join(user.getId(), room.getId(), now));
        String accessToken = jwtProvider.issuePair(user.getPublicId(), UUID.randomUUID(), now).access();
        authService.authenticate(accessToken);   // 탈퇴 전 요청으로 인증 캐시에 올라가 있다

        userDeletionService.delete(user.getId(), PASSWORD);

        assertThat(users.findById(user.getId()).isDeleted()).isTrue();
        assertThat(localUsers.findByUserId(user.getId())).as("다시 로그인할 수 없다").isEmpty();
        assertThat(userProfiles.findByUserId(user.getId())).isEmpty();
        assertThatThrownBy(() -> authService.authenticate(accessToken))
                .as("만료 전 access 토큰도 캐시가 비워져 바로 막힌다")
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.TOKEN_INVALID);
        assertThatThrownBy(() -> smsVerificationService.send(phone))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.REJOIN_RESTRICTED);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(chatRoomUsers.findActiveByUserId(user.getId())).as("끝난 합승 방에서도 나간다").isEmpty();
            assertThat(jdbcTemplate.queryForObject("select count(*) from favorite_place where user_id = ?", Integer.class, user.getId())).isZero();
        });
    }
}
