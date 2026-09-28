package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingChatRoomService {

    private final MatchingChatRoomSteps steps;

    /** 파티원이 들어왔다는 신호. 무엇을 할지는 파티 현재 상태로 정한다 */
    public void joinMember(Long partyId, Long memberId) {
        syncWithRetry(partyId, memberId);
    }

    /** 파티원이 나갔다는 신호. 무엇을 할지는 파티 현재 상태로 정한다 */
    public void leaveMember(Long partyId, Long memberId) {
        syncWithRetry(partyId, memberId);
    }

    /**
     * 방이 없던 순간 두 이벤트가 동시에 방을 만들면 한쪽이 선체크나 UNIQUE 에 걸린다.
     * 그때는 방이 이미 생긴 것이므로 한 번 더 돌리면 락을 잡고 정상 처리된다
     */
    private void syncWithRetry(Long partyId, Long memberId) {
        try {
            steps.sync(partyId, memberId);
        } catch (DataIntegrityViolationException e) {
            log.info("채팅방 동시 생성 충돌, 재동기화 partyId={}, memberId={}", partyId, memberId);
            steps.sync(partyId, memberId);
        } catch (ChatException e) {
            if (e.getErrorCode() != ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS) {
                throw e;
            }
            log.info("채팅방 동시 생성 충돌, 재동기화 partyId={}, memberId={}", partyId, memberId);
            steps.sync(partyId, memberId);
        }
    }
}