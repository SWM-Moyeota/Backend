package team.codingforest.moyeota.matching.faresplit.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FareSplits {
    /** 새 정산과 송금 행을 바로 INSERT 한다 - 같은 방에 이미 있으면(PK 중복) DuplicateKeyException, 다른 무결성 위반은 DataIntegrityViolationException */
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

    /** partyIds 중 정산이 있고 memberId 가 받는 사람이거나 보내는 사람인 것(상태 무관) - 탑승 기록 한 페이지를 한 번에 읽는다 */
    List<FareShare> findShares(Long memberId, Collection<Long> partyIds);

    /** now 에 송금 재요청 차례(TransferRequestSchedule 2..4번째)가 지난, 확인되지 않은 송금이 있는 OPEN 정산(받을 계좌가 있는 것만) */
    List<Long> findTransferRequestTargets(Instant now);

    /** 보냈다고 누른 지 CONFIRM_REQUEST_AFTER 가 지나도 확인되지 않았고 아직 입금 확인을 요청하지 않은 송금이 있는 OPEN 정산 */
    List<Long> findConfirmRequestTargets(Instant now);

    /** 시작하고 EXPIRES_AFTER 가 지난 OPEN 정산 */
    List<Long> findExpireTargets(Instant now);

    /** payerId 가 받는 사람인 OPEN 정산 */
    List<Long> findOpenByPayer(Long payerId);
}
