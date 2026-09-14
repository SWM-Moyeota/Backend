package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.application.dto.ChatLocationResult;
import team.codingforest.moyeota.chat.domain.ChatLocation;
import team.codingforest.moyeota.chat.domain.ChatLocationPublisher;
import team.codingforest.moyeota.chat.domain.ChatLocations;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomStatus;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.chat.domain.MemberProvider;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatLocationServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long PARTY_ID = 100L;
    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final UUID PUBLIC_ID = UUID.randomUUID();
    private static final UUID OTHER_PUBLIC_ID = UUID.randomUUID();

    @Mock
    private ChatLocations chatLocations;
    @Mock
    private ChatRooms chatRooms;
    @Mock
    private PartyProvider partyProvider;
    @Mock
    private MemberProvider memberProvider;
    @Mock
    private ChatLocationPublisher locationPublisher;
    @InjectMocks
    private ChatLocationService chatLocationService;

    private ChatRoom room() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        return ChatRoom.restore(ROOM_ID, PARTY_ID, "서울시청", "강남역", now, now, ChatRoomStatus.ACTIVE);
    }

    private ChatLocation location() {
        return new ChatLocation(37.5665, 126.9780, Instant.now());
    }

    private void givenActiveMember() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(partyProvider.isActiveMember(USER_ID, PARTY_ID)).willReturn(true);
    }

    @Test
    void 공유를_시작하면_세션이_생성된다() {
        givenActiveMember();

        chatLocationService.startSharing(USER_ID, ROOM_ID);

        then(chatLocations).should().startSession(USER_ID, ROOM_ID);
    }

    @Test
    void 파티원이_아니면_공유를_시작할_수_없다() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(room()));
        given(partyProvider.isActiveMember(USER_ID, PARTY_ID)).willReturn(false);

        assertThatThrownBy(() -> chatLocationService.startSharing(USER_ID, ROOM_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTY_MEMBER);

        then(chatLocations).should(never()).startSession(anyLong(), anyLong());
    }

    @Test
    void 없는_방이면_파티를_조회하지_않는다() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatLocationService.startSharing(USER_ID, ROOM_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);

        then(partyProvider).should(never()).isActiveMember(anyLong(), anyLong());
    }

    @Test
    void 신선한_좌표는_저장하고_브로드캐스트한다() {
        givenActiveMember();
        given(chatLocations.isSharing(USER_ID, ROOM_ID)).willReturn(true);
        given(chatLocations.put(eq(USER_ID), eq(ROOM_ID), any(ChatLocation.class))).willReturn(true);

        ChatLocation location = location();

        chatLocationService.share(USER_ID, PUBLIC_ID, ROOM_ID, location);

        then(locationPublisher).should().publish(ROOM_ID, PUBLIC_ID, location);
    }

    @Test
    void 낡은_좌표는_브로드캐스트하지_않는다() {
        givenActiveMember();
        given(chatLocations.isSharing(USER_ID, ROOM_ID)).willReturn(true);
        given(chatLocations.put(eq(USER_ID), eq(ROOM_ID), any(ChatLocation.class))).willReturn(false);

        chatLocationService.share(USER_ID, PUBLIC_ID, ROOM_ID, location());

        then(locationPublisher).should(never()).publish(anyLong(), any(UUID.class), any(ChatLocation.class));
    }

    @Test
    void 토글을_켜지_않으면_발행할_수_없다() {
        givenActiveMember();
        given(chatLocations.isSharing(USER_ID, ROOM_ID)).willReturn(false);

        assertThatThrownBy(() -> chatLocationService.share(USER_ID, PUBLIC_ID, ROOM_ID, location()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTY_MEMBER);

        then(chatLocations).should(never()).put(anyLong(), anyLong(), any(ChatLocation.class));
        then(locationPublisher).should(never()).publish(anyLong(), any(UUID.class), any(ChatLocation.class));
    }

    @Test
    void 공유_중단은_파티_검증_없이_동작한다() {
        chatLocationService.stopSharing(USER_ID, ROOM_ID);

        then(chatLocations).should().stop(USER_ID, ROOM_ID);
        then(chatRooms).should(never()).findById(anyLong());
        then(partyProvider).should(never()).isActiveMember(anyLong(), anyLong());
    }

    @Test
    void 낡은_좌표는_스냅샷에서_제외된다() {
        givenActiveMember();

        ChatLocation fresh = new ChatLocation(37.5665, 126.9780, Instant.now());
        ChatLocation stale = new ChatLocation(37.4979, 127.0276, Instant.now().minus(Duration.ofMinutes(10)));

        given(chatLocations.findAll(ROOM_ID)).willReturn(Map.of(USER_ID, fresh, OTHER_USER_ID, stale));
        given(memberProvider.findMembers(any())).willReturn(Map.of(
                USER_ID, new ChatMember(USER_ID, PUBLIC_ID, "길동이", null),
                OTHER_USER_ID, new ChatMember(OTHER_USER_ID, OTHER_PUBLIC_ID, "영희", null)));

        List<ChatLocationResult> results = chatLocationService.findAll(USER_ID, ROOM_ID);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().publicId()).isEqualTo(PUBLIC_ID);
        assertThat(results.getFirst().latitude()).isEqualTo(37.5665);
    }

    @Test
    void 좌표가_없으면_회원을_조회하지_않는다() {
        givenActiveMember();
        given(chatLocations.findAll(ROOM_ID)).willReturn(Map.of());

        assertThat(chatLocationService.findAll(USER_ID, ROOM_ID)).isEmpty();

        then(memberProvider).should(never()).findMembers(any());
    }

    @Test
    void 조회되지_않는_회원의_좌표는_제외된다() {
        givenActiveMember();

        given(chatLocations.findAll(ROOM_ID)).willReturn(Map.of(USER_ID, location()));
        given(memberProvider.findMembers(any())).willReturn(Map.of());

        assertThat(chatLocationService.findAll(USER_ID, ROOM_ID)).isEmpty();
    }
}
