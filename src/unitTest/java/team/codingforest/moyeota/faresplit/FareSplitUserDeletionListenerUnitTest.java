package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.api.UserDeletedEvent;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FareSplitUserDeletionListenerUnitTest {
    private static final Long USER_ID = 7L;

    @Mock
    private FareSplitService fareSplitService;

    @InjectMocks
    private FareSplitUserDeletionListener listener;

    @DisplayName("on - 탈퇴한 사용자가 받는 사람인 열린 정산의 계좌번호를 지운다")
    @Test
    void on() {

        /*
         * settings
         */
        doReturn(2).when(fareSplitService).forgetPayerAccounts(eq(USER_ID));

        /*
         * test
         */
        listener.on(new UserDeletedEvent(USER_ID, UUID.randomUUID()));

        /*
         * verify
         */
        verify(fareSplitService).forgetPayerAccounts(eq(USER_ID));
    }
}
