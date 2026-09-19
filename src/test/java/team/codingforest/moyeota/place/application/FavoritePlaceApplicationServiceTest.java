package team.codingforest.moyeota.place.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceUpdateCommand;
import team.codingforest.moyeota.place.domain.FavoritePlace;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FavoritePlaceApplicationServiceTest {

    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;

    private FavoritePlaceJpaTest fake;
    private FavoritePlaceApplicationService service;

    @BeforeEach
    void setUp() {
        fake = new FavoritePlaceJpaTest();
        service = new FavoritePlaceApplicationService(fake);
    }

    private FavoritePlaceCommand 장소(String name) {
        return new FavoritePlaceCommand(name, "서울 강남구 테헤란로 123", 37.5013, 127.0396);
    }

    @Test
    void 등록하면_목록에서_조회된다() {
        service.save(장소("집"), 유저);

        FavoritePlaceListResponse result = service.getList(유저);

        assertThat(result.places()).hasSize(1);
        assertThat(result.places().get(0).placeName()).isEqualTo("집");
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
    }

    @Test
    void 다른_유저와는_이름이_겹쳐도_된다() {
        service.save(장소("집"), 유저);

        service.save(장소("집"), 다른유저);

        assertThat(service.getList(다른유저).places()).hasSize(1);
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
        assertThatThrownBy(() -> service.save(장소("  "), 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_PLACE_NAME);
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
                .doesNotHaveDuplicates();   // size 기준(2+1=3)이면 기존 3과 충돌 - max 기준이어야 한다
    }

    @Test
    void 좌표_없이는_등록할_수_없다() {
        assertThatThrownBy(() -> service.save(new FavoritePlaceCommand("집", "주소", null, 127.0), 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_COORDINATES);
    }

    @Test
    void 목록은_sequence_오름차순이다() {
        fake.save(FavoritePlace.from(유저, "헬스장", "주소", 37.5, 127.0, 3));
        fake.save(FavoritePlace.from(유저, "집", "주소", 37.5, 127.0, 1));
        fake.save(FavoritePlace.from(유저, "회사", "주소", 37.5, 127.0, 2));

        assertThat(service.getList(유저).places())
                .extracting(FavoritePlaceResponse::placeName)
                .containsExactly("집", "회사", "헬스장");
    }

    @Test
    void 수정하면_주소와_좌표가_바뀌고_이름과_순서는_그대로다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);

        service.update(new FavoritePlaceUpdateCommand(null, "서울 마포구 월드컵로 1", 37.55, 126.9), 유저, "집");

        FavoritePlaceResponse updated = service.getList(유저).places().get(0);
        assertThat(updated.placeName()).isEqualTo("집");
        assertThat(updated.roadName()).isEqualTo("서울 마포구 월드컵로 1");
        assertThat(updated.latitude()).isEqualTo(37.55);
        assertThat(updated.longitude()).isEqualTo(126.9);
        assertThat(updated.placeSequence()).isEqualTo(1);
    }

    @Test
    void 이름을_바꾸면_옛_이름은_사라지고_순서는_유지된다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);

        service.update(new FavoritePlaceUpdateCommand("본가", "주소", 37.5, 127.0), 유저, "집");

        List<FavoritePlaceResponse> places = service.getList(유저).places();
        assertThat(places).extracting(FavoritePlaceResponse::placeName).containsExactly("본가", "회사");
        assertThat(places.get(0).placeSequence()).isEqualTo(1);
        assertThat(fake.existsByUserIdAndPlace(유저, "집")).isFalse();
    }

    @Test
    void 같은_이름으로_보내면_이름_변경으로_취급하지_않는다() {
        service.save(장소("집"), 유저);

        service.update(new FavoritePlaceUpdateCommand("집", "새 주소", 37.5, 127.0), 유저, "집");

        assertThat(service.getList(유저).places()).hasSize(1);
        assertThat(service.getList(유저).places().get(0).roadName()).isEqualTo("새 주소");
    }

    @Test
    void 이미_있는_이름으로는_바꿀_수_없다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);

        assertThatThrownBy(() -> service.update(new FavoritePlaceUpdateCommand("회사", "주소", 37.5, 127.0), 유저, "집"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
        assertThat(fake.existsByUserIdAndPlace(유저, "집")).isTrue();   // 실패했으면 원본이 남아야 한다
    }

    @Test
    void 없는_장소는_수정할_수_없다() {
        assertThatThrownBy(() -> service.update(new FavoritePlaceUpdateCommand(null, "주소", 37.5, 127.0), 유저, "없는곳"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
    }

    @Test
    void 삭제하면_목록에서_사라진다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);

        service.delete(유저, "집");

        assertThat(service.getList(유저).places())
                .extracting(FavoritePlaceResponse::placeName)
                .containsExactly("회사");
    }

    @Test
    void 없는_장소는_삭제할_수_없다() {
        assertThatThrownBy(() -> service.delete(유저, "없는곳"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
    }

    @Test
    void 다른_유저의_장소는_삭제할_수_없다() {
        service.save(장소("집"), 다른유저);

        assertThatThrownBy(() -> service.delete(유저, "집"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        assertThat(service.getList(다른유저).places()).hasSize(1);
    }

    @Test
    void 순서를_바꾸면_보낸_순서대로_1부터_다시_매겨진다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);
        service.save(장소("헬스장"), 유저);

        service.reorder(List.of("헬스장", "집", "회사"), 유저);

        List<FavoritePlaceResponse> places = service.getList(유저).places();
        assertThat(places).extracting(FavoritePlaceResponse::placeName).containsExactly("헬스장", "집", "회사");
        assertThat(places).extracting(FavoritePlaceResponse::placeSequence).containsExactly(1, 2, 3);
    }

    @Test
    void 순서_목록에_장소가_빠지면_실패한다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);

        assertThatThrownBy(() -> service.reorder(List.of("회사"), 유저))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PlaceErrorCode.INVALID_PLACE_ORDER);
    }

    @Test
    void 순서_목록에_모르는_장소나_중복이_있으면_실패한다() {
        service.save(장소("집"), 유저);
        service.save(장소("회사"), 유저);

        assertThatThrownBy(() -> service.reorder(List.of("집", "없는곳"), 유저))
                .extracting("errorCode").isEqualTo(PlaceErrorCode.INVALID_PLACE_ORDER);
        assertThatThrownBy(() -> service.reorder(List.of("집", "집"), 유저))
                .extracting("errorCode").isEqualTo(PlaceErrorCode.INVALID_PLACE_ORDER);

        // 실패한 요청은 순서를 건드리지 않는다
        assertThat(service.getList(유저).places())
                .extracting(FavoritePlaceResponse::placeName).containsExactly("집", "회사");
    }
}
