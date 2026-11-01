package team.codingforest.moyeota.chat.contract;

/**
 * API 서버가 Redis 로 보내고 소켓 서버가 받아 그대로 전달하는 채팅 이벤트
 * 소켓 서버는 payload 를 열어보지 않고 destination 으로만 보냄
 *
 * @param type        이벤트 종류, 소켓 서버는 ROOM_LEFT 일 때만 구독 정리에 씀
 * @param destination 클라이언트에 보낼 STOMP 목적지
 * @param userId      방을 나간 사용자 id, ROOM_LEFT 가 아니면 null
 * @param chatRoomId  나간 채팅방 id, ROOM_LEFT 가 아니면 null
 * @param payload     클라이언트에 그대로 보낼 JSON 문자열
 */
public record ChatEventEnvelope(
        String type,
        String destination,
        Long userId,
        Long chatRoomId,
        String payload
) {

    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_ROOM_LEFT = "ROOM_LEFT";
    public static final String TYPE_LOCATION = "LOCATION";
    public static final String TYPE_MEMBER = "MEMBER";

    /**
     * 방 구독자 전체에게 보내는 이벤트를 만듦
     *
     * @param type        이벤트 종류
     * @param destination 클라이언트에 보낼 STOMP 목적지
     * @param payload     클라이언트에 그대로 보낼 JSON 문자열
     * @return 보낼 사람 지정 없는 봉투
     */
    public static ChatEventEnvelope of(String type, String destination, String payload) {
        return new ChatEventEnvelope(type, destination, null, null, payload);
    }

    /**
     * 방을 나간 사용자에게 알리고 소켓 서버가 그 사람의 방 구독을 끊게 하는 이벤트를 만듦
     *
     * @param userId     방을 나간 사용자 id
     * @param chatRoomId 나간 채팅방 id
     * @param payload    클라이언트에 그대로 보낼 JSON 문자열
     * @return 받는 사람과 방이 채워진 봉투
     */
    public static ChatEventEnvelope roomLeft(Long userId, Long chatRoomId, String payload) {
        return new ChatEventEnvelope(
                TYPE_ROOM_LEFT, ChatDestinations.roomLeft(userId), userId, chatRoomId, payload);
    }
}
