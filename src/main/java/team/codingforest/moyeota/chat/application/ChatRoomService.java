package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.application.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.application.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.PartySnapshot;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRooms chatRooms;
    private final PartyProvider partyProvider;

    /**
     * 채팅방 만들기 (내부 호출 이벤트)
     */
    @Transactional
    public ChatRoomResult createRoom(CreateChatRoomCommand command) {
        ChatRoom chatRoom = ChatRoom.create(
                command.partyId(),
                command.departure(),
                command.destination(),
                Instant.now()
        );

        if (chatRooms.existsByPartyId(command.partyId())) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }

        ChatRoom result = chatRooms.save(chatRoom);

        log.info("채팅방 생성 partyId={} chatRoomId={}", result.getPartyId(), result.getId());

        return ChatRoomResult.from(result);
    }

    /**
     * 채팅방 단건 조회
     */
    @Transactional(readOnly = true)
    public ChatRoomResult findById(Long userId, Long chatRoomId) {
        validatePartyMember(userId, chatRoomId);

        ChatRoom chatRoom = chatRooms.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        return ChatRoomResult.from(chatRoom);
    }

    /**
     * 채팅방 상태 변경 (방 닫기)
     */
    @Transactional
    public void close(Long chatRoomId) {
        ChatRoom chatRoom = chatRooms.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        chatRoom.close(Instant.now());

        chatRooms.save(chatRoom);

        log.info("채팅방 종료 chatRoomId={}", chatRoomId);
    }

    private void validatePartyMember(Long userId, Long partyId) {
        PartySnapshot party = partyProvider.findSnapshot(partyId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_PARTY_NOT_FOUND));

        if (!party.userIds().contains(userId)) {
            throw new ChatException(ChatErrorCode.CHAT_NOT_PARTY_MEMBER);
        }
    }

}
