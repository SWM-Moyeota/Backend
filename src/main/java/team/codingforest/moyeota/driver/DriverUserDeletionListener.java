package team.codingforest.moyeota.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.user.api.UserDeletedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverUserDeletionListener {
    private final Drivers drivers;

    @ApplicationModuleListener
    public void on(UserDeletedEvent event) {
        drivers.deleteByUserId(event.userId());
        log.info("탈퇴한 사용자의 기사 정보 삭제 userId={}", event.userId());
    }
}
