package team.codingforest.moyeota.chat.common.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import team.codingforest.moyeota.chat.contract.ChatChannels;
import team.codingforest.moyeota.chat.contract.ChatEventEnvelope;
import team.codingforest.moyeota.chat.member.domain.ChatMember;
import team.codingforest.moyeota.chat.member.domain.enums.MemberChangeType;
import team.codingforest.moyeota.chat.member.infrastructure.MemberChangedPayload;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRedisPublisherTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab");
    private static final ChatMember MEMBER = new ChatMember(USER_ID, PUBLIC_ID, "영희", null);

    @Mock
    private StringRedisTemplate redisTemplate;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private ChatRedisPublisher publisher;

    @Test
    void 퇴장하면_본인_동기화와_방_알림을_모두_발행한다() {
        publisher.memberLeft(ROOM_ID, USER_ID, PUBLIC_ID, MEMBER);

        verify(redisTemplate, times(2)).convertAndSend(eq(ChatChannels.CHAT), anyString());
    }

    @Test
    void 퇴장하면_본인_목적지와_구독_정리_정보를_담아_발행한다() {
        publisher.memberLeft(ROOM_ID, USER_ID, PUBLIC_ID, MEMBER);

        ArgumentCaptor<String> captor = ArgumentCaptor.captor();
        verify(redisTemplate, times(2)).convertAndSend(eq(ChatChannels.CHAT), captor.capture());
        ChatEventEnvelope roomLeft = objectMapper.readValue(captor.getAllValues().getFirst(), ChatEventEnvelope.class);

        assertThat(roomLeft.type()).isEqualTo(ChatEventEnvelope.TYPE_ROOM_LEFT);
        assertThat(roomLeft.destination()).isEqualTo("/user/7/queue/room-left");
        assertThat(roomLeft.userId()).isEqualTo(USER_ID);
        assertThat(roomLeft.chatRoomId()).isEqualTo(ROOM_ID);
    }

    @Test
    void 참여하면_방_멤버_목적지로_발행한다() {
        publisher.memberJoined(ROOM_ID, MEMBER);

        ArgumentCaptor<String> captor = ArgumentCaptor.captor();
        verify(redisTemplate).convertAndSend(eq(ChatChannels.CHAT), captor.capture());
        ChatEventEnvelope envelope = objectMapper.readValue(captor.getValue(), ChatEventEnvelope.class);

        assertThat(envelope.destination()).isEqualTo("/sub/chat-rooms/10/members");
    }

    @Test
    void 참여하면_방_알림에_닉네임이_실린다() {
        publisher.memberJoined(ROOM_ID, MEMBER);

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
        publisher.memberLeft(ROOM_ID, USER_ID, PUBLIC_ID, MEMBER);

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