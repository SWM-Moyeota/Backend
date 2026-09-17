package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingChatRoomService {

    private final ChatRooms chatRooms;
    private final ChatRoomService chatRoomService;
    private final ChatRoomUserService chatRoomUserService;
    private final PartyProvider partyProvider;

    /**
     * 파티원이 들어올 때마다 호출된다. 방이 없으면 만들고, 그 사람만 참여시킨다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void joinMember(Long partyId, Long memberId) {
        Long chatRoomId = findOrCreateRoom(partyId);

        try {
            chatRoomUserService.join(new ChatRoomCommand(chatRoomId, memberId, null));
        } catch (ChatException e) {
            if (e.getErrorCode() == ChatErrorCode.CHAT_ROOM_ALREADY_JOINED) {
                return;
            }
            throw e;
        }
    }

    /**
     * 파티에서 나가면 채팅방에서도 나간다. 방이 없거나 이미 나갔으면 무시
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void leaveMember(Long partyId, Long memberId) {
        Optional<ChatRoom> room = chatRooms.findByPartyId(partyId);

        if (room.isEmpty()) {
            return;
        }

        try {
            chatRoomUserService.leave(
                    new ChatRoomCommand(room.get().getId(), memberId, null));
        } catch (ChatException e) {
            if (e.getErrorCode() == ChatErrorCode.CHAT_NOT_PARTICIPANT) {
                return;
            }
            throw e;
        }
    }

    private Long findOrCreateRoom(Long partyId) {
        return chatRooms.findByPartyId(partyId)
                .map(ChatRoom::getId)
                .orElseGet(() -> createRoom(partyId));
    }

    private Long createRoom(Long partyId) {
        PartySnapshot party = partyProvider.findSnapshot(partyId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_PARTY_NOT_FOUND));

        try {
            return chatRoomService.createRoom(new CreateChatRoomCommand(
                    party.partyId(), party.departurePlace(), party.destinationPlace())).id();
        } catch (ChatException | DataIntegrityViolationException e) {
            return chatRooms.findByPartyId(partyId)
                    .map(ChatRoom::getId)
                    .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS));
        }
    }

}