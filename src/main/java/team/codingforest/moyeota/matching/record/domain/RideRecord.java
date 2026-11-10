package team.codingforest.moyeota.matching.record.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 *  탑승 기록 - 방이 FINISHED 로 끝날 때 방마다 한 줄 남는다. 누가 탔는지는 그 방에 도착한 멤버로 조회할 때 정한다.
 *  하차 시각은 서버가 알 수 없어 남기지 않고, 승차 시각(pickupTime)만 남긴다
 */
public class RideRecord {
    private final Long matchId;
    private final String departurePlace;
    private final String destinationPlace;
    private final Instant pickupTime;

    private RideRecord(Long matchId, String departurePlace, String destinationPlace, Instant pickupTime) {
        this.matchId = matchId;
        this.departurePlace = departurePlace;
        this.destinationPlace = destinationPlace;
        this.pickupTime = pickupTime;
    }

    /**
     *  @param pickupTime 승차 시각 - 방장이 출발을 누른 순간, 자동 정리로 끝난 방은 약속 시각.
     *                    DB 가 마이크로초까지만 저장하므로 잘라서 둔다 - 커서 비교가 저장 값과 어긋나지 않게
     */
    public static RideRecord create(Long matchId, String departurePlace, String destinationPlace, Instant pickupTime) {
        return new RideRecord(matchId, departurePlace, destinationPlace, pickupTime.truncatedTo(ChronoUnit.MICROS));
    }

    public static RideRecord load(Long matchId, String departurePlace, String destinationPlace, Instant pickupTime) {
        return new RideRecord(matchId, departurePlace, destinationPlace, pickupTime);
    }

	public Long getMatchId() {
		return matchId;
	}

	public String getDeparturePlace() {
		return departurePlace;
	}

	public String getDestinationPlace() {
		return destinationPlace;
	}

	public Instant getPickupTime() {
		return pickupTime;
	}
}
