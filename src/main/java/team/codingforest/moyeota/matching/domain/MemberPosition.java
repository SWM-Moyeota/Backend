package team.codingforest.moyeota.matching.domain;

/** 파티 멤버가 마지막으로 보고한 좌표. TTL 안에 들어온 값만 존재한다 */
public record MemberPosition(double latitude, double longitude) {
}
