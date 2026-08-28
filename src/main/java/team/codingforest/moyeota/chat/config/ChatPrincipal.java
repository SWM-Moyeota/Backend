package team.codingforest.moyeota.chat.config;

import java.security.Principal;

public record ChatPrincipal(Long userId) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
