package team.codingforest.moyeota.chat.room.application;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import team.codingforest.moyeota.matching.api.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberLeftEvent;

@Component
@RequiredArgsConstructor
public class ChatRoomMatchingListener {
    private final MatchingChatRoomService matchingChatRoomService;

    /**
     * 예외를 삼키지 않는다. 삼키면 event_publication 이 완료로 기록돼 재발행되지 않는다.
     * 이미 참여·이미 나감 같은 정상 케이스는 MatchingChatRoomService 가 처리한다.
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(PartyMemberJoinedEvent event) {
        matchingChatRoomService.joinMember(event.partyId(), event.memberId());
    }

    /**
     * 채팅방 나가기
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(PartyMemberLeftEvent event) {
        matchingChatRoomService.leaveMember(event.partyId(), event.memberId());
    }
}
