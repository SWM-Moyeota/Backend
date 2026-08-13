package team.codingforest.moyeota.chat.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.app.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.app.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomRepository;
import team.codingforest.moyeota.chat.domain.ChatRoomStatus;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long PARTY_ID = 100L;
    private static final String DEPARTURE = "서울시청";
    private static final String DESTINATION = "강남역";
    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @InjectMocks
    private ChatRoomService chatRoomService;

    private ChatRoom room(ChatRoomStatus status) {
        return ChatRoom.restore(ROOM_ID, PARTY_ID, DEPARTURE, DESTINATION, NOW, status);
    }

    private CreateChatRoomCommand command() {
        return new CreateChatRoomCommand(PARTY_ID, DEPARTURE, DESTINATION);
    }

    @Test
    public void 채팅방_생성_성공() {
        given(chatRoomRepository.existsByPartyId(PARTY_ID)).willReturn(false);
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(room(ChatRoomStatus.ACTIVE));

        ChatRoomResult result = chatRoomService.createRoom(command());

        assertThat(result.id()).isEqualTo(ROOM_ID);
        assertThat(result.partyId()).isEqualTo(PARTY_ID);
        assertThat(result.departure()).isEqualTo(DEPARTURE);
        assertThat(result.destination()).isEqualTo(DESTINATION);
        assertThat(result.status()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    @Test
    public void 중복된_partyId_예외() {
        given(chatRoomRepository.existsByPartyId(PARTY_ID)).willReturn(true);

        assertThatThrownBy(() -> chatRoomService.createRoom(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS);
    }

    @Test
    public void 채팅방_닫기_성공() {
        ChatRoom chatRoom = room(ChatRoomStatus.ACTIVE);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        chatRoomService.close(ROOM_ID);

        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
    }

    @Test
    public void 채팅방_단건_조회_성공() {
        ChatRoom chatRoom = room(ChatRoomStatus.ACTIVE);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        ChatRoomResult result = chatRoomService.findById(ROOM_ID);

        assertThat(result.id()).isEqualTo(ROOM_ID);
        assertThat(result.partyId()).isEqualTo(PARTY_ID);
        assertThat(result.status()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    @Test
    public void 없는_방_조회_예외() {
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.findById(ROOM_ID))
            .isInstanceOf(ChatException.class)
            .extracting("errorCode")
            .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }
}