package team.codingforest.moyeota.chat.contract;

/**
 * 클라이언트가 구독하는 STOMP 목적지를 한곳에서 만듦
 * 앱이 이미 이 주소를 구독하고 있어 값을 바꾸면 앱도 같이 바꿔야 함
 */
public final class ChatDestinations {

    public static final String ROOM_PREFIX = "/sub/chat-rooms/";

    private static final String USER_PREFIX = "/user/";
    private static final String ROOM_LEFT = "/queue/room-left";

    private ChatDestinations() {
    }

    /**
     * 방 메시지 목적지를 만듦
     *
     * @param chatRoomId 채팅방 id
     * @return /sub/chat-rooms/{chatRoomId}
     */
    public static String room(Long chatRoomId) {
        return ROOM_PREFIX + chatRoomId;
    }

    /**
     * 방 위치 공유 목적지를 만듦
     *
     * @param chatRoomId 채팅방 id
     * @return /sub/chat-rooms/{chatRoomId}/location
     */
    public static String location(Long chatRoomId) {
        return room(chatRoomId) + "/location";
    }

    /**
     * 방 멤버 변경 목적지를 만듦
     *
     * @param chatRoomId 채팅방 id
     * @return /sub/chat-rooms/{chatRoomId}/members
     */
    public static String members(Long chatRoomId) {
        return room(chatRoomId) + "/members";
    }

    /**
     * 방을 나간 본인에게만 보내는 목적지를 만듦
     *
     * @param userId 방을 나간 사용자 id
     * @return /user/{userId}/queue/room-left
     */
    public static String roomLeft(Long userId) {
        return USER_PREFIX + userId + ROOM_LEFT;
    }
}
