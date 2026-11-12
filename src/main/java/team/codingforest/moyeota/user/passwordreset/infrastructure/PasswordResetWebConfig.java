package team.codingforest.moyeota.user.passwordreset.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import team.codingforest.moyeota.user.passwordreset.presentation.PasswordResetRequestFilter;
import team.codingforest.moyeota.user.passwordreset.domain.ResetRequests;
import team.codingforest.moyeota.common.crypto.FieldHasher;

@Configuration
public class PasswordResetWebConfig {
    @Bean public PasswordResetRequestFilter passwordResetRequestFilter(ResetRequests requests, FieldHasher hasher) {
        return new PasswordResetRequestFilter(requests, hasher);
    }
}
