package team.codingforest.moyeota.chat.contract;

/**
 * API 서버가 발행하고 소켓 서버가 구독하는 Redis 채널 이름을 모아 둠
 */
public final class ChatChannels {

    public static final String CHAT = "chat_channel";

    private ChatChannels() {
    }
}
