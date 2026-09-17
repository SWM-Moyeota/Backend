package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingChatRoomServiceTest {
    private static final Long PARTY_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 7L;

    @Mock
    private MatchingChatRoomSteps steps;
    @InjectMocks
    private MatchingChatRoomService matchingChatRoomService;

    @Test
    void 방을_찾거나_만든_뒤_그_사람만_참여시킨다() {
        given(steps.findOrCreateRoom(PARTY_ID)).willReturn(ROOM_ID);

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(steps).join(ROOM_ID, MEMBER_ID);
        verify(steps, never()).findRoom(anyLong());
    }

    @Test
    void 동시에_들어와_방이_먼저_만들어졌으면_그_방에_참여한다() {
        given(steps.findOrCreateRoom(PARTY_ID))
                .willThrow(new DataIntegrityViolationException("party_id unique"));
        given(steps.findRoom(PARTY_ID)).willReturn(ROOM_ID);

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(steps).join(ROOM_ID, MEMBER_ID);
    }

    @Test
    void 선체크에_걸려_이미_있다고_하면_그_방에_참여한다() {
        given(steps.findOrCreateRoom(PARTY_ID))
                .willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS));
        given(steps.findRoom(PARTY_ID)).willReturn(ROOM_ID);

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(steps).join(ROOM_ID, MEMBER_ID);
    }

    @Test
    void 생성도_실패하고_재조회도_없으면_참여하지_않고_던진다() {
        given(steps.findOrCreateRoom(PARTY_ID))
                .willThrow(new ChatException(ChatErrorCode.CHAT_PARTY_NOT_FOUND));
        given(steps.findRoom(PARTY_ID))
                .willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        assertThatThrownBy(() -> matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);

        verify(steps, never()).join(anyLong(), anyLong());
    }

    @Test
    void 이미_참여한_사람의_이벤트가_다시_와도_실패하지_않는다() {
        given(steps.findOrCreateRoom(PARTY_ID)).willReturn(ROOM_ID);
        willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED))
                .given(steps).join(ROOM_ID, MEMBER_ID);

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);
    }

    @Test
    void 참여_중_다른_오류는_그대로_던진다() {
        given(steps.findOrCreateRoom(PARTY_ID)).willReturn(ROOM_ID);
        willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND))
                .given(steps).join(ROOM_ID, MEMBER_ID);

        assertThatThrownBy(() -> matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    void 파티에서_나가면_채팅방에서도_나간다() {
        given(steps.findRoom(PARTY_ID)).willReturn(ROOM_ID);

        matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID);

        verify(steps).leave(ROOM_ID, MEMBER_ID);
    }

    @Test
    void 채팅방이_없는_파티의_퇴장은_무시한다() {
        given(steps.findRoom(PARTY_ID))
                .willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID);

        verify(steps, never()).leave(anyLong(), anyLong());
    }

    @Test
    void 이미_나간_사람의_이벤트가_다시_와도_실패하지_않는다() {
        given(steps.findRoom(PARTY_ID)).willReturn(ROOM_ID);
        willThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(steps).leave(ROOM_ID, MEMBER_ID);

        matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID);
    }
}
