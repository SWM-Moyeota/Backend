package team.codingforest.moyeota.place.favorite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceResponse;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoritePlaceController.class)
class FavoritePlaceControllerUnitTest extends ControllerUnitTestSupport {
    private static final String BASE_URL = "/api/v1/users/me/favorite-places";
    private static final String BATCH_DELETE_URL = BASE_URL + "/batch-delete";
    private static final Long PLACE_ID = 100L;
    private static final String SAVE_REQUEST = """
            {
                "placeName": "우리집",
                "roadName": "서울 강남구 테헤란로 152",
                "latitude": 37.4979,
                "longitude": 127.0276
            }
            """;
    private static final FavoritePlaceCommand SAVE_COMMAND =
            new FavoritePlaceCommand("우리집", "서울 강남구 테헤란로 152", 37.4979, 127.0276);

    @MockitoBean
    private FavoritePlaceService service;

    @DisplayName("POST /users/me/favorite-places - 성공하면 204 이고 요청 값과 토큰의 USER_ID 가 서비스로 간다")
    @Test
    void save() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(SAVE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).save(eq(SAVE_COMMAND), eq(USER_ID));
    }

    @DisplayName("POST /users/me/favorite-places - 토큰이 없으면 401")
    @Test
    void save_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(SAVE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /users/me/favorite-places - 본문이 없으면 400")
    @Test
    void save_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /users/me/favorite-places - 좌표가 숫자가 아니면 400")
    @Test
    void save_latitudeNotNumber() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "placeName": "우리집",
                    "latitude": "abc",
                    "longitude": 127.0276
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /users/me/favorite-places - 이름이 없으면 서비스의 INVALID_PLACE_NAME 이 400 으로 나간다")
    @Test
    void save_invalidPlaceName() throws Exception {

        /*
         * settings
         */
        final FavoritePlaceCommand command = new FavoritePlaceCommand(null, "서울 강남구 테헤란로 152", 37.4979, 127.0276);
        final String requestJson = """
                {
                    "roadName": "서울 강남구 테헤란로 152",
                    "latitude": 37.4979,
                    "longitude": 127.0276
                }
                """;

        doThrow(new BusinessException(PlaceErrorCode.INVALID_PLACE_NAME)).when(service).save(eq(command), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_NAME"))
                .andExpect(jsonPath("$.message").value(PlaceErrorCode.INVALID_PLACE_NAME.getMessage()));
    }

    @DisplayName("POST /users/me/favorite-places - 좌표가 없으면 서비스의 INVALID_COORDINATES 가 400 으로 나간다")
    @Test
    void save_missingCoordinates() throws Exception {

        /*
         * settings
         */
        final FavoritePlaceCommand command = new FavoritePlaceCommand("우리집", "서울 강남구 테헤란로 152", null, null);
        final String requestJson = """
                {
                    "placeName": "우리집",
                    "roadName": "서울 강남구 테헤란로 152"
                }
                """;

        doThrow(new BusinessException(PlaceErrorCode.INVALID_COORDINATES)).when(service).save(eq(command), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_COORDINATES"))
                .andExpect(jsonPath("$.message").value(PlaceErrorCode.INVALID_COORDINATES.getMessage()));
    }

    @DisplayName("POST /users/me/favorite-places - 같은 이름이면 409")
    @Test
    void save_duplicated() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.FAVORITE_PLACE_DUPLICATED)).when(service).save(eq(SAVE_COMMAND), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(SAVE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE_PLACE_DUPLICATED"));
    }

    @DisplayName("POST /users/me/favorite-places - 10개를 넘으면 409")
    @Test
    void save_limitExceeded() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.FAVORITE_PLACE_LIMIT_EXCEEDED)).when(service).save(eq(SAVE_COMMAND), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post(BASE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(SAVE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE_PLACE_LIMIT_EXCEEDED"));
    }

    @DisplayName("GET /users/me/favorite-places - 토큰의 USER_ID 로 조회한 목록을 필드 그대로 반환")
    @Test
    void getFavoriteList() throws Exception {

        /*
         * settings
         */
        final FavoritePlaceListResponse response = new FavoritePlaceListResponse(List.of(
                new FavoritePlaceResponse(PLACE_ID, "우리집", "서울 강남구 테헤란로 152", 37.4979, 127.0276, 1),
                new FavoritePlaceResponse(101L, "회사", null, 37.5, 127.1, 2)
        ));
        doReturn(response).when(service).getList(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(BASE_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].placeId").value(100))
                .andExpect(jsonPath("$.places[0].placeName").value("우리집"))
                .andExpect(jsonPath("$.places[0].roadName").value("서울 강남구 테헤란로 152"))
                .andExpect(jsonPath("$.places[0].latitude").value(37.4979))
                .andExpect(jsonPath("$.places[0].longitude").value(127.0276))
                .andExpect(jsonPath("$.places[0].placeSequence").value(1))
                .andExpect(jsonPath("$.places[1].placeId").value(101))
                .andExpect(jsonPath("$.places[1].placeSequence").value(2));
        verify(service).getList(eq(USER_ID));
    }

    @DisplayName("GET /users/me/favorite-places - 즐겨찾기가 없으면 빈 배열")
    @Test
    void getFavoriteList_empty() throws Exception {

        /*
         * settings
         */
        doReturn(new FavoritePlaceListResponse(List.of())).when(service).getList(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(BASE_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(0));
    }

    @DisplayName("GET /users/me/favorite-places - 토큰이 없으면 401")
    @Test
    void getFavoriteList_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(BASE_URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /users/me/favorite-places/{placeId} - 성공하면 204 이고 USER_ID 와 경로의 ID 가 서비스로 간다")
    @Test
    void deleteOne() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(BASE_URL + "/{placeId}", PLACE_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(service).delete(eq(USER_ID), eq(PLACE_ID));
    }

    @DisplayName("DELETE /users/me/favorite-places/{placeId} - 토큰이 없으면 401")
    @Test
    void deleteOne_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(BASE_URL + "/{placeId}", PLACE_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /users/me/favorite-places/{placeId} - 숫자가 아닌 ID 는 400")
    @Test
    void deleteOne_placeIdNotNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(BASE_URL + "/{placeId}", "abc").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("placeId: 형식이 올바르지 않습니다."));
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /users/me/favorite-places/{placeId} - 내 즐겨찾기에 없으면 404")
    @Test
    void deleteOne_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND)).when(service).delete(eq(USER_ID), eq(PLACE_ID));

        /*
         * test
         */
        mockMvc.perform(delete(BASE_URL + "/{placeId}", PLACE_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAVORITE_PLACE_NOT_FOUND"));
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - 성공하면 204 이고 USER_ID 와 ID 목록이 서비스로 간다")
    @Test
    void deleteByPlaceIds() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "placeIds": [123, 456]
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(service).deleteByPlaceIds(eq(USER_ID), eq(List.of(123L, 456L)));
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - 토큰이 없으면 401")
    @Test
    void deleteByPlaceIds_noToken() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "placeIds": [123]
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - 본문이 없으면 400")
    @Test
    void deleteByPlaceIds_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - ID 가 숫자가 아니면 400")
    @Test
    void deleteByPlaceIds_idNotNumber() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "placeIds": ["abc"]
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - placeIds 가 없으면 서비스의 INVALID_PLACE_IDS 가 400 으로 나간다")
    @Test
    void deleteByPlaceIds_missingPlaceIds() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                }
                """;

        doThrow(new BusinessException(PlaceErrorCode.INVALID_PLACE_IDS)).when(service).deleteByPlaceIds(eq(USER_ID), eq(null));

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_IDS"));
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - 빈 목록이면 서비스의 INVALID_PLACE_IDS 가 400 으로 나간다")
    @Test
    void deleteByPlaceIds_emptyPlaceIds() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "placeIds": []
                }
                """;

        doThrow(new BusinessException(PlaceErrorCode.INVALID_PLACE_IDS)).when(service).deleteByPlaceIds(eq(USER_ID), eq(List.of()));

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_IDS"));
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - 목록에 null 이 있으면 서비스의 INVALID_PLACE_ID 가 400 으로 나간다")
    @Test
    void deleteByPlaceIds_nullElement() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "placeIds": [123, null]
                }
                """;

        doThrow(new BusinessException(PlaceErrorCode.INVALID_PLACE_ID)).when(service).deleteByPlaceIds(eq(USER_ID), eq(Arrays.asList(123L, null)));

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLACE_ID"));
    }

    @DisplayName("POST /users/me/favorite-places/batch-delete - 내 즐겨찾기에 없는 ID 가 있으면 404")
    @Test
    void deleteByPlaceIds_notFound() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "placeIds": [123, 456]
                }
                """;

        doThrow(new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND)).when(service).deleteByPlaceIds(eq(USER_ID), eq(List.of(123L, 456L)));

        /*
         * test
         */
        mockMvc.perform(post(BATCH_DELETE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAVORITE_PLACE_NOT_FOUND"));
    }

    @DisplayName("DELETE /users/me/favorite-places/all - 성공하면 204 이고 단건 삭제로 빠지지 않는다")
    @Test
    void deleteAll() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(BASE_URL + "/all").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(service).deleteAll(eq(USER_ID));
        verify(service, never()).delete(any(), any());
    }

    @DisplayName("DELETE /users/me/favorite-places/all - 토큰이 없으면 401")
    @Test
    void deleteAll_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(BASE_URL + "/all"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /users/me/favorite-places/all - 즐겨찾기가 없으면 404")
    @Test
    void deleteAll_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND)).when(service).deleteAll(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(delete(BASE_URL + "/all").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAVORITE_PLACE_NOT_FOUND"));
    }
}
