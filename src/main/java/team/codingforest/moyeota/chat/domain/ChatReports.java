package team.codingforest.moyeota.chat.domain;

public interface ChatReports {
    ChatReport save(ChatReport chatReport);

    /**
     * 메시지 중복 신고 불가
     */
    boolean existsByUserIdAndChatMessageId(Long userId, Long chatMessageId);
}
