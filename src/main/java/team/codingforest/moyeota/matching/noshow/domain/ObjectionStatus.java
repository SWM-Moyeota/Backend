package team.codingforest.moyeota.matching.noshow.domain;

/**
 *  노쇼 이의 신청 상태. PENDING 에서만 ACCEPTED·REJECTED 로 한 번 넘어간다
 */
public enum ObjectionStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
