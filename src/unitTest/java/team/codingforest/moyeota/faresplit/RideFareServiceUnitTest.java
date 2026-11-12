package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.faresplit.domain.FareShare;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.matching.api.dto.RideFare;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *  탑승 기록(matching)에 넘기는 택시비와 내 몫 - 정산마다 기록을 보는 사람의 줄에 적힌 부담 금액을 그대로 준다
 */
@ExtendWith(MockitoExtension.class)
class RideFareServiceUnitTest {
    private static final Long PAYER_ID = 1L;
    private static final Long SENDER_ID = 2L;

    @Mock
    private FareSplits fareSplits;
    @InjectMocks
    private RideFareService service;

    @DisplayName("find - 내가 받는 사람(방장)이면 내 몫은 방장 줄에 적힌 부담 금액이다")
    @Test
    void find_payer() {

        /*
         * settings
         */
        // 받는 사람도 자기 줄이 있어 myPayment 에 방장 부담금이 온다
        doReturn(List.of(new FareShare(10L, 18_700, 6_220))).when(fareSplits).findShares(eq(PAYER_ID), eq(List.of(10L)));

        /*
         * test
         */
        List<RideFare> fares = service.find(PAYER_ID, List.of(10L));

        /*
         * verify
         */
        assertThat(fares).containsExactly(new RideFare(10L, 18_700, 6_220));
    }

    @DisplayName("find - 내가 보내는 사람이면 내 몫은 내 줄에 적힌 부담 금액이다")
    @Test
    void find_sender() {

        /*
         * settings
         */
        doReturn(List.of(new FareShare(10L, 18_700, 6_240))).when(fareSplits).findShares(eq(SENDER_ID), eq(List.of(10L)));

        /*
         * test
         */
        List<RideFare> fares = service.find(SENDER_ID, List.of(10L));

        /*
         * verify
         */
        assertThat(fares).containsExactly(new RideFare(10L, 18_700, 6_240));
    }

    @DisplayName("find - 넘긴 방들의 정산을 한 번에 읽고, 받아 온 순서 그대로 방마다 택시비와 내 부담 금액을 준다")
    @Test
    void find_batch() {

        /*
         * settings
         */
        doReturn(List.of(new FareShare(30L, 10_001, 5_010), new FareShare(20L, 12_000, 6_000)))
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
