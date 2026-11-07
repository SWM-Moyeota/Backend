package team.codingforest.moyeota.faresplit.domain;

/**
 *  정산 상태. OPEN 에서 모든 송금이 확인되면 COMPLETED, 만든 지 7일이 지나면 EXPIRED 로 한 번 넘어간다.
 *  취소는 상태가 아니라 행을 지운다. 닫히면(COMPLETED·EXPIRED) 계좌번호를 지운다
 */
public enum FareSplitStatus {
    OPEN,
    COMPLETED,
    EXPIRED
}
