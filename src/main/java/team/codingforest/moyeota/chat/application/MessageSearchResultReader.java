package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.application.dto.*;
import team.codingforest.moyeota.chat.domain.*;
import team.codingforest.moyeota.chat.domain.enums.ChatMessageType;
import team.codingforest.moyeota.chat.domain.search.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageSearchResultReader {
    private final ChatMessages messages;
    private final ChatRoomUserService participants;
    private final MemberProvider members;

    /** ES 응답 이후 새 트랜잭션에서 원본 상태와 권한을 확인한다. 색인에 남은 삭제 본문은 반환하지 않는다. */
    @Transactional(readOnly = true)
    public MessageSearchPage read(Long userId, MessageSearchQuery query, List<MessageSearchIndex.Hit> hits) {
        participants.validateParticipant(userId, query.roomId());
        boolean hasNext = hits.size() > query.size();
        var scanned = hits.stream().limit(query.size()).toList();
        Map<Long, ChatMessage> originals = messages.findByIds(scanned.stream().map(MessageSearchIndex.Hit::id).toList())
                .stream().collect(Collectors.toMap(ChatMessage::getId, m -> m));
        var valid = scanned.stream().filter(hit -> eligible(originals.get(hit.id()), query)
                && Objects.equals(originals.get(hit.id()).getContent(), hit.indexedContent())).toList();
        var senders = members.findMembers(valid.stream().map(hit -> originals.get(hit.id()).getUserId()).distinct().toList());
        var items = valid.stream().map(hit -> {
            ChatMessage message = originals.get(hit.id());
            ChatMember sender = senders.get(message.getUserId());
            return new MessageSearchPage.Item(ChatMessageResult.from(message, sender == null ? null : sender.publicId()), hit.highlight());
        }).toList();
        // 걸러진 메시지가 있어도 마지막 스캔 위치를 반환하여 빈 페이지에서 커서가 멈추지 않는다.
        Long cursor = scanned.isEmpty() ? null : scanned.getLast().id();
        return new MessageSearchPage(items, cursor, hasNext);
    }

    private boolean eligible(ChatMessage m, MessageSearchQuery q) {
        return m != null && !m.isDeleted() && m.getType() == ChatMessageType.TEXT
                && m.getChatRoomId().equals(q.roomId())
                && (q.senderId() == null || q.senderId().equals(m.getUserId()))
                && (q.cursor() == null || m.getId() < q.cursor())
                && (q.from() == null || !m.getCreatedAt().isBefore(q.from()))
                && (q.until() == null || m.getCreatedAt().isBefore(q.until()));
    }
}
