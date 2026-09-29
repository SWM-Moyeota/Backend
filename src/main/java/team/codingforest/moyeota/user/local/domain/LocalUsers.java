package team.codingforest.moyeota.user.local.domain;

public interface LocalUsers {
    void register(Long userId, String loginId, String password);
    LocalUser findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}
