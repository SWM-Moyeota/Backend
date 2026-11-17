package team.codingforest.moyeota.chat.room.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.room.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.room.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.infrastructure.ChatRooms;
import team.codingforest.moyeota.chat.room.domain.PartyProvider;
import team.codingforest.moyeota.chat.room.domain.PartySnapshot;
import team.codingforest.moyeota.chat.room.domain.ChatRoomStatus;
import team.codingforest.moyeota.chat.common.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.common.exception.ChatException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final Long PARTY_ID = 100L;
    private static final String DEPARTURE = "서울시청";
    private static final String DESTINATION = "강남역";
    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    @Mock
    private ChatRooms chatRooms;
    @Mock
    private PartyProvider partyProvider;
    @InjectMocks
    private ChatRoomService chatRoomService;

    private ChatRoom room(ChatRoomStatus status) {
        return ChatRoom.restore(ROOM_ID, PARTY_ID, 0L, DEPARTURE, DESTINATION, NOW, NOW, status);
    }

    private CreateChatRoomCommand command() {
        return new CreateChatRoomCommand(PARTY_ID, DEPARTURE, DESTINATION);
    }

    private PartySnapshot snapshot(Long... memberIds) {
        return new PartySnapshot(PARTY_ID, List.of(memberIds), DEPARTURE, DESTINATION);
    }

    @Test
    public void 채팅방_생성_성공() {
        given(chatRooms.existsByPartyId(PARTY_ID)).willReturn(false);
        given(chatRooms.save(any(ChatRoom.class))).willReturn(room(ChatRoomStatus.ACTIVE));

        ChatRoomResult result = chatRoomService.createRoom(command());

        assertThat(result.id()).isEqualTo(ROOM_ID);
        assertThat(result.partyId()).isEqualTo(PARTY_ID);
        assertThat(result.departure()).isEqualTo(DEPARTURE);
        assertThat(result.destination()).isEqualTo(DESTINATION);
        assertThat(result.status()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    @Test
    public void 중복된_partyId_예외() {
        given(chatRooms.existsByPartyId(PARTY_ID)).willReturn(true);

        assertThatThrownBy(() -> chatRoomService.createRoom(command()))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS);
    }

    @Test
    public void 약속_확정_장소를_채팅방_출발지로_바꾼다() {
        given(chatRooms.changeDeparture(eq(PARTY_ID), eq("강남역 11번 출구"), any(Instant.class))).willReturn(1);

        chatRoomService.changeDeparture(PARTY_ID, "강남역 11번 출구");

        then(chatRooms).should().changeDeparture(eq(PARTY_ID), eq("강남역 11번 출구"), any(Instant.class));
        then(chatRooms).should(never()).save(any(ChatRoom.class));   // 방을 통째로 저장하면 상태를 옛값으로 덮어쓸 수 있다
    }

    @Test
    public void 채팅방이_아직_없으면_재발행되도록_출발지_변경은_예외() {
        given(chatRooms.changeDeparture(eq(PARTY_ID), eq("강남역 11번 출구"), any(Instant.class))).willReturn(0);

        assertThatThrownBy(() -> chatRoomService.changeDeparture(PARTY_ID, "강남역 11번 출구"))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    public void 파티가_같이_타고_끝나면_채팅방을_FINISHED_로_바꾼다() {
        given(chatRooms.endByPartyId(eq(PARTY_ID), eq(ChatRoomStatus.FINISHED), any(Instant.class))).willReturn(1);

        chatRoomService.end(PARTY_ID, "FINISHED");

        then(chatRooms).should().endByPartyId(eq(PARTY_ID), eq(ChatRoomStatus.FINISHED), any(Instant.class));
        then(chatRooms).should(never()).save(any(ChatRoom.class));   // 방을 통째로 저장하면 출발지를 옛값으로 덮어쓸 수 있다
    }

    @Test
    public void 파티가_같이_타지_못하고_끝나면_채팅방을_CANCELED_로_바꾼다() {
        given(chatRooms.endByPartyId(eq(PARTY_ID), eq(ChatRoomStatus.CANCELED), any(Instant.class))).willReturn(1);

        chatRoomService.end(PARTY_ID, "CANCELED");

        then(chatRooms).should().endByPartyId(eq(PARTY_ID), eq(ChatRoomStatus.CANCELED), any(Instant.class));
    }

    @Test
    public void 진행_중인_채팅방이_없으면_종료는_예외_없이_넘어간다() {
        given(chatRooms.endByPartyId(eq(PARTY_ID), eq(ChatRoomStatus.CANCELED), any(Instant.class))).willReturn(0);

        chatRoomService.end(PARTY_ID, "CANCELED");   // 혼자 있다 취소된 파티이거나 같은 이벤트가 다시 온 경우
    }

    @Test
    public void 채팅방_닫기_성공() {
        ChatRoom chatRoom = room(ChatRoomStatus.ACTIVE);
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        chatRoomService.close(ROOM_ID);

        assertThat(chatRoom.getStatus()).isEqualTo(ChatRoomStatus.CLOSED);
    }

    @Test
    public void 채팅방_단건_조회_성공() {
        ChatRoom chatRoom = room(ChatRoomStatus.ACTIVE);
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(partyProvider.findSnapshot(PARTY_ID)).willReturn(Optional.of(snapshot(USER_ID)));

        ChatRoomResult result = chatRoomService.findById(USER_ID, ROOM_ID);

        assertThat(result.id()).isEqualTo(ROOM_ID);
        assertThat(result.partyId()).isEqualTo(PARTY_ID);
        assertThat(result.status()).isEqualTo(ChatRoomStatus.ACTIVE);
    }

    @Test
    public void 없는_방_조회_예외() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.findById(USER_ID, ROOM_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    public void 파티원이_아니면_조회_예외() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        given(partyProvider.findSnapshot(PARTY_ID)).willReturn(Optional.of(snapshot(OTHER_USER_ID)));

        assertThatThrownBy(() -> chatRoomService.findById(USER_ID, ROOM_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTY_MEMBER);
    }

    @Test
    public void 파티_정보가_없으면_조회_예외() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.of(room(ChatRoomStatus.ACTIVE)));
        given(partyProvider.findSnapshot(PARTY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.findById(USER_ID, ROOM_ID))
                .isInstanceOf(ChatException.class)
                .extracting("errorCode")
                .isEqualTo(ChatErrorCode.CHAT_PARTY_NOT_FOUND);
    }

    @Test
    public void 방이_없으면_파티_조회를_하지_않는다() {
        given(chatRooms.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.findById(USER_ID, ROOM_ID))
                .isInstanceOf(ChatException.class);

        then(partyProvider).should(never()).findSnapshot(any());
    }
}
