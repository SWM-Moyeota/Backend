package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.domain.LocationShare;
import team.codingforest.moyeota.matching.domain.LocationShares;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;

import java.time.Instant;
import java.util.List;

// TODO 예외처리 해야함
@Service
@Slf4j
@RequiredArgsConstructor
public class LocationShareService {
    private final LocationShares locationShares;
    private final Parties parties;

    @Transactional
    public void start(Long partyId, Long memberId) {
        Party party = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방이 존재하지 않음"));

        if(!party.hasMember(memberId)) {
            throw new IllegalArgumentException("해당 방에 입장하지 않았음 memberId=" + memberId);
        }

        // 이미 공유하는 경우
        if(locationShares.findOngoing(partyId, memberId).isPresent()) {
            return;
        }

        locationShares.save(LocationShare.start(partyId, memberId, Instant.now()));
        log.info("위치 공유 시작 partyId={}, memberId={}", partyId, memberId);
    }

    @Transactional
    public void stop(Long partyId, Long memberId) {
        LocationShare share = locationShares.findOngoing(partyId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("이미 위치공유를 하지 않습니다."));

        share.stop(Instant.now());
        locationShares.save(share);
        log.info("위치 공유 종료 partyId={}, memberId={}", partyId, memberId);
    }

    @Transactional
    public List<Long> findSharingMemberIds(Long partyId) {
        return locationShares.findAllOngoingByPartyId(partyId)
                .stream().map(LocationShare::getMemberId)
                .toList();
    }
}
