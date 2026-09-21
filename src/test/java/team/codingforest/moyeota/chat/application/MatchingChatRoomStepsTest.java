package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.application.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.application.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.application.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.PartySnapshot;
import team.codingforest.moyeota.chat.domain.enums.ChatRoomStatus;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingChatRoomStepsTest {
    private static final Long PARTY_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

    @Mock
    private ChatRooms chatRooms;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ChatRoomUserService chatRoomUserService;
    @Mock
    private PartyProvider partyProvider;
    @InjectMocks
    private MatchingChatRoomSteps steps;

    private ChatRoom room() {
        return ChatRoom.restore(ROOM_ID, PARTY_ID, "강남역", "판교역", NOW, NOW, ChatRoomStatus.ACTIVE);
    }

    @Test
    void 방이_없으면_파티_정보로_만든다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        given(partyProvider.findSnapshot(PARTY_ID))
                .willReturn(Optional.of(new PartySnapshot(PARTY_ID, List.of(MEMBER_ID), "강남역", "판교역")));
        given(chatRoomService.createRoom(any()))
                .willReturn(new ChatRoomResult(ROOM_ID, PARTY_ID, "강남역", "판교역", NOW, ChatRoomStatus.ACTIVE));

        Long result = steps.findOrCreateRoom(PARTY_ID);

        assertThat(result).isEqualTo(ROOM_ID);
        verify(chatRoomService).createRoom(new CreateChatRoomCommand(PARTY_ID, "강남역", "판교역"));
    }

    @Test
    void 방이_있으면_만들지_않는다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.of(room()));

        Long result = steps.findOrCreateRoom(PARTY_ID);

        assertThat(result).isEqualTo(ROOM_ID);
        verify(chatRoomService, never()).createRoom(any());
    }

    @Test
    void 파티를_찾을_수_없으면_방을_만들지_않는다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        given(partyProvider.findSnapshot(PARTY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> steps.findOrCreateRoom(PARTY_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_PARTY_NOT_FOUND);
        verify(chatRoomService, never()).createRoom(any());
    }

    @Test
    void findRoom은_방이_없으면_NOT_FOUND를_던진다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> steps.findRoom(PARTY_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void join은_커맨드로_변환해_위임() {
        steps.join(ROOM_ID, MEMBER_ID);

        verify(chatRoomUserService).join(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
    }

    @Test
    void leave는_커맨드로_변환해_위임() {
        steps.leave(ROOM_ID, MEMBER_ID);

        verify(chatRoomUserService).leave(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
    }
}