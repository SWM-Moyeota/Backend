package team.codingforest.moyeota.chat.room.application;

import team.codingforest.moyeota.chat.member.application.ChatRoomUserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.room.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.room.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.room.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;
import team.codingforest.moyeota.chat.room.domain.PartyProvider;
import team.codingforest.moyeota.chat.room.domain.PartySnapshot;
import team.codingforest.moyeota.chat.room.domain.ChatRoomStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingChatRoomStepsTest {
    private static final Long PARTY_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");

    @Mock
    private ChatRooms chatRooms;
    @Mock
    private ChatRoomUsers chatRoomUsers;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ChatRoomUserService chatRoomUserService;
    @Mock
    private PartyProvider partyProvider;
    @InjectMocks
    private MatchingChatRoomSteps steps;

    private ChatRoom room() {
        return ChatRoom.restore(ROOM_ID, PARTY_ID, "강남역", "판교역", NOW, NOW, ChatRoomStatus.ACTIVE);
    }

    private void partyMembers(Long... userIds) {
        given(partyProvider.findSnapshot(PARTY_ID))
                .willReturn(Optional.of(new PartySnapshot(PARTY_ID, List.of(userIds), "강남역", "판교역")));
    }

    private void roomExists() {
        given(chatRooms.findByPartyIdForUpdate(PARTY_ID)).willReturn(Optional.of(room()));
    }

    private void memberInRoom(boolean in) {
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(MEMBER_ID, ROOM_ID))
                .willReturn(in ? Optional.of(ChatRoomUser.join(MEMBER_ID, ROOM_ID, NOW)) : Optional.empty());
    }

    @Test
    void 파티에_있고_채팅방에_없으면_참여시킨다() {
        partyMembers(MEMBER_ID);
        roomExists();
        memberInRoom(false);

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService).join(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
        verify(chatRoomUserService, never()).leave(any());
    }

    @Test
    void 파티에_없고_채팅방에_있으면_내보낸다() {
        partyMembers();
        roomExists();
        memberInRoom(true);

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService).leave(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
        verify(chatRoomUserService, never()).join(any());
    }

    @Test
    void 파티와_채팅방이_이미_같으면_아무것도_안_한다() {
        partyMembers(MEMBER_ID);
        roomExists();
        memberInRoom(true);

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService, never()).join(any());
        verify(chatRoomUserService, never()).leave(any());
    }

    @Test
    void 뒤늦은_참여_이벤트라도_파티에_없으면_참여시키지_않는다() {
        partyMembers();
        roomExists();
        memberInRoom(false);

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService, never()).join(any());
        verify(chatRoomUserService, never()).leave(any());
    }

    @Test
    void 방이_없고_파티에_있으면_방을_만들고_참여시킨다() {
        partyMembers(MEMBER_ID);
        given(chatRooms.findByPartyIdForUpdate(PARTY_ID)).willReturn(Optional.empty());
        given(chatRoomService.createRoom(any()))
                .willReturn(new ChatRoomResult(ROOM_ID, PARTY_ID, "강남역", "판교역", NOW, ChatRoomStatus.ACTIVE));
        memberInRoom(false);

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRoomService).createRoom(new CreateChatRoomCommand(PARTY_ID, "강남역", "판교역"));
        verify(chatRoomUserService).join(new ChatRoomCommand(ROOM_ID, MEMBER_ID, null));
    }

    @Test
    void 방이_없고_파티에도_없으면_방을_만들지_않는다() {
        partyMembers();
        given(chatRooms.findByPartyIdForUpdate(PARTY_ID)).willReturn(Optional.empty());

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRoomService, never()).createRoom(any());
        verify(chatRoomUserService, never()).join(any());
    }

    @Test
    void 파티를_찾을_수_없으면_채팅방을_건드리지_않는다() {
        given(partyProvider.findSnapshot(PARTY_ID)).willReturn(Optional.empty());

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRooms, never()).findByPartyIdForUpdate(any());
        verify(chatRoomService, never()).createRoom(any());
        verify(chatRoomUserService, never()).join(any());
        verify(chatRoomUserService, never()).leave(any());
    }

    @Test
    void 끝난_파티에_늦은_이벤트가_와도_멤버는_그대로_남는다() {
        partyMembers(MEMBER_ID);   // 파티가 끝나도 멤버 목록은 유지된다
        roomExists();
        memberInRoom(true);

        steps.sync(PARTY_ID, MEMBER_ID);

        verify(chatRoomUserService, never()).leave(any());
        verify(chatRoomUserService, never()).join(any());
    }
}