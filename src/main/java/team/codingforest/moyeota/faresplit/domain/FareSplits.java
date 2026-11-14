package team.codingforest.moyeota.faresplit.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FareSplits {
	FareSplit create(FareSplit split);

	Optional<FareSplit> findByPartyId(Long partyId);

	Optional<FareSplit> findByPartyIdForUpdate(Long partyId);

	void save(FareSplit split);

	List<OpenTransfer> findOpenTransfers(Long memberId);

	List<FareShare> findShares(Long memberId, Collection<Long> partyIds);

	List<Long> findTransferRequestTargets(Instant now);

	List<Long> findExpireTargets(Instant now);

	List<Long> findOpenByPayer(Long payerId);
}
