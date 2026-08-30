package team.codingforest.moyeota.dispatch.domain;

/**
 *  기사 현재 위치 - 필드명으로 위경도 순서 실수를 막는다
 */
public record DriverPosition(double latitude, double longitude) {
}
