package team.codingforest.moyeota.matching.faresplit.domain;

/**
 *  송금 한 건의 상태. 컬럼으로 저장하지 않고 시각으로 가른다 - confirmed_at 이 있으면 CONFIRMED, 없고 sent_at 이 있으면 SENT, 둘 다 없으면 NOT_SENT
 */
public enum TransferStatus {
    NOT_SENT,
    SENT,
    CONFIRMED
}
