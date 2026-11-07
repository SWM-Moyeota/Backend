package team.codingforest.moyeota.matching.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.matching.api.dto.RideFare;
import team.codingforest.moyeota.matching.faresplit.domain.FareShare;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *  탑승 기록(matching)에 넘기는 택시비와 내 몫 - 정산마다 기록을 보는 사람의 몫을 골라 준다
 */
@ExtendWith(MockitoExtension.class)
class RideFareServiceUnitTest {
    private static final Long PAYER_ID = 1L;
    private static final Long SENDER_ID = 2L;

    @Mock
    private FareSplits fareSplits;
    @InjectMocks
    private RideFareService service;

    @DisplayName("find - 내가 받는 사람이면 내 몫은 택시비에서 보내는 사람 몫을 뺀 금액이다")
    @Test
    void find_payer() {

        /*
         * settings
         */
        doReturn(List.of(new FareShare(10L, PAYER_ID, 18_700, 6_240, 2))).when(fareSplits).findShares(eq(PAYER_ID), eq(List.of(10L)));

        /*
         * test
         */
        List<RideFare> fares = service.find(PAYER_ID, List.of(10L));

        /*
         * verify
         */
        assertThat(fares).containsExactly(new RideFare(10L, 18_700, 18_700 - 6_240 * 2));
    }

    @DisplayName("find - 내가 보내는 사람이면 내 몫은 1인당 금액이다")
    @Test
    void find_sender() {

        /*
         * settings
         */
        doReturn(List.of(new FareShare(10L, PAYER_ID, 18_700, 6_240, 2))).when(fareSplits).findShares(eq(SENDER_ID), eq(List.of(10L)));

        /*
         * test
         */
        List<RideFare> fares = service.find(SENDER_ID, List.of(10L));

        /*
         * verify
         */
        assertThat(fares).containsExactly(new RideFare(10L, 18_700, 6_240));
    }

    @DisplayName("find - 넘긴 방들의 정산을 한 번에 읽고, 방마다 내 역할에 맞는 몫을 고른다")
    @Test
    void find_batch() {

        /*
         * settings
         */
        doReturn(List.of(new FareShare(30L, SENDER_ID, 10_001, 5_010, 1), new FareShare(20L, PAYER_ID, 12_000, 6_000, 1)))
                .when(fareSplits).findShares(eq(PAYER_ID), eq(List.of(30L, 20L, 10L)));

        /*
         * test
         */
        List<RideFare> fares = service.find(PAYER_ID, List.of(30L, 20L, 10L));

        /*
         * verify
         */
        assertThat(fares).containsExactly(new RideFare(30L, 10_001, 5_010), new RideFare(20L, 12_000, 6_000));
        verify(fareSplits, times(1)).findShares(any(), any());
    }

    @DisplayName("find - 정산이 있는 방이 없으면 빈 목록이다")
    @Test
    void find_none() {

        /*
         * settings
         */
        doReturn(List.of()).when(fareSplits).findShares(eq(PAYER_ID), eq(List.of(10L)));

        /*
         * test
         */
        List<RideFare> fares = service.find(PAYER_ID, List.of(10L));

        /*
         * verify
         */
        assertThat(fares).isEmpty();
    }
}
