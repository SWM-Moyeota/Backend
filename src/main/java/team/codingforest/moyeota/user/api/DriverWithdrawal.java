package team.codingforest.moyeota.user.api;

import java.time.Instant;

/**
 * 탈퇴하는 사용자의 기사 계정을 같은 트랜잭션에서 닫음
 * user 가 driver 를 직접 부르면 driver → user(UserAccess) 와 순환이 생겨서 user 쪽에 인터페이스를 둠
 */
public interface DriverWithdrawal {
    /**
     * 기사가 아니면 아무것도 하지 않음
     *
     * @param userId 탈퇴하는 사용자의 내부 번호
     * @param withdrawnAt 탈퇴 시각
     */
    void withdraw(Long userId, Instant withdrawnAt);
}
