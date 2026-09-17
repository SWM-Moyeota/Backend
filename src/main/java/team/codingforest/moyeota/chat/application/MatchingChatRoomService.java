package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingChatRoomService {

    private final MatchingChatRoomSteps steps;

    /**
     * 파티원이 들어올 때마다 호출된다. 방이 없으면 만들고 그 사람만 참여시킨다.
     */
    public void joinMember(Long partyId, Long memberId) {
        Long chatRoomId;
        try {
            chatRoomId = steps.findOrCreateRoom(partyId);
        } catch (ChatException | DataIntegrityViolationException e) {
            chatRoomId = steps.findRoom(partyId);
        }

        try {
            steps.join(chatRoomId, memberId);
        } catch (ChatException e) {
            if (e.getErrorCode() != ChatErrorCode.CHAT_ROOM_ALREADY_JOINED) {
                throw e;
            }
        }
    }

    /**
     * 파티에서 나가면 채팅방에서도 나간다. 방이 없거나 이미 나갔으면 무시
     */
    public void leaveMember(Long partyId, Long memberId) {
        Long chatRoomId;
        try {
            chatRoomId = steps.findRoom(partyId);
        } catch (ChatException e) {
            if (e.getErrorCode() == ChatErrorCode.CHAT_ROOM_NOT_FOUND) {
                return;
            }
            throw e;
        }

        try {
            steps.leave(chatRoomId, memberId);
        } catch (ChatException e) {
            if (e.getErrorCode() != ChatErrorCode.CHAT_NOT_PARTICIPANT) {
                throw e;
            }
        }
    }
}