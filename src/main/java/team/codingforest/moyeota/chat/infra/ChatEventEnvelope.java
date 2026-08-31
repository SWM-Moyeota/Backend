package team.codingforest.moyeota.chat.infra;

public record ChatEventEnvelope(String type, String payload) {
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_ROOM_LEFT = "ROOM_LEFT";
}
