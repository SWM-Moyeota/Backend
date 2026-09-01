package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.user.application.dto.RefreshTokenResponse;
import team.codingforest.moyeota.user.domain.RefreshToken;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;
import team.codingforest.moyeota.user.domain.enums.RefreshTokens;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokens refreshTokens;
    private final Users users;

//    public RefreshTokenResponse issueToken(UUID uuid) {
//        RefreshToken token = RefreshToken.create()
//    }
}
