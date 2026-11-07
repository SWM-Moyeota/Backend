package team.codingforest.moyeota.matching.faresplit.domain;

import java.util.Optional;

public interface FareSplits {
    /** 새 정산과 송금 행을 바로 INSERT 한다 - 같은 방에 이미 있으면 DataIntegrityViolationException */
    FareSplit create(FareSplit split);

    /** 송금은 방 합류 순으로 담는다 */
    Optional<FareSplit> findByPartyId(Long partyId);

    boolean existsByPartyId(Long partyId);
}
