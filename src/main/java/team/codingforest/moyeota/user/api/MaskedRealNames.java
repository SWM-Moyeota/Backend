package team.codingforest.moyeota.user.api;

import java.util.List;
import java.util.Map;

/**
 * 가린 실명(홍*동) 창구. 택시비 정산에서 받는 사람이 은행 앱 입금자명과 송금자를 맞춰 보는 데 쓴다.
 * 실명 원문은 user 모듈 밖으로 내보내지 않는다
 */
public interface MaskedRealNames {
    /**
     * @param userIds 찾을 사용자의 내부 번호
     * @return userId → 가린 실명. 프로필이 없는 사용자(탈퇴 등)는 빠진다
     */
    Map<Long, String> find(List<Long> userIds);
}
