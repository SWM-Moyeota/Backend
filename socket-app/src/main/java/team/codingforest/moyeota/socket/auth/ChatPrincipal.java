package team.codingforest.moyeota.socket.auth;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP 연결에 붙는 사용자, 이름을 API 와 같게 userId 로 둬서 /user/{userId}/queue/** 목적지가 그대로 맞음
 *
 * @param userId   내부 사용자 id
 * @param publicId 공개 사용자 id
 */
public record ChatPrincipal(Long userId, UUID publicId) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
