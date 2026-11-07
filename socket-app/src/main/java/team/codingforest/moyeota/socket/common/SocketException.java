package team.codingforest.moyeota.socket.common;

import lombok.Getter;

/**
 * 연결·구독을 거절할 때 던짐, ERROR 프레임 처리기가 코드를 꺼내 앱에 알림
 */
@Getter
public class SocketException extends RuntimeException {
    private final SocketErrorCode errorCode;

    /**
     * 거절 사유 코드로 예외를 만듦
     *
     * @param errorCode 앱에 알릴 고정 코드
     */
    public SocketException(SocketErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }
}
