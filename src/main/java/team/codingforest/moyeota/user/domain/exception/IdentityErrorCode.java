package team.codingforest.moyeota.user.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum IdentityErrorCode implements ErrorCode {
    REQUIRED(HttpStatus.FORBIDDEN, "IDENTITY001", "본인인증이 필요합니다."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "IDENTITY002", "본인인증 요청을 찾을 수 없습니다."),
    NOT_VERIFIED(HttpStatus.CONFLICT, "IDENTITY003", "아직 본인인증이 완료되지 않았습니다."),
    INVALID_RESULT(HttpStatus.BAD_GATEWAY, "IDENTITY004", "본인인증 결과를 검증할 수 없습니다."),
    EXPIRED(HttpStatus.GONE, "IDENTITY005", "본인인증 유효 시간을 초과했습니다. 새 인증을 시작해 주세요."),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "IDENTITY006", "이미 본인인증된 계정입니다."),
    IDENTITY_IN_USE(HttpStatus.CONFLICT, "IDENTITY007", "이미 다른 계정에 연결된 본인인증 정보입니다."),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY008", "인증 결과 조회에 실패했습니다. 같은 요청으로 다시 시도해 주세요."),
    NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY009", "본인인증 서비스 설정이 준비되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
