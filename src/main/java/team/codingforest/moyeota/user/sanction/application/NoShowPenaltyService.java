package team.codingforest.moyeota.user.sanction.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.NoShowPenalty;
import team.codingforest.moyeota.user.sanction.domain.NoShow;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanction;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanctions;
import team.codingforest.moyeota.user.sanction.domain.NoShows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 *  matching 이 노쇼를 판정한 트랜잭션(출발·자동 정리·이의 인정) 안에서 부른다 - 기본 전파(REQUIRED)라 부른 쪽 트랜잭션에 묶인다.
 *  기록·취소는 users 행부터 잠가 같은 사용자의 노쇼 기록·취소가 줄을 서게 한다 - 잠금을 잡은 뒤에 노쇼를 더하고 세야 동시에 기록된 다른 노쇼도 센다.
 *  users 행이 없으면 경고만 남기고 넘어간다: 회원 탈퇴는 행을 남기는 soft delete 라 정상 흐름에선 없을 수 없고,
 *  여기서 예외를 던지면 출발·자동 정리까지 되돌아가 방이 닫히지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
class NoShowPenaltyService implements NoShowPenalty {
    private final NoShowSanctions sanctions;
    private final NoShows noShows;

    @Transactional
    @Override
    public void record(Long userId, Long partyId, Instant meetingAt, Instant now) {
        Optional<NoShowSanction> current = sanctions.findForUpdate(userId);

        if(current.isEmpty()) {
            log.warn("노쇼 제재 대상 사용자가 없어 건너뜀 userId={}, partyId={}", userId, partyId);
            return;
        }

        if(!noShows.add(userId, partyId, meetingAt)) {
            log.info("이미 기록된 노쇼라 건너뜀 userId={}, partyId={}", userId, partyId);
            return;
        }

        List<NoShow> unexpired = noShows.findUnexpired(userId, now);
        NoShowSanction next = current.get().record(meetingAt, unexpired, now);
        sanctions.save(userId, next);

        log.info("노쇼 기록 userId={}, partyId={}, count={}, meetingAt={}, restrictedUntil={}", userId, partyId,
                NoShowSanction.count(unexpired, now), meetingAt, next.restrictedUntil());
    }

    @Transactional
    @Override
    public void cancel(Long userId, Long partyId, Instant now) {
        Optional<NoShowSanction> current = sanctions.findForUpdate(userId);

        if(current.isEmpty()) {
            log.warn("노쇼 취소 대상 사용자가 없어 건너뜀 userId={}, partyId={}", userId, partyId);
            return;
        }

        if(!noShows.markVoided(userId, partyId, now)) {
            log.warn("취소할 노쇼가 없거나 이미 무효라 건너뜀 userId={}, partyId={}", userId, partyId);
            return;
        }

        sanctions.save(userId, current.get().cancel());

        log.info("노쇼 취소 userId={}, partyId={}", userId, partyId);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<Instant> findRestrictedUntil(Long userId, Instant now) {
        return sanctions.find(userId).flatMap(sanction -> sanction.activeRestrictionUntil(now));
    }
}
