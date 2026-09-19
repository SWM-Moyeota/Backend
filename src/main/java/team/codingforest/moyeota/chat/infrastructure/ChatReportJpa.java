package team.codingforest.moyeota.chat.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatReport;
import team.codingforest.moyeota.chat.domain.ChatReports;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatReportEntity;

@Repository
@RequiredArgsConstructor
public class ChatReportJpa implements ChatReports {

    private final ChatReportJpaRepository jpaRepository;

    @Override
    public ChatReport save(ChatReport chatReport) {
        return jpaRepository.save(ChatReportEntity.from(chatReport)).toDomain();
    }

    @Override
    public boolean existsByUserIdAndChatMessageId(Long userId, Long chatMessageId) {
        return jpaRepository.existsByUserIdAndChatMessageId(userId, chatMessageId);
    }
}
