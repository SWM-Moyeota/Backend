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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingChatRoomServiceTest {
    private static final Long PARTY_ID = 1L;
    private static final Long MEMBER_ID = 7L;

    @Mock
    private MatchingChatRoomSteps steps;
    @InjectMocks
    private MatchingChatRoomService matchingChatRoomService;

    @Test
    void 참여와_퇴장_모두_같은_sync_를_부른다() {
        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);
        matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID);

        verify(steps, times(2)).sync(PARTY_ID, MEMBER_ID);
    }

    @Test
    void 방_동시_생성으로_UNIQUE_에_걸리면_한_번_더_맞춘다() {
        willThrow(new DataIntegrityViolationException("party_id unique"))
                .willDoNothing()
                .given(steps).sync(PARTY_ID, MEMBER_ID);

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(steps, times(2)).sync(PARTY_ID, MEMBER_ID);
    }

    @Test
    void 선체크에_이미_있다고_걸려도_한_번_더_맞춘다() {
        willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS))
                .willDoNothing()
                .given(steps).sync(PARTY_ID, MEMBER_ID);

        matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID);

        verify(steps, times(2)).sync(PARTY_ID, MEMBER_ID);
    }

    @Test
    void 재시도도_실패하면_던져서_재발행에_맡긴다() {
        willThrow(new DataIntegrityViolationException("party_id unique"))
                .given(steps).sync(PARTY_ID, MEMBER_ID);

        assertThatThrownBy(() -> matchingChatRoomService.joinMember(PARTY_ID, MEMBER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_채팅_오류는_재시도하지_않고_던진다() {
        willThrow(new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND))
                .given(steps).sync(PARTY_ID, MEMBER_ID);

        assertThatThrownBy(() -> matchingChatRoomService.leaveMember(PARTY_ID, MEMBER_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
        verify(steps, times(1)).sync(PARTY_ID, MEMBER_ID);
    }
}