package team.codingforest.moyeota.user.local.domain;

import java.util.Optional;

public interface LocalUsers {
    void register(Long userId, String loginId, String password);
    /** 잠금 순서(회원 → 로그인 수단)를 정하려고 id 만 읽는다. 엔티티를 읽으면 뒤의 잠금 조회가 이 값을 그대로 돌려준다. */
    Long findUserIdByLoginId(String loginId);
    LocalUser findByLoginIdForUpdate(String loginId);
    Optional<LocalUser> findByUserIdForUpdate(Long userId);
    boolean existsByLoginId(String loginId);
    Optional<LocalUser> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
