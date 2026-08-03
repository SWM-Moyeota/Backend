package team.codingforest.moyeota.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.Getter;

import java.time.Instant;

@IdClass(ChatRoomUserId.class)
@Entity
@Getter
public class ChatRoomUser {

    @Id
    private Long userId;

    @Id
    private Long chatRoomId;

    private Long lastReadMessageId;

    private boolean notificationMuted;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant leftAt;

}
