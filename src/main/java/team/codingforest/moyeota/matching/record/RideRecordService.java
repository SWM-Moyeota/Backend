package team.codingforest.moyeota.matching.record;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RideRecordService {
    static final int MIN_PAGE_SIZE = 1;
    static final int MAX_PAGE_SIZE = 50;

    private final RideRecords rideRecords;
    private final UserAccess userAccess;

    @Transactional(readOnly = true)
    public RideRecordResponse findMyRides(Long memberId, Long cursor, int size) {
        if(size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) throw new BusinessException(MatchingErrorCode.INVALID_PAGE_SIZE);

        List<RideRecord> fetched = fetch(memberId, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<RideRecord> page = hasNext ? fetched.subList(0, size) : fetched;

        if(page.isEmpty()) return RideRecordResponse.of(List.of(), Map.of(), Map.of(), null, false);

        Map<Long, List<Long>> memberIds = rideRecords.findMemberIds(page.stream().map(RideRecord::getMatchId).toList());
        List<Long> allMemberIds = memberIds.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, MemberSummary> summaries = userAccess.findMemberSummaries(allMemberIds);

        Long nextCursor = hasNext ? page.getLast().getMatchId() : null;

        return RideRecordResponse.of(page, memberIds, summaries, nextCursor, hasNext);
    }

    private List<RideRecord> fetch(Long memberId, Long cursor, int limit) {
        if(cursor == null) return rideRecords.findDroppedOffByMember(memberId, limit);

        RideRecord last = rideRecords.findById(cursor)
                .filter(RideRecord::isDroppedOff)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.INVALID_CURSOR));

        return rideRecords.findDroppedOffByMemberBefore(memberId, last.getDropoffTime(), last.getMatchId(), limit);
    }
}
