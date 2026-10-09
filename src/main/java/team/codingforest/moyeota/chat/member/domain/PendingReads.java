package team.codingforest.moyeota.chat.member.domain;

import java.util.List;

public interface PendingReads {

    /**
     * 같은 방은 가장 큰 값만 남김
     *
     * @param position 읽음 위치
     * @param maxRooms 한 사람이 모아 둘 수 있는 방 수. 넘으면 새 방은 쌓지 않음
     * @return 쌓았으면 true, 상한에 걸렸으면 false
     */
    boolean record(ReadPosition position, int maxRooms);

    /**
     * 모아 둔 사람을 꺼냄. 서버 여러 대가 동시에 불러도 같은 사람이 두 번 나오지 않음
     *
     * @param count 한 번에 꺼낼 최대 인원
     * @return 꺼낸 사용자 id
     */
    List<Long> popUsers(int count);

    /**
     * 그 사람 것을 꺼내고 비움
     *
     * @param userId 꺼낼 사람
     * @return 방별 읽음 위치. 없으면 빈 목록
     */
    List<ReadPosition> take(Long userId);

    long countUsers();
}