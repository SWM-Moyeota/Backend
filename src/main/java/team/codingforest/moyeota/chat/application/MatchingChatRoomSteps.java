package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.application.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.application.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.PartySnapshot;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

/**
 * 매칭 이벤트 → 채팅방 동기화의 각 단계를 독립 트랜잭션으로 실행한다.
 * AFTER_COMMIT 리스너에서 불리므로 반드시 REQUIRES_NEW.
 * 한 단계가 실패해도 다른 단계의 트랜잭션은 오염되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MatchingChatRoomSteps {

    private final ChatRooms chatRooms;
    private final ChatRoomService chatRoomService;
    private final ChatRoomUserService chatRoomUserService;
    private final PartyProvider partyProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long findOrCreateRoom(Long partyId) {
        return chatRooms.findByPartyId(partyId)
                .map(ChatRoom::getId)
                .orElseGet(() -> {
                    PartySnapshot party = partyProvider.findSnapshot(partyId)
                            .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_PARTY_NOT_FOUND));
                    return chatRoomService.createRoom(new CreateChatRoomCommand(
                            party.partyId(), party.departurePlace(), party.destinationPlace())).id();
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findRoom(Long partyId) {
        return chatRooms.findByPartyId(partyId)
                .map(ChatRoom::getId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void join(Long chatRoomId, Long memberId) {
        chatRoomUserService.join(new ChatRoomCommand(chatRoomId, memberId, null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void leave(Long chatRoomId, Long memberId) {
        chatRoomUserService.leave(new ChatRoomCommand(chatRoomId, memberId, null));
    }
}