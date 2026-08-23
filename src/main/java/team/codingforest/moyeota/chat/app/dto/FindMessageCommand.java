package team.codingforest.moyeota.chat.app.dto;

import java.io.Serializable;

public record FindMessageCommand(Long userId, Long chatRoomId, Long cursor, int size){
}
