package team.codingforest.moyeota.chat.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import team.codingforest.moyeota.chat.app.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.app.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.app.dto.ReadChatCommand;
import team.codingforest.moyeota.chat.app.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.domain.*;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomUserServiceTest {
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Long PARTY_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    private ChatRoomUserRepository chatRoomUserRepository;
    private ChatRoomRepository chatRoomRepository;
    private ChatRoomUserService chatRoomUserService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        chatRoomUserRepository = mock(ChatRoomUserRepository.class);
        chatRoomRepository = mock(ChatRoomRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        chatRoomUserService = new ChatRoomUserService(eventPublisher, chatRoomUserRepository, chatRoomRepository);
    }

    private ChatRoom room(ChatRoomStatus status) {
        return ChatRoom.restore(ROOM_ID, PARTY_ID, "서울시청", "강남역", NOW, status);
    }

    private ChatRoomUser activeUser(Long chatRoomId) {
        return ChatRoomUser.join(USER_ID, chatRoomId, NOW);
    }

    @Test
    void 채팅방_참여_성공() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        given(chatRoomUserRepository.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.empty());

        chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID));

        verify(chatRoomUserRepository).save(any(ChatRoomUser.class));
    }

    @Test
    void 이미_참여중이면_예외() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        given(chatRoomUserRepository.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID))
                .willReturn(Optional.of(activeUser(ROOM_ID)));

        assertThatThrownBy(() -> chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED);
    }

    @Test
    void 없는_방_참여_예외() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 종료된_방_참여_예외() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.CLOSED)));

        assertThatThrownBy(() -> chatRoomUserService.join(new ChatRoomCommand(ROOM_ID, USER_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_CLOSED);
    }

    @Test
    void 채팅방_나가기_성공() {
        ChatRoomUser user = activeUser(ROOM_ID);
        given(chatRoomUserRepository.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.of(user));

        chatRoomUserService.leave(new ChatRoomCommand(ROOM_ID, USER_ID));

        assertThat(user.hasLeft()).isTrue();
        verify(chatRoomUserRepository).save(user);
    }

    @Test
    void 참여자가_아니면_나가기_예외() {
        given(chatRoomUserRepository.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomUserService.leave(new ChatRoomCommand(ROOM_ID, USER_ID)))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }

    @Test
    void 읽음_처리_성공() {
        ChatRoomUser user = activeUser(ROOM_ID);
        given(chatRoomUserRepository.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID)).willReturn(Optional.of(user));

        chatRoomUserService.read(new ReadChatCommand(USER_ID, ROOM_ID, 5L));

        assertThat(user.getLastReadMessageId()).isEqualTo(5L);
        verify(chatRoomUserRepository).save(user);
    }

    @Test
    void 참여중인_방_목록_조회() {
        given(chatRoomUserRepository.findActiveByUserId(USER_ID))
                .willReturn(List.of(activeUser(10L), activeUser(20L)));

        List<ChatRoomUserResult> results = chatRoomUserService.findMyActiveRooms(USER_ID);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ChatRoomUserResult::chatRoomId).containsExactly(10L, 20L);
    }

    @Test
    void 참여중인_방이_없으면_빈_목록() {
        given(chatRoomUserRepository.findActiveByUserId(USER_ID)).willReturn(List.of());

        List<ChatRoomUserResult> results = chatRoomUserService.findMyActiveRooms(USER_ID);

        assertThat(results).isEmpty();
    }

    @Test
    void 나가기_시_퇴장_이벤트를_발행한다() {
        given(chatRoomUserRepository.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID))
                .willReturn(Optional.of(activeUser(ROOM_ID)));

        chatRoomUserService.leave(new ChatRoomCommand(ROOM_ID, USER_ID));

        verify(eventPublisher).publishEvent(new ChatRoomLeftEvent(USER_ID, ROOM_ID));
    }
}
