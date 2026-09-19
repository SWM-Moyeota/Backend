package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.chat.application.dto.ChatLocationResult;
import team.codingforest.moyeota.chat.domain.ChatLocation;
import team.codingforest.moyeota.chat.domain.ChatLocationPublisher;
import team.codingforest.moyeota.chat.domain.ChatLocations;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.ChatRooms;
import team.codingforest.moyeota.chat.domain.MemberProvider;
import team.codingforest.moyeota.chat.domain.PartyProvider;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatLocationService {

    private final ChatLocations chatLocations;
    private final ChatRooms chatRooms;
    private final PartyProvider partyProvider;
    private final MemberProvider memberProvider;
    private final ChatLocationPublisher locationPublisher;

    /**
     * 위치 공유 토글 ON. 세션이 살아있는 동안만 발행할 수 있다.
     */
    public void startSharing(Long userId, Long chatRoomId) {
        validateMember(userId, chatRoomId);

        chatLocations.startSession(userId, chatRoomId);

        log.info("위치 공유 시작 chatRoomId={} userId={}", chatRoomId, userId);
    }

    /**
     * 위치 발행 포그라운드(WS)와 백그라운드(REST)가 같은 경로를 탄다.
     */
    public void share(Long userId, UUID publicId, Long chatRoomId, ChatLocation location) {
        validateMember(userId, chatRoomId);

        if (!chatLocations.isSharing(userId, chatRoomId)) {
            throw new ChatException(ChatErrorCode.CHAT_LOCATION_SHARING_NOT_STARTED);
        }

        if (chatLocations.put(userId, chatRoomId, location)) {
            locationPublisher.publish(chatRoomId, publicId, location);
        }
    }

    /**
     * 위치 공유 토글 OFF
     * 파티가 끝난 뒤에도 끌 수 있어야 하므로 검증하지 않는다.
     */
    public void stopSharing(Long userId, Long chatRoomId) {
        chatLocations.stop(userId, chatRoomId);

        log.info("위치 공유 중단 chatRoomId={} userId={}", chatRoomId, userId);
    }

    /**
     * 지도를 열 때 받는 현재 스냅샷. 낡은 좌표는 제외한다.
     */
    public List<ChatLocationResult> findAll(Long userId, Long chatRoomId) {
        validateMember(userId, chatRoomId);

        Instant now = Instant.now();

        Map<Long, ChatLocation> locations = chatLocations.findAll(chatRoomId);

        if (locations.isEmpty()) {
            return List.of();
        }

        Map<Long, ChatMember> members = memberProvider.findMembers(locations.keySet().stream().toList());

        return locations.entrySet().stream()
                .filter(entry -> entry.getValue().isFresh(now))
                .filter(entry -> members.containsKey(entry.getKey()))
                .map(entry -> ChatLocationResult.of(members.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    /**
     * 파티가 끝났으면 위치 공유를 막는다.
     * chat 은 파티 상태를 직접 볼 수 없어 chatRoomId 로 partyId 를 얻어 확인한다.
     */
    private void validateMember(Long userId, Long chatRoomId) {
        Long partyId = chatRooms.findById(chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND))
                .getPartyId();

        if (!partyProvider.isActiveMember(userId, partyId)) {
            throw new ChatException(ChatErrorCode.CHAT_NOT_PARTY_MEMBER);
        }
    }
}
