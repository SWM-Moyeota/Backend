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
     * 탈퇴한 사용자가 받는 사람인 열린 정산의 계좌번호를 바로 지운다.
     */
    @ApplicationModuleListener
    public void on(UserDeletedEvent event) {
        int cleared = fareSplitService.forgetPayerAccounts(event.userId());
        log.info("탈퇴한 사용자가 받는 정산의 계좌번호 삭제 userId={}, splits={}", event.userId(), cleared);
    }
}
