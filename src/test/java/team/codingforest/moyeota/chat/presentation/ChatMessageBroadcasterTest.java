package team.codingforest.moyeota.chat.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import team.codingforest.moyeota.chat.application.event.ChatRoomJoinedEvent;
import team.codingforest.moyeota.chat.application.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.config.RedisConfig;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.MemberProvider;
import team.codingforest.moyeota.chat.domain.enums.MemberChangeType;
import team.codingforest.moyeota.chat.infrastructure.MemberChangedPayload;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageBroadcasterTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab");

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock private MemberProvider memberProvider;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private ChatMessageBroadcaster broadcaster;

    @Test
    void 퇴장하면_본인_동기화와_방_알림을_모두_발행한다() {
        given(memberProvider.findMembers(List.of(USER_ID)))
                .willReturn(Map.of(USER_ID, new ChatMember(USER_ID, PUBLIC_ID, "영희", null)));

        broadcaster.onRoomLeft(new ChatRoomLeftEvent(USER_ID, PUBLIC_ID, ROOM_ID));

        verify(redisTemplate, times(2)).convertAndSend(eq(RedisConfig.CHAT_CHANNEL), anyString());
    }
    @Test
    void 참여하면_방_알림에_닉네임이_실린다() {
        given(memberProvider.findMembers(List.of(USER_ID)))
                .willReturn(Map.of(USER_ID, new ChatMember(USER_ID, PUBLIC_ID, "영희", null)));

        broadcaster.onRoomJoined(new ChatRoomJoinedEvent(USER_ID, ROOM_ID));

        ArgumentCaptor<Object> captor = ArgumentCaptor.captor();
        verify(objectMapper, atLeastOnce()).writeValueAsString(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(MemberChangedPayload.class::isInstance)
                .first()
                .satisfies(payload -> {
                    MemberChangedPayload member = (MemberChangedPayload) payload;
                    assertThat(member.nickname()).isEqualTo("영희");
                    assertThat(member.type()).isEqualTo(MemberChangeType.JOINED);
                });
    }

    @Test
    void 퇴장하면_방_알림에_닉네임이_실린다() {
        given(memberProvider.findMembers(List.of(USER_ID)))
                .willReturn(Map.of(USER_ID, new ChatMember(USER_ID, PUBLIC_ID, "영희", null)));

        broadcaster.onRoomLeft(new ChatRoomLeftEvent(USER_ID, PUBLIC_ID, ROOM_ID));

        ArgumentCaptor<Object> captor = ArgumentCaptor.captor();
        verify(objectMapper, atLeastOnce()).writeValueAsString(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(MemberChangedPayload.class::isInstance)
                .first()
                .satisfies(payload -> {
                    MemberChangedPayload member = (MemberChangedPayload) payload;
                    assertThat(member.nickname()).isEqualTo("영희");
                    assertThat(member.type()).isEqualTo(MemberChangeType.LEFT);
                });
    }
}