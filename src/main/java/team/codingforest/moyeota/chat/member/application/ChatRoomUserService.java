package team.codingforest.moyeota.chat.member.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.common.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.common.exception.ChatException;
import team.codingforest.moyeota.chat.member.domain.ChatMember;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.member.domain.MemberProvider;
import team.codingforest.moyeota.chat.member.dto.ChatRoomMemberResult;
import team.codingforest.moyeota.chat.member.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.member.event.ChatRoomJoinedEvent;
import team.codingforest.moyeota.chat.member.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.message.domain.ChatMessage;
import team.codingforest.moyeota.chat.message.domain.ChatMessages;
import team.codingforest.moyeota.chat.message.dto.ReadChatCommand;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomUserService {
    private final ApplicationEventPublisher eventPublisher;
    private final ChatRoomUsers chatRoomUsers;
    private final ChatRooms chatRooms;
    private final ChatMessages chatMessages;
    private final MemberProvider memberProvider;
    private final ChatReadBuffer chatReadBuffer;

    @Transactional
    public void join(ChatRoomCommand command) {
        ChatRoom chatRoom = chatRooms.findById(command.chatRoomId())
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        chatRoom.validateJoin();

        boolean alreadyJoined = chatRoomUsers.findActiveByUserIdAndChatRoomId(command.userId(), command.chatRoomId())
                .isPresent();

        if (alreadyJoined) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED);
        }

        try {
            chatRoomUsers.save(ChatRoomUser.join(command.userId(), command.chatRoomId(), Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_ALREADY_JOINED);
        }

        eventPublisher.publishEvent(new ChatRoomJoinedEvent(command.userId(), command.chatRoomId()));

        log.info("채팅방 참여 chatRoomId={} userID={}", command.chatRoomId(), command.userId());
    }

    @Transactional
    public void leave(ChatRoomCommand command) {
        ChatRoomUser chatRoomUser = getActiveUser(command.userId(), command.chatRoomId());

        chatRoomUser.leave(Instant.now());

        chatRoomUsers.save(chatRoomUser);

        eventPublisher.publishEvent(
                new ChatRoomLeftEvent(command.userId(), command.publicId(), command.chatRoomId()));

        log.info("채팅방 나감 chatRoomId={} userID={}", command.chatRoomId(), command.userId());
    }

    public void read(ReadChatCommand command) {
        chatReadBuffer.record(command.userId(), command.chatRoomId(), command.lastReadMessageId());
    }

    @Transactional(readOnly = true)
    public List<ChatRoomUserResult> findMyActiveRooms(Long userId) {
        List<ChatRoomUser> rooms = chatRoomUsers.findActiveByUserId(userId);

        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(ChatRoomUser::getChatRoomId).toList();
        Map<Long, ChatRoom> chatRoomsById = chatRooms.findByIds(roomIds);
        Map<Long, Long> unreadCounts = chatMessages.countUnreadByUserId(userId);
        Map<Long, ChatMessage> latest = chatMessages.findLatestByChatRoomIds(roomIds);
        List<ChatRoomUser> participants = chatRoomUsers.findAllByChatRoomIds(roomIds);

        Map<Long, ChatMember> members = memberProvider.findMembers(
                Stream.concat(
                        latest.values().stream().map(ChatMessage::getUserId),
                        participants.stream().map(ChatRoomUser::getUserId)
                )
                        .distinct()
                        .toList()
        );

        Map<Long, List<ChatRoomMemberResult>> membersByRoom = participants.stream()
                .filter(participant -> members.containsKey(participant.getUserId()))
                .collect(Collectors.groupingBy(
                        ChatRoomUser::getChatRoomId,
                        Collectors.mapping(
                                participant -> toMemberResult(participant, members.get(participant.getUserId())),
                                Collectors.toList())));

        return rooms.stream()
                .filter(room -> chatRoomsById.containsKey(room.getChatRoomId()))
                .map(room -> ChatRoomUserResult.from(
                        room,
                        chatRoomsById.get(room.getChatRoomId()),
                        toLastMessage(latest.get(room.getChatRoomId()), members),
                        unreadCounts.getOrDefault(room.getChatRoomId(), 0L),
                        membersByRoom.getOrDefault(room.getChatRoomId(), List.of()))
                        )
                .toList();
    }

    private ChatRoomUserResult.LastMessage toLastMessage(ChatMessage message, Map<Long, ChatMember> members) {
        if (message == null) {
            return null;
        }
        ChatMember sender = members.get(message.getUserId());
        return ChatRoomUserResult.LastMessage.from(message, sender == null ? null : sender.publicId());
    }

    private ChatRoomMemberResult toMemberResult(ChatRoomUser participant, ChatMember member) {
        return new ChatRoomMemberResult(
                member.publicId(),
                member.nickname(),
                member.imageUrl(),
                !participant.hasLeft());
    }


    @Transactional(readOnly = true)
    public void validateParticipant(Long userId, Long chatRoomId) {
        getActiveUser(userId, chatRoomId);
    }

    private ChatRoomUser getActiveUser(Long userId, Long chatRoomId) {
        return chatRoomUsers.findActiveByUserIdAndChatRoomId(userId, chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomMemberResult> findMembers(Long userId, Long chatRoomId) {
        validateParticipant(userId, chatRoomId);

        List<ChatRoomUser> participants = chatRoomUsers.findAllByChatRoomId(chatRoomId);

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

    @Transactional
    public void muteNotification(Long userId, Long chatRoomId) {
        ChatRoomUser chatRoomUser = getActiveUser(userId, chatRoomId);
        chatRoomUser.muteNotification(Instant.now());
        chatRoomUsers.save(chatRoomUser);
    }

    @Transactional
    public void unmuteNotification(Long userId, Long chatRoomId) {
        ChatRoomUser chatRoomUser = getActiveUser(userId, chatRoomId);
        chatRoomUser.unmuteNotification(Instant.now());
        chatRoomUsers.save(chatRoomUser);
    }
}
