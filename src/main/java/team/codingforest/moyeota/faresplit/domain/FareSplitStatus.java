package team.codingforest.moyeota.faresplit.domain;

/**
 *  정산 상태. OPEN 에서 모든 송금이 확인되면 COMPLETED, 만든 지 1일이 지나면 EXPIRED 로 한 번 넘어간다.
 */
public enum FareSplitStatus {
    OPEN,
    COMPLETED,
    EXPIRED
}
