package team.codingforest.moyeota.chat.config;

import java.security.Principal;
import java.util.UUID;

public record ChatPrincipal(Long userId, UUID publicId) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
