package team.codingforest.moyeota.chat.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomMatchingListener {
    private final MatchingChatRoomService matchingChatRoomService;

    /**
     * 채팅방 생성이 필요없는 경우도 정상 흐름이므로 return
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchingStartedEvent event) {
        try {
            matchingChatRoomService.provisionForParty(event.partyId());
        } catch (ChatException e) {
            if (e.getErrorCode() == ChatErrorCode.CHAT_ROOM_NOT_REQUIRED) {
                return;
            }
            log.error("매칭 채팅방 생성 실패 partyId={}", event.partyId(), e);
        } catch (Exception e) {
            log.error("매칭 채팅방 생성 실패 partyId={}", event.partyId(), e);
        }
    }}
