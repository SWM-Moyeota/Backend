package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.chat.application.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.application.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.application.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomStatus;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.PartySnapshot;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingChatRoomServiceTest {
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
    private MatchingChatRoomService matchingChatRoomService;

    private ChatRoom room() {
        return ChatRoom.restore(ROOM_ID, PARTY_ID, "강남역", "판교역", NOW, NOW, ChatRoomStatus.ACTIVE);
    }

    private void givenNoRoom() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        given(partyProvider.findSnapshot(PARTY_ID))
                .willReturn(Optional.of(new PartySnapshot(PARTY_ID, List.of(MEMBER_ID), "강남역", "판교역")));
    }

    private ChatRoomResult createdRoom() {
        return new ChatRoomResult(ROOM_ID, PARTY_ID, "강남역", "판교역", NOW, ChatRoomStatus.ACTIVE);
    }

    @Test
    void 방이_없으면_만들고_그_사람만_참여시킨다() {
        givenNoRoom();
        given(chatRoomService.createRoom(any())).willReturn(createdRoom());

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(chatRoomService).createRoom(new CreateChatRoomCommand(PARTY_ID, "강남역", "판교역"));
        verify(chatRoomUserService).join(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
    }

    @Test
    void 방이_있으면_만들지_않고_참여만_한다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.of(room()));

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(chatRoomService, never()).createRoom(any());
        verify(chatRoomUserService).join(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
    }

    @Test
    void 이미_참여한_사람의_이벤트가_다시_와도_실패하지_않는다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.of(room()));
        willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED))
                .given(chatRoomUserService).join(any());

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);   // 예외가 새어 나오면 실패
    }

    @Test
    void 참여_중_다른_오류는_그대로_던진다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.of(room()));
        willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND))
                .given(chatRoomUserService).join(any());

        assertThatThrownBy(() -> matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 파티를_찾을_수_없으면_방을_만들지_않는다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.empty());
        given(partyProvider.findSnapshot(PARTY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_PARTY_NOT_FOUND);
    }

    @Test
    void 동시에_들어와_방이_먼저_만들어졌으면_그_방에_참여한다() {
        given(chatRooms.findByPartyId(PARTY_ID))
                .willReturn(Optional.empty())        // 만들기 전 조회 - 없음
                .willReturn(Optional.of(room()));    // 생성 실패 후 재조회 - 다른 요청이 먼저 만듦
        given(partyProvider.findSnapshot(PARTY_ID))
                .willReturn(Optional.of(new PartySnapshot(PARTY_ID, List.of(MEMBER_ID), "강남역", "판교역")));
        given(chatRoomService.createRoom(any()))
                .willThrow(new DataIntegrityViolationException("party_id unique"));

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService).join(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
    }

    @Test
    void 파티에서_나가면_채팅방에서도_나간다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.of(room()));

        matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService).leave(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
    }

    @Test
    void 채팅방이_없는_파티의_퇴장은_무시한다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.empty());

        matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService, never()).leave(any());
    }

    @Test
    void 이미_나간_사람의_이벤트가_다시_와도_실패하지_않는다() {
        given(chatRooms.findByPartyId(PARTY_ID)).willReturn(Optional.of(room()));
        willThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(chatRoomUserService).leave(any());

        matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID);
    }
}