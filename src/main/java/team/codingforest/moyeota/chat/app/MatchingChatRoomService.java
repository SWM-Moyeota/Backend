package team.codingforest.moyeota.chat.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.app.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.app.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.app.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomRepository;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.PartySnapshot;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomService chatRoomService;
    private final ChatRoomUserService chatRoomUserService;
    private final PartyProvider partyProvider;

    /**
     * 채팅방 여부를 확인하고 채팅방이 없으면 파티정보를 가져와 채팅방을 만들어 유저들을 추가한다.
     */
    @Transactional
    public ChatRoomResult provisionForParty(Long partyId) {
        Optional<ChatRoom> existing = chatRoomRepository.findByPartyId(partyId);

        if (existing.isPresent()) {
            return ChatRoomResult.from(existing.get());
        }

        PartySnapshot party = partyProvider.findSnapshot(partyId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_PARTY_NOT_FOUND));

        if (!party.needsChatRoom()) {
            log.info("파티원이 부족해 채팅방을 만들지 않음 partyId={} userCount={}", partyId, party.userIds().size());
            throw new ChatException(ChatErrorCode.CHAT_ROOM_NOT_REQUIRED);
        }

        ChatRoomResult room = chatRoomService.createRoom(
                new CreateChatRoomCommand(party.partyId(), party.departurePlace(), party.destinationPlace()));

        for (Long memberId : party.userIds()) {
            chatRoomUserService.join(new ChatRoomCommand(room.id(), memberId));
        }

        log.info("매칭 채팅방 생성 partyId={} chatRoomId={} 참여자={}", partyId, room.id(), party.userIds());

        return room;
    }
}
