package team.codingforest.moyeota.matching.faresplit.domain;

import java.util.List;
import java.util.Optional;

public interface FareSplits {
    /** 새 정산과 송금 행을 바로 INSERT 한다 - 같은 방에 이미 있으면 DataIntegrityViolationException */
    FareSplit create(FareSplit split);

    /** 송금은 방 합류 순으로 담는다 */
    Optional<FareSplit> findByPartyId(Long partyId);

    /** 정산 행을 잠그고(PESSIMISTIC_WRITE) 읽는다 - 정산을 바꾸는 일은 모두 이걸로 시작해 같은 정산의 변경을 줄 세운다 */
    Optional<FareSplit> findByPartyIdForUpdate(Long partyId);

    boolean existsByPartyId(Long partyId);

    /** 이미 있는 정산의 값(택시비·계좌·상태)과 송금 시각을 옮긴다. 송금 행을 늘리거나 줄이지 않는다 */
    void save(FareSplit split);

    /** 정산과 송금 행을 지운다(취소) */
    void delete(Long partyId);

    /** 내가 보내는 사람인 OPEN 정산, 시작 시각 최신순 */
    List<OpenTransfer> findOpenTransfers(Long memberId);
}
