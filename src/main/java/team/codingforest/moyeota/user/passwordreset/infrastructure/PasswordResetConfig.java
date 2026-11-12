package team.codingforest.moyeota.user.passwordreset.infrastructure;

import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class PasswordResetConfig {
 @Bean public Clock passwordResetClock() { return Clock.systemUTC(); }
}
