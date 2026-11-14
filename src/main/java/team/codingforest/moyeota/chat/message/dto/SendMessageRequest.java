package team.codingforest.moyeota.chat.message.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
        @NotBlank(message = "빈 문자를 전송할 수 없습니다")
        @Size(max = 1000, message = "최대 1000글자만 전송 가능합니다")
        String content,

        @Schema(description = "앱이 메시지마다 만든 UUID. 응답을 못 받아 다시 보낼 때 같은 값을 쓰면 한 번만 저장되고 처음 저장한 메시지가 그대로 온다. 없으면 중복 방지를 하지 않는다",
                example = "6f1c2b9e-3d4a-4e7b-9c1d-2a3b4c5d6e7f")
        UUID clientMessageId
) {
    public SendMessageRequest(String content) {
        this(content, null);
    }
}
