package team.codingforest.moyeota.matching.faresplit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.api.RideFares;
import team.codingforest.moyeota.matching.api.dto.RideFare;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
class RideFareService implements RideFares {
    private final FareSplits fareSplits;

    @Transactional(readOnly = true)
    @Override
    public List<RideFare> find(Long memberId, Collection<Long> partyIds) {
        return fareSplits.findShares(memberId, partyIds).stream()
                .map(share -> new RideFare(share.partyId(), share.totalFare(), share.amountOf(memberId)))
                .toList();
    }
}
