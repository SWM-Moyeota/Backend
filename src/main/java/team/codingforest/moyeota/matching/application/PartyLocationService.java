package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.application.dto.MemberLocationResult;
import team.codingforest.moyeota.matching.domain.MemberLocations;
import team.codingforest.moyeota.matching.domain.MemberPosition;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.PartyMember;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;

/**
 * 동승자끼리 보는 실시간 위치.
 *
 * 기사 위치(dispatch)와 목적이 다르다 — 이건 **탑승 지점에서 서로를 찾기 위한 것**이라,
 * 방이 끝난 뒤에는 쓸모가 없고 남아서도 안 된다. 그래서 좌표는 DB 가 아니라 TTL 이 붙은
 * Redis 에만 두고([MemberLocations]), 읽기는 항상 "그 방의 멤버인가"를 먼저 확인한다.
 *
 * 내 위치는 결과에서 뺀다 — 앱은 기기 GPS 로 자기 점을 이미 그리고 있고, 서버를 한 바퀴 돈
 * 낡은 좌표를 겹쳐 그리면 점이 둘로 보인다.
 */
@Service
@RequiredArgsConstructor
public class PartyLocationService {

    private final Parties parties;
    private final MemberLocations memberLocations;
    private final UserAccess userAccess;

    @Transactional(readOnly = true)
    public void report(Long partyId, Long memberId, double latitude, double longitude) {
        requireMember(partyId, memberId);

        memberLocations.update(partyId, memberId, latitude, longitude);
    }

    /** 나를 뺀 동승자들의 최근 위치. 보고가 없거나 TTL 이 지난 멤버는 빠진다 */
    @Transactional(readOnly = true)
    public List<MemberLocationResult> findOthers(Long partyId, Long memberId) {
        Party party = requireMember(partyId, memberId);

        List<Long> others = party.getMembers().stream()
                .map(PartyMember::getMemberId)
                .filter(id -> !id.equals(memberId))
                .toList();
        if (others.isEmpty()) return List.of();

        Map<Long, MemberPosition> positions = memberLocations.findAll(partyId, others);
        if (positions.isEmpty()) return List.of();

        Map<Long, MemberSummary> summaries = userAccess.findMemberSummaries(List.copyOf(positions.keySet()));

        return positions.entrySet().stream()
                .map(entry -> toResult(entry.getKey(), entry.getValue(), summaries.get(entry.getKey())))
                .toList();
    }

    private MemberLocationResult toResult(Long memberId, MemberPosition position, MemberSummary summary) {
        return new MemberLocationResult(
                summary == null ? null : summary.publicId(),
                summary == null ? null : summary.nickname(),
                position.latitude(),
                position.longitude());
    }

    private Party requireMember(Long partyId, Long memberId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        if (!party.hasMember(memberId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

        return party;
    }
}
