package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import team.codingforest.moyeota.chat.application.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.application.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.application.dto.ReadChatCommand;
import team.codingforest.moyeota.chat.application.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageStatus;
import team.codingforest.moyeota.chat.domain.ChatMessageType;
import team.codingforest.moyeota.chat.domain.ChatMessages;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomStatus;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.chat.domain.MemberProvider;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomUserServiceTest {
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Long PARTY_ID = 100L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab");
    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    private ChatRoomUsers chatRoomUsers;
    private ChatRooms chatRooms;
    private ChatMessages chatMessages;
    private ChatRoomUserService chatRoomUserService;
    private MemberProvider memberProvider;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        chatRoomUsers = mock(ChatRoomUsers.class);
        chatRooms = mock(ChatRooms.class);
        chatMessages = mock(ChatMessages.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        memberProvider = mock(MemberProvider.class);
        chatRoomUserService = new ChatRoomUserService(eventPublisher, chatRoomUsers, chatRooms, chatMessages, memberProvider);
    }

    private ChatRoom room(ChatRoomStatus status) {
        return ChatRoom.restore(ROOM_ID, PARTY_ID, "서울시청", "강남역", NOW, NOW, status);
    }

    private ChatRoomUser activeUser(Long chatRoomId) {
        return ChatRoomUser.join(USER_ID, chatRoomId, NOW);
    }

    @Test
    void 채팅방_참여_성공() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.empty());

        chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID, PUBLIC_ID));

        verify(chatRoomUsers).save(any(ChatRoomUser.class));
    }

    @Test
    void 이미_참여중이면_예외() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID))
                .willReturn(Optional.of(activeUser(ROOM_ID)));

        assertThatThrownBy(() -> chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID, PUBLIC_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED);
    }

    @Test
    void 없는_방_참여_예외() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID, PUBLIC_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 종료된_방_참여_예외() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.CLOSED)));

        assertThatThrownBy(() -> chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID, PUBLIC_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void 채팅방_나가기_성공() {
        ChatRoomUser user = activeUser(ROOM_ID);
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.of(user));

        chatRoomUserService.leave(new ChatRoomCommand(ROOM_ID, USER_ID, PUBLIC_ID));

        assertThat(user.hasLeft()).isTrue();
        verify(chatRoomUsers).save(user);
    }

    @Test
    void 참여자가_아니면_나가기_예외() {
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomUserService.leave(new ChatRoomCommand(ROOM_ID, USER_ID, PUBLIC_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void 읽음_처리_성공() {
        ChatRoomUser user = activeUser(ROOM_ID);
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.of(user));

        chatRoomUserService.read(new ReadChatCommand(USER_ID, ROOM_ID, 5L));

        assertThat(user.getLastReadMessageId()).isEqualTo(5L);
        verify(chatRoomUsers).save(user);
    }

    @Test
    void 참여중인_방_목록_조회() {
        given(chatRoomUsers.findActiveByUserId(USER_ID))
                .willReturn(List.of(activeUser(10L), activeUser(20L)));

        List<ChatRoomUserResult> results = chatRoomUserService.findMyActiveRooms(USER_ID);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ChatRoomUserResult::chatRoomId).containsExactly(10L, 20L);
    }

    @Test
    void 참여중인_방이_없으면_빈_목록() {
        given(chatRoomUsers.findActiveByUserId(USER_ID)).willReturn(List.of());

        List<ChatRoomUserResult> results = chatRoomUserService.findMyActiveRooms(USER_ID);

        assertThat(results).isEmpty();
    }

    @Test
    void 나가기_시_퇴장_이벤트를_발행한다() {
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID))
                .willReturn(Optional.of(activeUser(ROOM_ID)));

        chatRoomUserService.leave(new ChatRoomCommand(ROOM_ID, USER_ID, PUBLIC_ID));

        verify(eventPublisher).publishEvent(new ChatRoomLeftEvent(USER_ID, PUBLIC_ID, ROOM_ID));
    }

    @Test
    void 방_목록에_마지막_메시지_포함() {
        given(chatRoomUsers.findActiveByUserId(USER_ID))
                .willReturn(List.of(activeUser(10L), activeUser(20L)));
        ChatMessage last = ChatMessage.restore(99L, 10L, 5L, "안녕", ChatMessageType.TEXT, ChatMessageStatus.ACTIVE, NOW, null);
        given(chatMessages.findLatestByChatRoomIds(List.of(10L, 20L))).willReturn(Map.of(10L, last));
        given(memberProvider.findMembers(List.of(5L)))
                .willReturn(Map.of(5L, new ChatMember(5L, PUBLIC_ID, "닉", null)));

        List<ChatRoomUserResult> results = chatRoomUserService.findMyActiveRooms(USER_ID);

        assertThat(results.get(0).lastMessage().content()).isEqualTo("안녕");
        assertThat(results.get(0).lastMessage().senderPublicId()).isEqualTo(PUBLIC_ID);
        assertThat(results.get(1).lastMessage()).isNull();
    }
}
