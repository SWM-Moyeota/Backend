package team.codingforest.moyeota.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.user.api.UserDeletedEvent;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DriverUserDeletionListenerTest {
    private DriverJpaTest drivers;
    private DriverUserDeletionListener listener;

    @BeforeEach
    void setUp() {
        drivers = new DriverJpaTest();
        listener = new DriverUserDeletionListener(drivers);
    }

    @Test
    void 탈퇴한_사용자의_기사_정보를_지운다() {
        Driver saved = drivers.save(Driver.register(1L, "서울-1234-5678", new BankAccount("국민은행", "123-456-789012")));
        drivers.save(Driver.register(2L, "서울-9999-0000", new BankAccount("국민은행", "999-999-999999")));

        listener.on(new UserDeletedEvent(1L, UUID.randomUUID()));

        assertThat(drivers.findById(saved.getId())).isEmpty();
        assertThat(drivers.findByUserId(2L)).isPresent();
    }

    @Test
    void 기사가_아닌_사용자가_탈퇴해도_예외_없이_지나간다() {
        assertThatCode(() -> listener.on(new UserDeletedEvent(1L, UUID.randomUUID())))
                .doesNotThrowAnyException();
    }
}
