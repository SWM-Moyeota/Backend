package team.codingforest.moyeota.user.domain;

import java.util.UUID;

/** UUID 생성 포트 (user publicId, refresh jti). 구현(UUID v7 라이브러리)은 infrastructure 에. PasswordHasher 와 같은 패턴 */
public interface IdGenerator {
    UUID generate();
}
