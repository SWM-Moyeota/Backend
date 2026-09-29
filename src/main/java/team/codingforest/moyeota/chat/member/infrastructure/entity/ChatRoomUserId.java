package team.codingforest.moyeota.chat.member.infrastructure.entity;

import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode
public class ChatRoomUserId implements Serializable {
    private Long userId;
    private Long chatRoomId;
}
