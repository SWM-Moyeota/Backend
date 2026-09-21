package team.codingforest.moyeota.chat.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatReportEntity;

public interface ChatReportJpaRepository extends JpaRepository<ChatReportEntity, Long> {
    boolean existsByUserIdAndChatMessageId(Long userId, Long chatMessageId);
}
