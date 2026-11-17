package team.codingforest.moyeota.chat.room.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.common.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.common.exception.ChatException;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.domain.ChatRoomStatus;
import team.codingforest.moyeota.chat.room.domain.PartyProvider;
import team.codingforest.moyeota.chat.room.domain.PartySnapshot;
import team.codingforest.moyeota.chat.room.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.room.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;

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
        ChatRoom chatRoom = chatRooms.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        validatePartyMember(userId, chatRoom.getPartyId());

        return ChatRoomResult.from(chatRoom);
    }

    /**
     * 약속 확정으로 바뀐 만남 장소를 채팅방 출발지에 반영
     * 방이 없으면 예외를 던짐. 참여 리스너가 방을 아직 커밋하지 않았을 수 있어 재발행돼 방이 생긴 뒤 반영됨
     *
     * @param partyId 약속을 확정한 파티
     * @param departure 확정된 만남 장소 이름
     */
    @Transactional
    public void changeDeparture(Long partyId, String departure) {
        int changed = chatRooms.changeDeparture(partyId, departure, Instant.now());

        if (changed == 0) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        log.info("채팅방 출발지 변경 partyId={} departure={}", partyId, departure);
    }

    /**
     * 파티가 닫히면 채팅방을 끝난 상태로 바꿈. 대화는 CLOSED 가 되기 전까지 계속 가능
     * 방이 없거나(혼자 있다 취소된 파티) 이미 끝났으면(같은 이벤트 재발행) 할 일이 없음
     *
     * @param partyId 닫힌 파티
     * @param partyStatus 파티의 닫힌 상태 이름(FINISHED 또는 CANCELED)
     */
    @Transactional
    public void end(Long partyId, String partyStatus) {
        ChatRoomStatus status = ChatRoomStatus.endedBy(partyStatus);
        int changed = chatRooms.endByPartyId(partyId, status, Instant.now());

        if (changed == 0) {
            log.info("진행 중인 채팅방이 없어 종료 건너뜀 partyId={} status={}", partyId, status);
            return;
        }

        log.info("채팅방 종료 partyId={} status={}", partyId, status);
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
