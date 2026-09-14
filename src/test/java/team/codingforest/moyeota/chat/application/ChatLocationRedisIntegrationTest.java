package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import team.codingforest.moyeota.chat.domain.ChatLocation;
import team.codingforest.moyeota.chat.domain.ChatLocations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ChatLocationRedisIntegrationTest {

    @Autowired
    ChatLocations chatLocations;
    @Autowired
    StringRedisTemplate redisTemplate;

    private static final Long ROOM_ID = 999_999L;
    private static final Long USER_ID = 7L;

    @AfterEach
    void clean() {
        redisTemplate.delete("chat:location:" + ROOM_ID);
        redisTemplate.delete("chat:sharing:" + ROOM_ID + ":" + USER_ID);
    }

    @Test
    void 저장한_좌표를_그대로_읽는다() {
        ChatLocation location = new ChatLocation(37.5665, 126.9780, Instant.now());

        assertThat(chatLocations.put(USER_ID, ROOM_ID, location)).isTrue();

        assertThat(chatLocations.findAll(ROOM_ID).get(USER_ID))
                .isEqualTo(location);   // Instant 직렬화가 깨지면 여기서 실패
    }

    @Test
    void 지연_도착한_과거_좌표는_덮어쓰지_않는다() {
        Instant now = Instant.now();
        ChatLocation newer = new ChatLocation(37.5665, 126.9780, now);
        ChatLocation older = new ChatLocation(99.0, 99.0, now.minusSeconds(60));

        chatLocations.put(USER_ID, ROOM_ID, newer);

        assertThat(chatLocations.put(USER_ID, ROOM_ID, older)).isFalse();
        assertThat(chatLocations.findAll(ROOM_ID).get(USER_ID)).isEqualTo(newer);
    }

    @Test
    void 세션은_생성하면_존재하고_중단하면_사라진다() {
        chatLocations.startSession(USER_ID, ROOM_ID);
        assertThat(chatLocations.isSharing(USER_ID, ROOM_ID)).isTrue();

        chatLocations.stop(USER_ID, ROOM_ID);
        assertThat(chatLocations.isSharing(USER_ID, ROOM_ID)).isFalse();
    }

    @Test
    void 중단하면_좌표도_함께_지운다() {
        chatLocations.startSession(USER_ID, ROOM_ID);
        chatLocations.put(USER_ID, ROOM_ID, new ChatLocation(37.5665, 126.9780, Instant.now()));

        chatLocations.stop(USER_ID, ROOM_ID);

        assertThat(chatLocations.findAll(ROOM_ID)).isEmpty();
    }
}