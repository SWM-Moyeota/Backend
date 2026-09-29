package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.room.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.room.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.room.ChatRoomService;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.PartySnapshot;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingChatRoomSteps {

    private final ChatRooms chatRooms;
    private final ChatRoomUsers chatRoomUsers;
    private final ChatRoomService chatRoomService;
    private final ChatRoomUserService chatRoomUserService;
    private final PartyProvider partyProvider;

    /**
     * 이벤트 순서를 믿지 않음
     * 파티의 지금 원본을 읽고 채팅방을 그 상태로 변경
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(Long partyId, Long memberId) {
        Optional<PartySnapshot> party = partyProvider.findSnapshot(partyId);
        if (party.isEmpty()) {
            log.warn("파티를 찾을 수 없어 채팅방 동기화 건너뜀 partyId={} memberId={}", partyId, memberId);
            return;
        }

        boolean shouldBeIn = party.get().userIds().contains(memberId);

        Long chatRoomId = chatRooms.findByPartyIdForUpdate(partyId)
                .map(ChatRoom::getId)
                .orElse(null);

        if (chatRoomId == null) {
            if (!shouldBeIn) {
                return;
            }
            PartySnapshot p = party.get();
            chatRoomId = chatRoomService.createRoom(
                    new CreateChatRoomCommand(p.partyId(), p.departurePlace(), p.destinationPlace())).id();
        }

        boolean isIn = chatRoomUsers.findActiveByUserIdAndChatRoomId(memberId, chatRoomId).isPresent();

        if (shouldBeIn && !isIn) {
            chatRoomUserService.join(new ChatRoomCommand(chatRoomId, memberId, null));
        }
        if (!shouldBeIn && isIn) {
            chatRoomUserService.leave(new ChatRoomCommand(chatRoomId, memberId, null));
        }
    }
}