package team.codingforest.moyeota.chat.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.chat.domain.*;
import team.codingforest.moyeota.chat.domain.enums.*;
import team.codingforest.moyeota.chat.domain.exception.*;
import team.codingforest.moyeota.chat.domain.search.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageSearchServiceTest {
    private final ChatRoomUserService participants = mock(ChatRoomUserService.class);
    private final MessageSearchIndex index = mock(MessageSearchIndex.class);
    private final ChatMessages messages = mock(ChatMessages.class);
    private final MemberProvider members = mock(MemberProvider.class);
    private final MessageSearchResultReader reader = new MessageSearchResultReader(messages, participants, members);
    private final MessageSearchService service = new MessageSearchService(participants, index, reader);
    private final MessageSearchQuery query = new MessageSearchQuery(1L, "출구", null, null, null, null, 2);
    private final Instant now = Instant.parse("2026-09-20T00:00:00Z");

    @Test void 비참여자는_검색엔진을_호출하지_않는다() {
        doThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT)).when(participants).validateParticipant(7L, 1L);
        assertThatThrownBy(() -> service.search(7L, query)).isInstanceOf(ChatException.class);
        verifyNoInteractions(index, messages);
    }

    @Test void 검색_도중_퇴장했으면_응답을_차단한다() {
        doNothing().doThrow(new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .when(participants).validateParticipant(7L, 1L);
        when(index.search(query)).thenReturn(List.of());
        assertThatThrownBy(() -> service.search(7L, query)).isInstanceOf(ChatException.class);
        verifyNoInteractions(messages);
    }

    @Test void 색인에_남은_삭제본문과_다른방_메시지를_제거하고_커서는_진행한다() {
        var deleted = message(30L, 1L, "출구"); deleted.delete(now);
        when(messages.findByIds(List.of(30L, 29L))).thenReturn(List.of(deleted, message(29L, 2L, "출구")));
        var result = reader.read(7L, query, List.of(hit(30L, "출구"), hit(29L, "출구"), hit(28L, "출구")));
        assertThat(result.messages()).isEmpty();
        assertThat(result.nextCursor()).isEqualTo(29L);
        assertThat(result.hasNext()).isTrue();
    }

    @Test void 변경전_본문의_강조는_노출하지_않는다() {
        when(messages.findByIds(List.of(30L))).thenReturn(List.of(message(30L, 1L, "수정한 내용")));
        assertThat(reader.read(7L, query, List.of(hit(30L, "출구"))).messages()).isEmpty();
    }

    @Test void 현재_메시지와_발신자_publicId를_반환한다() {
        UUID publicId = UUID.randomUUID();
        when(messages.findByIds(List.of(30L))).thenReturn(List.of(message(30L, 1L, "출구")));
        when(members.findMembers(List.of(7L))).thenReturn(Map.of(7L, new ChatMember(7L, publicId, "회원", null)));
        var result = reader.read(7L, query, List.of(hit(30L, "출구")));
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().getFirst().message().publicId()).isEqualTo(publicId);
        assertThat(result.messages().getFirst().highlight()).isEqualTo("<mark>출구</mark>");
        assertThat(result.hasNext()).isFalse();
    }

    @Test void 기간과_작성자_필터를_DB_원본에도_적용한다() {
        when(messages.findByIds(List.of(30L))).thenReturn(List.of(message(30L, 1L, "출구")));
        var filtered = new MessageSearchQuery(1L, "출구", 9L, now, now.plusSeconds(1), null, 2);
        assertThat(reader.read(7L, filtered, List.of(hit(30L, "출구"))).messages()).isEmpty();
        var until = new MessageSearchQuery(1L, "출구", null, null, now, null, 2);
        assertThat(reader.read(7L, until, List.of(hit(30L, "출구"))).messages()).isEmpty();
    }

    @Test void 잘못된_검색_범위와_과도한_크기를_거부한다() {
        assertThatThrownBy(() -> new MessageSearchQuery(1L, "출구", null, now, now, null, 2)).isInstanceOf(ChatException.class);
        assertThatThrownBy(() -> new MessageSearchQuery(1L, "출구", null, null, null, 0L, 2)).isInstanceOf(ChatException.class);
        assertThatThrownBy(() -> new MessageSearchQuery(1L, "출구", null, null, null, null, 101)).isInstanceOf(ChatException.class);
        assertThatThrownBy(() -> new MessageSearchQuery(1L, " ", null, null, null, null, 2)).isInstanceOf(ChatException.class);
    }

    private ChatMessage message(Long id, Long roomId, String content) {
        return ChatMessage.restore(id, roomId, 7L, content, ChatMessageType.TEXT, ChatMessageStatus.ACTIVE, now, null);
    }
    private MessageSearchIndex.Hit hit(Long id, String text) {
        return new MessageSearchIndex.Hit(id, text, "<mark>" + text + "</mark>");
    }
}
