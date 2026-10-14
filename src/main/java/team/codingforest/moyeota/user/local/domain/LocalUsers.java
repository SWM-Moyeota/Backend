package team.codingforest.moyeota.user.local.domain;

import java.util.Optional;

public interface LocalUsers {
    void register(Long userId, String loginId, String password);
    LocalUser findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    Optional<LocalUser> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
