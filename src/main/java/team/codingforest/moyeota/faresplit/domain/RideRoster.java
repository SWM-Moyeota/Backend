package team.codingforest.moyeota.faresplit.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.util.List;

public record RideRoster(Long partyId, Long hostId, boolean finished, List<Long> memberIds, List<Long> riderIds) {
    static final int MIN_RIDERS = 2;

    public void validateStarter(Long memberId) {
        if (!isHost(memberId)) {
            throw new BusinessException(FareSplitErrorCode.NOT_PARTY_HOST);
        }

        if (!finished) {
            throw new BusinessException(FareSplitErrorCode.PARTY_NOT_FINISHED);
        }

        // 아무도 출발을 누르지 않아 자동 정리된 방은 방장이 타지 않았을 수 있다
        if (!isRider(memberId)) {
            throw new BusinessException(FareSplitErrorCode.NOT_A_RIDER);
        }

        if (riderIds.size() < MIN_RIDERS) {
            throw new BusinessException(FareSplitErrorCode.NOT_ENOUGH_RIDERS);
        }
    }

    public boolean isHost(Long memberId) {
        return hostId != null && hostId.equals(memberId);
    }

    public boolean isRider(Long memberId) {
        return riderIds.contains(memberId);
    }
}
