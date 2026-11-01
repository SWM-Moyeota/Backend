package team.codingforest.moyeota.place.favorite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlaces;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceResponse;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FavoritePlaceServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final Long PLACE_ID = 100L;
    private static final String PLACE_NAME = "우리집";
    private static final String ROAD_NAME = "서울 강남구 테헤란로 152";
    private static final Double LATITUDE = 37.4979;
    private static final Double LONGITUDE = 127.0276;

    @Mock
    private FavoritePlaces places;
    @InjectMocks
    private FavoritePlaceService service;
    @Captor
    private ArgumentCaptor<FavoritePlace> placeCaptor;

    private static FavoritePlaceCommand command(String placeName) {
        return new FavoritePlaceCommand(placeName, ROAD_NAME, LATITUDE, LONGITUDE);
    }

    private static FavoritePlace place(Long placeId, String placeName, int sequence) {
        return FavoritePlace.restore(USER_ID, placeId, placeName, ROAD_NAME, LATITUDE, LONGITUDE, sequence, Instant.EPOCH, Instant.EPOCH);
    }

    /** 이름이 장소1, 장소2 ... 인 즐겨찾기 count 개 */
    private static List<FavoritePlace> placesOf(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(i -> place(i, "장소" + i, (int) i))
                .toList();
    }

    @DisplayName("save - 첫 등록이면 순서 1로 요청 값 그대로 저장")
    @Test
    void save_first() {

        /*
         * settings
         */
        doReturn(List.of()).when(places).findByUserId(eq(USER_ID));
        doReturn(place(PLACE_ID, PLACE_NAME, 1)).when(places).save(any(FavoritePlace.class));

        /*
         * test
         */
        service.save(command(PLACE_NAME), USER_ID);

        /*
         * verify
         */
        verify(places).save(placeCaptor.capture());
        FavoritePlace saved = placeCaptor.getValue();
        assertThat(saved.getPlaceId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getPlaceName()).isEqualTo(PLACE_NAME);
        assertThat(saved.getRoadName()).isEqualTo(ROAD_NAME);
        assertThat(saved.getLatitude()).isEqualTo(LATITUDE);
        assertThat(saved.getLongitude()).isEqualTo(LONGITUDE);
        assertThat(saved.getPlaceSequence()).isEqualTo(1);
    }

    @DisplayName("save - 순서는 개수+1 이 아니라 가장 큰 순서+1")
    @Test
    void save_nextSequenceFromMax() {

        /*
         * settings
         */
        doReturn(List.of(place(1L, "회사", 5), place(2L, "학교", 2))).when(places).findByUserId(eq(USER_ID));
        doReturn(place(PLACE_ID, PLACE_NAME, 6)).when(places).save(any(FavoritePlace.class));

        /*
         * test
         */
        service.save(command(PLACE_NAME), USER_ID);

        /*
         * verify
         */
        verify(places).save(placeCaptor.capture());
        assertThat(placeCaptor.getValue().getPlaceSequence()).isEqualTo(6);
    }

    @DisplayName("save - 9개까지는 등록 가능(경계값)")
    @Test
    void save_nineExisting() {

        /*
         * settings
         */
        doReturn(placesOf(9)).when(places).findByUserId(eq(USER_ID));
        doReturn(place(PLACE_ID, PLACE_NAME, 10)).when(places).save(any(FavoritePlace.class));

        /*
         * test
         */
        service.save(command(PLACE_NAME), USER_ID);

        /*
         * verify
         */
        verify(places).save(placeCaptor.capture());
        assertThat(placeCaptor.getValue().getPlaceSequence()).isEqualTo(10);
    }

    @DisplayName("save - 이미 10개면 개수 초과로 실패(경계값)")
    @Test
    void save_limitExceeded() {

        /*
         * settings
         */
        doReturn(placesOf(10)).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.save(command(PLACE_NAME), USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_LIMIT_EXCEEDED);
        verify(places, never()).save(any());
    }

    @DisplayName("save - 10개를 넘겨 있어도 개수 초과로 실패")
    @Test
    void save_overLimit() {

        /*
         * settings
         */
        doReturn(placesOf(11)).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.save(command(PLACE_NAME), USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_LIMIT_EXCEEDED);
        verify(places, never()).save(any());
    }

    @DisplayName("save - 같은 이름이 있으면 중복으로 실패")
    @Test
    void save_duplicated() {

        /*
         * settings
         */
        doReturn(List.of(place(1L, "회사", 1), place(2L, PLACE_NAME, 2))).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.save(command(PLACE_NAME), USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
        verify(places, never()).save(any());
    }

    @DisplayName("save - 중복이면서 10개면 중복 에러가 먼저")
    @Test
    void save_duplicatedAtLimit() {

        /*
         * settings
         */
        doReturn(placesOf(10)).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.save(command("장소1"), USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
        verify(places, never()).save(any());
    }

    @DisplayName("save - 장소 이름이 null 이거나 비어 있으면 실패")
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void save_invalidPlaceName(String placeName) {

        /*
         * settings
         */
        doReturn(List.of(place(1L, "회사", 1))).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.save(command(placeName), USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_PLACE_NAME);
        verify(places, never()).save(any());
    }

    @DisplayName("save - 좌표가 null 이면 저장하지 않고 실패")
    @ParameterizedTest
    @CsvSource(value = {"null, 127.0276", "37.4979, null", "null, null"}, nullValues = "null")
    void save_nullCoordinates(Double latitude, Double longitude) {

        /*
         * settings
         */
        doReturn(List.of()).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.save(new FavoritePlaceCommand(PLACE_NAME, ROAD_NAME, latitude, longitude), USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_COORDINATES);
        verify(places, never()).save(any());
    }

    @DisplayName("save - 서비스 지역 밖이거나 NaN 좌표면 저장하지 않고 실패")
    @ParameterizedTest
    @CsvSource({
            "32.99999, 127.0",
            "39.00001, 127.0",
            "37.0, 123.99999",
            "37.0, 132.00001",
            "35.6762, 139.6503",
            "NaN, 127.0",
            "37.0, NaN"
    })
    void save_outOfServiceArea(Double latitude, Double longitude) {

        /*
         * settings
         */
        doReturn(List.of()).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.save(new FavoritePlaceCommand(PLACE_NAME, ROAD_NAME, latitude, longitude), USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_COORDINATES);
        verify(places, never()).save(any());
    }

    @DisplayName("save - 서비스 지역 경계의 좌표는 저장(경계값)")
    @ParameterizedTest
    @CsvSource({"33.0, 124.0", "33.0, 132.0", "39.0, 124.0", "39.0, 132.0"})
    void save_serviceAreaBoundary(Double latitude, Double longitude) {

        /*
         * settings
         */
        doReturn(List.of()).when(places).findByUserId(eq(USER_ID));
        doReturn(place(PLACE_ID, PLACE_NAME, 1)).when(places).save(any(FavoritePlace.class));

        /*
         * test
         */
        service.save(new FavoritePlaceCommand(PLACE_NAME, ROAD_NAME, latitude, longitude), USER_ID);

        /*
         * verify
         */
        verify(places).save(placeCaptor.capture());
        assertThat(placeCaptor.getValue().getLatitude()).isEqualTo(latitude);
        assertThat(placeCaptor.getValue().getLongitude()).isEqualTo(longitude);
    }

    @DisplayName("delete - 내 즐겨찾기에 있으면 삭제")
    @Test
    void delete() {

        /*
         * settings
         */
        doReturn(true).when(places).existsByUserIdAndPlace(eq(USER_ID), eq(PLACE_ID));

        /*
         * test
         */
        service.delete(USER_ID, PLACE_ID);

        /*
         * verify
         */
        verify(places).deleteByUserIdAndPlaceName(eq(USER_ID), eq(PLACE_ID));
    }

    @DisplayName("delete - placeId 가 null 이면 저장소를 부르지 않고 실패")
    @Test
    void delete_nullPlaceId() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.delete(USER_ID, null));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_PLACE_ID);
        verifyNoInteractions(places);
    }

    @DisplayName("delete - 내 즐겨찾기에 없으면 삭제하지 않고 실패")
    @Test
    void delete_notFound() {

        /*
         * settings
         */
        doReturn(false).when(places).existsByUserIdAndPlace(eq(USER_ID), eq(PLACE_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.delete(USER_ID, PLACE_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        verify(places, never()).deleteByUserIdAndPlaceName(any(), any());
    }

    @DisplayName("deleteByPlaceIds - 전부 내 즐겨찾기면 삭제")
    @Test
    void deleteByPlaceIds() {

        /*
         * settings
         */
        doReturn(placesOf(3)).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        service.deleteByPlaceIds(USER_ID, List.of(1L, 3L));

        /*
         * verify
         */
        verify(places).deleteByUserIdAndPlaceIdIn(eq(USER_ID), eq(List.of(1L, 3L)));
    }

    @DisplayName("deleteByPlaceIds - 한 건만 요청해도 삭제(경계값)")
    @Test
    void deleteByPlaceIds_single() {

        /*
         * settings
         */
        doReturn(placesOf(1)).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        service.deleteByPlaceIds(USER_ID, List.of(1L));

        /*
         * verify
         */
        verify(places).deleteByUserIdAndPlaceIdIn(eq(USER_ID), eq(List.of(1L)));
    }

    @DisplayName("deleteByPlaceIds - 같은 ID 를 여러 번 보내면 한 번씩만 삭제 요청")
    @Test
    void deleteByPlaceIds_distinct() {

        /*
         * settings
         */
        doReturn(placesOf(2)).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        service.deleteByPlaceIds(USER_ID, List.of(2L, 1L, 2L, 1L));

        /*
         * verify
         */
        verify(places).deleteByUserIdAndPlaceIdIn(eq(USER_ID), eq(List.of(2L, 1L)));
    }

    @DisplayName("deleteByPlaceIds - 목록이 null 이면 저장소를 부르지 않고 실패")
    @Test
    void deleteByPlaceIds_null() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.deleteByPlaceIds(USER_ID, null));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_PLACE_IDS);
        verifyNoInteractions(places);
    }

    @DisplayName("deleteByPlaceIds - 목록이 비어 있으면 저장소를 부르지 않고 실패")
    @Test
    void deleteByPlaceIds_empty() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.deleteByPlaceIds(USER_ID, List.of()));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_PLACE_IDS);
        verifyNoInteractions(places);
    }

    @DisplayName("deleteByPlaceIds - 목록에 null 이 섞이면 저장소를 부르지 않고 실패")
    @Test
    void deleteByPlaceIds_nullElement() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.deleteByPlaceIds(USER_ID, Arrays.asList(1L, null)));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.INVALID_PLACE_ID);
        verifyNoInteractions(places);
    }

    @DisplayName("deleteByPlaceIds - 하나라도 내 즐겨찾기에 없으면 아무것도 삭제하지 않고 실패")
    @Test
    void deleteByPlaceIds_partiallyMissing() {

        /*
         * settings
         */
        doReturn(placesOf(2)).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.deleteByPlaceIds(USER_ID, List.of(1L, 999L)));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        verify(places, never()).deleteByUserIdAndPlaceIdIn(anyLong(), anyList());
    }

    @DisplayName("deleteByPlaceIds - 즐겨찾기가 하나도 없으면 실패")
    @Test
    void deleteByPlaceIds_noFavorites() {

        /*
         * settings
         */
        doReturn(List.of()).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.deleteByPlaceIds(USER_ID, List.of(1L)));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        verify(places, never()).deleteByUserIdAndPlaceIdIn(anyLong(), anyList());
    }

    @DisplayName("deleteAll - 즐겨찾기가 있으면 전체 삭제")
    @Test
    void deleteAll() {

        /*
         * settings
         */
        doReturn(1).when(places).countByUserId(eq(USER_ID));

        /*
         * test
         */
        service.deleteAll(USER_ID);

        /*
         * verify
         */
        verify(places).deleteAllByUserId(eq(USER_ID));
    }

    @DisplayName("deleteAll - 즐겨찾기가 없으면 삭제하지 않고 실패")
    @Test
    void deleteAll_empty() {

        /*
         * settings
         */
        doReturn(0).when(places).countByUserId(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.deleteAll(USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        verify(places, never()).deleteAllByUserId(any());
    }

    @DisplayName("getList - 저장소 순서 그대로 모든 필드를 옮겨 반환")
    @Test
    void getList() {

        /*
         * settings
         */
        doReturn(List.of(place(2L, "회사", 2), place(1L, PLACE_NAME, 1))).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        FavoritePlaceListResponse response = service.getList(USER_ID);

        /*
         * verify
         */
        assertThat(response.places()).containsExactly(
                new FavoritePlaceResponse(2L, "회사", ROAD_NAME, LATITUDE, LONGITUDE, 2),
                new FavoritePlaceResponse(1L, PLACE_NAME, ROAD_NAME, LATITUDE, LONGITUDE, 1)
        );
    }

    @DisplayName("getList - 즐겨찾기가 없으면 빈 목록")
    @Test
    void getList_empty() {

        /*
         * settings
         */
        doReturn(List.of()).when(places).findByUserId(eq(USER_ID));

        /*
         * test
         */
        FavoritePlaceListResponse response = service.getList(USER_ID);

        /*
         * verify
         */
        assertThat(response.places()).isEmpty();
    }
}
