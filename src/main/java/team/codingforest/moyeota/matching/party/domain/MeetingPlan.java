package team.codingforest.moyeota.matching.party.domain;

import java.time.Instant;

/**
 *  방장이 확정하는 약속 - 약속 시간과 만남 장소. 장소를 바꾸지 않았으면 지금 출발지가 그대로 담겨 온다
 */
public record MeetingPlan(Instant departureAt, Location departure, String departurePlace) {
}
