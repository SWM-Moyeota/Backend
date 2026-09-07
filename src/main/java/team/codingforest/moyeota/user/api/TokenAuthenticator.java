package team.codingforest.moyeota.user.api;

import java.util.Optional;

public interface TokenAuthenticator {
    Optional<AuthenticatedPrincipal> authenticate(String accessToken);
}