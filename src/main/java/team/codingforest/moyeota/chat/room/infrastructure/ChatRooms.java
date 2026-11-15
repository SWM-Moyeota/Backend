package team.codingforest.moyeota.chat.room.infrastructure;

import team.codingforest.moyeota.chat.room.domain.ChatRoom;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChatRooms {
    ChatRoom save(ChatRoom chatRoom);

    Optional<ChatRoom> findById(Long chatRoomId);

    Map<Long, ChatRoom> findByIds(List<Long> chatRoomIds);

    boolean existsByPartyId(Long partyId);

    Optional<ChatRoom> findByPartyId(Long partyId);

    Optional<ChatRoom> findByPartyIdForUpdate(Long partyId);

    /**
     * 방의 출발지만 바꿈. 방 전체를 저장하지 않아 상태·순번을 옛값으로 덮어쓰지 않음
     *
     * @param partyId 출발지가 바뀐 파티
     * @param departure 새 출발지 이름
     * @param now 바꾼 시각
     * @return 바뀐 방 수. 방이 아직 없으면 0
     */
    int changeDeparture(Long partyId, String departure, Instant now);

    /**
     * 방 행을 잠그고 다음 메시지 순번을 받음. 트랜잭션이 끝날 때까지 같은 방의 다른 전송은 기다림
     * 그래서 같은 방 안에서는 순번 순서와 커밋 순서가 같음
     *
     * @param chatRoomId 메시지를 저장할 방
     * @return 1부터 1씩 증가하는 방별 순번. 방이 없으면 빈 값
     */
    Optional<Long> nextMessageSeq(Long chatRoomId);
}