package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.matching.api.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberLeftEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomMatchingListener {
    private final MatchingChatRoomService matchingChatRoomService;

    /**
     * 채팅방 생성 및 입장
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PartyMemberJoinedEvent event) {
        try {
            matchingChatRoomService.joinMember(event.partyId(), event.memberId());
        } catch (Exception e) {
            log.error("채팅방 참여 실패 partyId={} memberId={}", event.partyId(), event.memberId(), e);
        }
    }

    /**
     * 채팅방 나가기
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PartyMemberLeftEvent event) {
        try {
            matchingChatRoomService.leaveMember(event.partyId(), event.memberId());
        } catch (Exception e) {
            log.error("채팅방 퇴장 실패 partyId={} memberId={}", event.partyId(), event.memberId(), e);
        }
    }
}
