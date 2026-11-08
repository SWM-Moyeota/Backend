package team.codingforest.moyeota.faresplit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.api.UserDeletedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class FareSplitUserDeletionListener {
    private final FareSplitService fareSplitService;

    /**
     * 탈퇴한 사용자가 받는 사람인 열린 정산의 계좌번호를 바로 지움
     * 정산은 열어 둬 시작 2일 뒤 만료로 닫힘. 이미 지운 정산은 그대로라 재발행돼도 됨
     *
     * @param event 탈퇴한 사용자 정보
     */
    @ApplicationModuleListener
    public void on(UserDeletedEvent event) {
        int cleared = fareSplitService.forgetPayerAccounts(event.userId());
        log.info("탈퇴한 사용자가 받는 정산의 계좌번호 삭제 userId={}, splits={}", event.userId(), cleared);
    }
}
