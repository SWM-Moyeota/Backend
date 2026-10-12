package team.codingforest.moyeota.user.deletion.domain;

import java.time.Duration;
import java.time.Instant;

public interface UserDeletions {
    /**
     * 탈퇴한 번호의 재가입을 막고 기록을 보관하는 기간
     * 재가입 제한과 기록 삭제가 같은 값을 써야 어긋나지 않음
     */
    Duration RETENTION = Duration.ofDays(30);

    void record(String normalizedPhoneNumber, Instant deletedAt);
    boolean existsDeletedSince(String normalizedPhoneNumber, Instant since);
    int deleteRecordedBefore(Instant before);
}
