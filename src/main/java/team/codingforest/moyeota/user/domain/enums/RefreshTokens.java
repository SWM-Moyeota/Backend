package team.codingforest.moyeota.user.domain.enums;

import team.codingforest.moyeota.user.domain.RefreshToken;

import java.util.List;
import java.util.UUID;

public interface RefreshTokens {
    void save(RefreshToken token);

    List<RefreshToken> findByPublicId(UUID publicId);
}
