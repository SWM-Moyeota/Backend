package team.codingforest.moyeota.socket.common;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocketErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SocketErrorHandler handler = new SocketErrorHandler(objectMapper);

    @Test
    void 감싸인_거절_사유를_message_헤더와_JSON_본문에_싣는다() {
        Message<byte[]> error = handler.handleClientMessageProcessingError(
                clientMessage(null), delivery(new SocketException(SocketErrorCode.TOKEN_EXPIRED)));

        StompHeaderAccessor accessor = accessor(error);
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("TOKEN_EXPIRED");
        assertThat(accessor.getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON);
        assertThat(body(error))
                .containsEntry("code", "TOKEN_EXPIRED")
                .containsEntry("message", SocketErrorCode.TOKEN_EXPIRED.getMessage());
    }

    @Test
    void 알_수_없는_예외는_SERVER_UNAVAILABLE_로_알린다() {
        Message<byte[]> error = handler.handleClientMessageProcessingError(
                clientMessage(null), delivery(new IllegalStateException("boom")));

        assertThat(accessor(error).getMessage()).isEqualTo("SERVER_UNAVAILABLE");
        assertThat(body(error)).containsEntry("code", "SERVER_UNAVAILABLE");
    }

    @Test
    void 클라이언트가_보낸_receipt_를_돌려준다() {
        Message<byte[]> error = handler.handleClientMessageProcessingError(
                clientMessage("receipt-1"), delivery(new SocketException(SocketErrorCode.NOT_PARTICIPANT)));

        assertThat(accessor(error).getReceiptId()).isEqualTo("receipt-1");
    }

    // 인터셉터가 던진 예외는 채널이 MessageDeliveryException 으로 감싸서 넘김
    private static MessageDeliveryException delivery(Throwable cause) {
        return new MessageDeliveryException(MessageBuilder.withPayload(new byte[0]).build(), cause);
    }

    private static Message<byte[]> clientMessage(String receipt) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (receipt != null) {
            accessor.setReceipt(receipt);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompHeaderAccessor accessor(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(Message<byte[]> message) {
        return objectMapper.readValue(message.getPayload(), Map.class);
    }
}
