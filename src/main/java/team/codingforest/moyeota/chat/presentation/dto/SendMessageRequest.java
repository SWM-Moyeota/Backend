package team.codingforest.moyeota.chat.presentation.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest (
        @NotBlank(message = "빈 문자를 전송할 수 없습니다")
        @Size(max = 1000, message = "최대 1000글자만 전송 가능합니다")
        String content
) { }
