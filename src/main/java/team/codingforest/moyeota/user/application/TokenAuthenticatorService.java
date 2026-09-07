package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.api.TokenAuthenticator;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenAuthenticatorService implements TokenAuthenticator {

    private final AuthService authService;

    @Override
    public Optional<AuthenticatedPrincipal> authenticate(String accessToken) {
        try {
            return Optional.of(authService.authenticate(accessToken));
        } catch (UserException e) {
            return Optional.empty();
        }
    }
}