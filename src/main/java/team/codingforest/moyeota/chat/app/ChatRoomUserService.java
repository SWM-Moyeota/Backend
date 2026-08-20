package team.codingforest.moyeota.chat.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.app.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.app.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.app.dto.ReadChatCommand;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomRepository;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUserRepository;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomUserService {
    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Transactional
    public void join(ChatRoomCommand command) {
        ChatRoom chatRoom = chatRoomRepository.findById(command.chatRoomId())
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        chatRoom.validateJoin();

        boolean alreadyJoined = chatRoomUserRepository.findActiveByUserIdAndChatRoomId(command.userId(), command.chatRoomId())
                .isPresent();

        if (alreadyJoined) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED);
        }

        chatRoomUserRepository.save(ChatRoomUser.join(command.userId(), command.chatRoomId(), Instant.now()));

        log.info("채팅방 참여 chatRoomId={} userID={}", command.chatRoomId(), command.userId());
    }

    @Transactional
    public void leave(ChatRoomCommand command) {
        ChatRoomUser chatRoomUser = getActiveUser(command.userId(), command.chatRoomId());

        chatRoomUser.leave(Instant.now());

        chatRoomUserRepository.save(chatRoomUser);

        log.info("채팅방 나감 chatRoomId={} userID={}", command.chatRoomId(), command.userId());
    }

    @Transactional
    public void read(ReadChatCommand command) {
        ChatRoomUser chatRoomUser = getActiveUser(command.userId(), command.chatRoomId());

        chatRoomUser.read(command.lastReadMessageId());

        chatRoomUserRepository.save(chatRoomUser);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomUserResult> findMyActiveRooms(Long userId) {
        return chatRoomUserRepository.findActiveByUserId(userId).stream()
                .map(ChatRoomUserResult::from)
                .toList();
    }

    private ChatRoomUser getActiveUser(Long userId, Long chatRoomId) {
        return chatRoomUserRepository.findActiveByUserIdAndChatRoomId(userId, chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }
}
