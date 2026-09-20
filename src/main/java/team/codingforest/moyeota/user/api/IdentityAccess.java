package team.codingforest.moyeota.user.api;

/** 보호 기능에서 서버의 본인인증 상태를 확인한다. */
public interface IdentityAccess {
    void requireVerified(Long userId);
}
