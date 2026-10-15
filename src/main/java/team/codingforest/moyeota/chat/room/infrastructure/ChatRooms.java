package team.codingforest.moyeota.chat.room.infrastructure;

import team.codingforest.moyeota.chat.room.domain.ChatRoom;

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
     * 방 행을 잠그고 다음 메시지 순번을 받음. 트랜잭션이 끝날 때까지 같은 방의 다른 전송은 기다림
     * 그래서 같은 방 안에서는 순번 순서와 커밋 순서가 같음
     *
     * @param chatRoomId 메시지를 저장할 방
     * @return 1부터 1씩 증가하는 방별 순번. 방이 없으면 빈 값
     */
    Optional<Long> nextMessageSeq(Long chatRoomId);
}