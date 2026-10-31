package team.codingforest.moyeota.matching.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.route.domain.RouteCache;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.route.domain.RouteFinder;
import team.codingforest.moyeota.matching.route.domain.RouteKey;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RouteServiceUnitTest {
    private static final double DEPARTURE_LAT = 37.4979;
    private static final double DEPARTURE_LNG = 127.0276;
    private static final double DESTINATION_LAT = 37.3948;
    private static final double DESTINATION_LNG = 127.1112;
    private static final RouteKey KEY = new RouteKey(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG);
    private static final RouteEstimate ESTIMATE = new RouteEstimate(12000, 25, "encoded-path");

    @Mock
    private RouteFinder routeFinder;
    @Mock
    private RouteCache routeCache;

    @InjectMocks
    private RouteService routeService;

    @DisplayName("estimate - 캐시에 있으면 캐시 값을 돌려주고 네이버를 부르지 않는다")
    @Test
    void estimate_cacheHit() {

        /*
         * settings
         */
        doReturn(Optional.of(ESTIMATE)).when(routeCache).find(eq(KEY));

        /*
         * test
         */
        RouteEstimate result = routeService.estimate(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG);

        /*
         * verify
         */
        assertThat(result).isEqualTo(ESTIMATE);
        verifyNoInteractions(routeFinder);
        verify(routeCache, never()).save(any(), any());
    }

    @DisplayName("estimate - 캐시에 없으면 경로를 산출해 캐시에 저장하고 돌려준다")
    @Test
    void estimate_cacheMiss() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(routeCache).find(eq(KEY));
        doReturn(ESTIMATE).when(routeFinder).find(eq(KEY));

        /*
         * test
         */
        RouteEstimate result = routeService.estimate(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG);

        /*
         * verify
         */
        assertThat(result).isEqualTo(ESTIMATE);
        InOrder inOrder = inOrder(routeFinder, routeCache);
        inOrder.verify(routeCache).find(eq(KEY));
        inOrder.verify(routeFinder).find(eq(KEY));
        inOrder.verify(routeCache).save(eq(KEY), eq(ESTIMATE));
    }

    @DisplayName("estimate - 좌표는 소수 다섯째 자리로 반올림한 키로 조회한다")
    @Test
    void estimate_roundedKey() {

        /*
         * settings
         */
        RouteKey rounded = new RouteKey(37.49792, 127.02757, 37.39481, 127.11119);

        doReturn(Optional.of(ESTIMATE)).when(routeCache).find(eq(rounded));

        /*
         * test
         */
        RouteEstimate result = routeService.estimate(37.497921, 127.027568, 37.394812, 127.111191);

        /*
         * verify
         */
        assertThat(result).isEqualTo(ESTIMATE);
    }

    @DisplayName("estimate - 캐시 미스일 때 경로 산출에도 반올림한 키를 넘긴다")
    @Test
    void estimate_cacheMissRoundedKey() {

        /*
         * settings
         */
        RouteKey rounded = new RouteKey(37.49792, 127.02757, 37.39481, 127.11119);

        doReturn(Optional.empty()).when(routeCache).find(eq(rounded));
        doReturn(ESTIMATE).when(routeFinder).find(eq(rounded));

        /*
         * test
         */
        routeService.estimate(37.497921, 127.027568, 37.394812, 127.111191);

        /*
         * verify
         */
        verify(routeCache).save(eq(rounded), eq(ESTIMATE));
    }

    @DisplayName("estimate - 경로 산출이 ROUTE_SEARCH_FAILED 면 그대로 나가고 캐시에 저장하지 않는다")
    @Test
    void estimate_routeSearchFailed() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(routeCache).find(eq(KEY));
        doThrow(new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED)).when(routeFinder).find(eq(KEY));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> routeService.estimate(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ROUTE_SEARCH_FAILED);
        verify(routeCache, never()).save(any(), any());
    }

    @DisplayName("estimate - 경로 산출이 ROUTE_NOT_FOUND 면 그대로 나가고 캐시에 저장하지 않는다")
    @Test
    void estimate_routeNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(routeCache).find(eq(KEY));
        doThrow(new BusinessException(MatchingErrorCode.ROUTE_NOT_FOUND)).when(routeFinder).find(eq(KEY));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> routeService.estimate(DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ROUTE_NOT_FOUND);
        verify(routeCache, never()).save(any(), any());
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
