package team.codingforest.moyeota.chat.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.app.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.app.dto.ChatRoomMemberResult;
import team.codingforest.moyeota.chat.app.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.app.dto.ReadChatCommand;
import team.codingforest.moyeota.chat.app.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.domain.*;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomUserService {
    private final ApplicationEventPublisher eventPublisher;
    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MemberProvider memberProvider;

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

        try {
            chatRoomUserRepository.save(ChatRoomUser.join(command.userId(), command.chatRoomId(), Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED);
        }


        log.info("채팅방 참여 chatRoomId={} userID={}", command.chatRoomId(), command.userId());
    }

    @Transactional
    public void leave(ChatRoomCommand command) {
        ChatRoomUser chatRoomUser = getActiveUser(command.userId(), command.chatRoomId());

        chatRoomUser.leave(Instant.now());

        chatRoomUserRepository.save(chatRoomUser);

        eventPublisher.publishEvent(
                new ChatRoomLeftEvent(command.userId(), command.publicId(), command.chatRoomId()));

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

    @Transactional(readOnly = true)
    public void validateParticipant(Long userId, Long chatRoomId) {
        getActiveUser(userId, chatRoomId);
    }

    private ChatRoomUser getActiveUser(Long userId, Long chatRoomId) {
        return chatRoomUserRepository.findActiveByUserIdAndChatRoomId(userId, chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomMemberResult> findMembers(Long userId, Long chatRoomId) {
        validateParticipant(userId, chatRoomId);

        List<ChatRoomUser> participants = chatRoomUserRepository.findAllByChatRoomId(chatRoomId);

        List<Long> userIds = participants.stream().map(ChatRoomUser::getUserId).toList();

        Map<Long, ChatMember> members = memberProvider.findMembers(userIds);

        return participants.stream()
                .filter(p -> members.containsKey(p.getUserId()))
                .map(p -> {
                    ChatMember member = members.get(p.getUserId());
                    return new ChatRoomMemberResult(
                            member.publicId(),
                            member.nickname(),
                            member.imageUrl(),
                            !p.hasLeft());
                })
                .toList();
    }
}
