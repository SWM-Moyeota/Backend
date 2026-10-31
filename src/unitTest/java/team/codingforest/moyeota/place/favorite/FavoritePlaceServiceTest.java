package team.codingforest.moyeota.place.favorite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceResponse;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FavoritePlaceServiceTest {

    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;
    private static final Long 없는ID = 9_999L;

    private FakeFavoritePlaces fake;
    private FavoritePlaceService service;

    @BeforeEach
    void setUp() {
        fake = new FakeFavoritePlaces();
        service = new FavoritePlaceService(fake);
    }

    private FavoritePlaceCommand 장소(String name) {
        return new FavoritePlaceCommand(name, "서울 강남구 테헤란로 123", 37.5013, 127.0396);
    }

    /** 목록에서 이름으로 찾아 DB 가 채번한 placeId 를 돌려준다 */
    private Long id(Long userId, String name) {
        return service.getList(userId).places().stream()
                .filter(p -> p.placeName().equals(name))
                .map(FavoritePlaceResponse::placeId)
                .findFirst().orElseThrow();
    }

    @Test
    void 등록하면_placeId_가_부여되고_목록에서_조회된다() {
        service.save(장소("집"), 유저);

        FavoritePlaceListResponse result = service.getList(유저);

        assertThat(result.places()).hasSize(1);
        assertThat(result.places().get(0).placeName()).isEqualTo("집");
        assertThat(result.places().get(0).placeId()).isNotNull();
    }

    @Test
    void 등록_순서대로_sequence가_부여된다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);
        service.save(장소("헬스장"), 유저);

        FavoritePlaceListResponse result = service.getList(유저);

        assertThat(result.places())
                .extracting(FavoritePlaceResponse::placeSequence)
                .containsExactly(1, 2, 3);
    }

    @Test
    void 같은_이름은_중복_등록할_수_없다() {
        service.save(장소("집"), 유저);

        assertThatThrownBy(() -> service.save(장소("집"), 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
        assertThat(service.getList(유저).places()).hasSize(1);
    }

    @Test
    void 다른_유저와는_이름이_겹쳐도_된다() {
        service.save(장소("집"), 유저);

        service.save(장소("집"), 다른유저);

        assertThat(service.getList(유저).places()).hasSize(1);
        assertThat(service.getList(다른유저).places()).hasSize(1);
        assertThat(id(유저, "집")).isNotEqualTo(id(다른유저, "집"));
    }

    @Test
    void 열_개를_초과하면_등록할_수_없다() {
        for(int i = 1; i <= 10; i++) {
            service.save(장소("장소" + i), 유저);
        }

        assertThatThrownBy(() -> service.save(장소("열한번째"), 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_LIMIT_EXCEEDED);
        assertThat(service.getList(유저).places()).hasSize(10);   // 초과분이 저장되면 안 된다
    }

    @Test
    void 이름_없는_장소는_등록할_수_없다() {
        // 지도에서 이름 파싱에 실패한 채로 등록 버튼이 눌린 경우 - 목록에 무명 항목이 남으면 안 된다
        for (String name : Arrays.asList(null, "", "  ")) {
            assertThatThrownBy(() -> service.save(장소(name), 유저))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PlaceErrorCode.INVALID_PLACE_NAME);
        }
        assertThat(service.getList(유저).places()).isEmpty();
    }

    @Test
    void 좌표가_없으면_등록할_수_없다() {
        FavoritePlaceCommand 좌표없음 = new FavoritePlaceCommand("집", "서울 강남구 테헤란로 123", null, null);

        assertThatThrownBy(() -> service.save(좌표없음, 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATES);
        assertThat(service.getList(유저).places()).isEmpty();
    }

    @Test
    void 서비스_지역_밖_좌표는_등록할_수_없다() {
        // 도쿄 - 앱은 이런 값을 보내지 않지만 API 를 직접 호출하면 들어올 수 있다
        FavoritePlaceCommand 도쿄 = new FavoritePlaceCommand("호텔", "", 35.6762, 139.6503);

        assertThatThrownBy(() -> service.save(도쿄, 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATES);
        assertThat(service.getList(유저).places()).isEmpty();
    }

    @Test
    void 중간이_삭제된_상태에서_등록해도_sequence가_겹치지_않는다() {
        // 1, 3만 있는 상태 = 2번이 삭제된 뒤 상황을 가짜에 직접 심음
        fake.save(FavoritePlace.from(유저, "집", "주소", 37.5, 127.0, 1));
        fake.save(FavoritePlace.from(유저, "헬스장", "주소", 37.5, 127.0, 3));

        service.save(장소("새장소"), 유저);

        assertThat(service.getList(유저).places())
                .extracting(FavoritePlaceResponse::placeSequence)
                .doesNotHaveDuplicates();
    }

    @Test
    void 삭제하면_목록에서_사라진다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);

        service.delete(유저, id(유저, "집"));

        assertThat(service.getList(유저).places())
                .extracting(FavoritePlaceResponse::placeName)
                .containsExactly("회사");
    }

    @Test
    void 없는_장소를_삭제하면_실패한다() {
        assertThatThrownBy(() -> service.delete(유저, 없는ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
    }

    @Test
    void 다른_유저의_즐겨찾기는_지울_수_없다() {
        // 내 즐겨찾기가 아니면 404, 상대 데이터는 그대로 남아야 한다
        service.save(장소("집"), 다른유저);

        assertThatThrownBy(() -> service.delete(유저, id(다른유저, "집")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        assertThat(service.getList(다른유저).places()).hasSize(1);
    }

    @Test
    void ID_없이는_삭제할_수_없다() {
        // ID 없이 호출되면 404 가 아니라 400 으로 요청 자체가 잘못됐음을 알린다
        assertThatThrownBy(() -> service.delete(유저, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_PLACE_ID);
    }

    @Test
    void 전체_삭제하면_내_즐겨찾기만_모두_사라진다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);
        service.save(장소("집"), 다른유저);

        service.deleteAll(유저);

        assertThat(service.getList(유저).places()).isEmpty();
        assertThat(service.getList(다른유저).places()).hasSize(1);
    }

    @Test
    void 내_즐겨찾기가_없으면_전체_삭제는_실패한다() {
        service.save(장소("집"), 다른유저);
        assertThatThrownBy(() -> service.deleteAll(유저))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        assertThat(service.getList(다른유저).places()).hasSize(1);

        assertThat(service.getList(유저).places()).isEmpty();
    }

    @Test
    void 삭제한_이름으로_다시_등록할_수_있다() {
        service.save(장소("집"), 유저);
        service.delete(유저, id(유저, "집"));

        service.save(장소("집"), 유저);

        assertThat(service.getList(유저).places()).hasSize(1);
    }

    @Test
    void 다건_삭제는_중복_ID를_제거하고_선택한_내_항목만_지운다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);
        service.save(장소("헬스장"), 유저);
        service.save(장소("집"), 다른유저);
        Long 집 = id(유저, "집");
        List<Long> 대상 = List.of(집, id(유저, "회사"), 집);

        service.deleteByPlaceIds(유저, 대상);

        assertThat(service.getList(유저).places()).extracting(FavoritePlaceResponse::placeName)
                .containsExactly("헬스장");
        assertThat(service.getList(다른유저).places()).hasSize(1);
    }

    @Test
    void 잘못된_다건_삭제_요청은_삭제_전에_거절한다() {
        service.save(장소("집"), 유저);
        List<List<Long>> invalidLists = Arrays.asList(null, List.of());
        for (var 목록 : invalidLists) {
            assertThatThrownBy(() -> service.deleteByPlaceIds(유저, 목록))
                    .isInstanceOf(BusinessException.class).extracting("errorCode")
                    .isEqualTo(PlaceErrorCode.INVALID_PLACE_IDS);
        }
        assertThatThrownBy(() -> service.deleteByPlaceIds(유저, Arrays.asList(id(유저, "집"), null)))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_PLACE_ID);
        assertThat(service.getList(유저).places()).hasSize(1);
    }

    @Test
    void 다건_삭제는_하나라도_내_즐겨찾기가_아니면_아무것도_삭제하지_않는다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 다른유저);
        for (Long missingId : List.of(없는ID, id(다른유저, "회사"))) {
            assertThatThrownBy(() -> service.deleteByPlaceIds(유저, List.of(id(유저, "집"), missingId)))
                    .isInstanceOf(BusinessException.class).extracting("errorCode")
                    .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
            assertThat(service.getList(유저).places()).hasSize(1);
            assertThat(service.getList(다른유저).places()).hasSize(1);
        }
    }

    @Test
    void 다건_삭제할_즐겨찾기가_전부_없으면_실패한다() {
        assertThatThrownBy(() -> service.deleteByPlaceIds(유저, List.of(없는ID)))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
    }
}
