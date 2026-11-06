package team.codingforest.moyeota.chat.member.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.room.dto.ChatRoomCommand;
import team.codingforest.moyeota.user.api.UserDeletedEvent;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomUserDeletionListener {
    private final ChatRoomUsers chatRoomUsers;
    private final ChatRoomUserService chatRoomUserService;

    /**
     * 탈퇴한 사용자를 참여 중인 채팅방에서 전부 내보냄
     * 진행 중인 합승이 있으면 탈퇴가 막히므로 여기 남은 방은 끝난 합승 방뿐임
     * 방마다 leave 가 자기 트랜잭션이라 중간에 실패해 재발행돼도 아직 남은 방만 다시 돎
     *
     * @param event 탈퇴한 사용자 정보
     */
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(UserDeletedEvent event) {
        List<ChatRoomUser> rooms = chatRoomUsers.findActiveByUserId(event.userId());

        for (ChatRoomUser room : rooms) {
            chatRoomUserService.leave(new ChatRoomCommand(room.getChatRoomId(), event.userId(), event.publicId()));
        }

        log.info("탈퇴한 사용자를 채팅방에서 내보냄 userId={}, rooms={}", event.userId(), rooms.size());
    }
}
