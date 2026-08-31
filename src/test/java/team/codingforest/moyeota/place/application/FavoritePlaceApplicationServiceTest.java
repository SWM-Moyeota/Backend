package team.codingforest.moyeota.place.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceResponse;
import team.codingforest.moyeota.place.domain.FavoritePlace;
import team.codingforest.moyeota.place.infrastructure.FavoritePlaceJpa;

import static org.assertj.core.api.Assertions.*;

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
                .doesNotHaveDuplicates();   // 현재 구현: size=2 → 2+1=3 → 기존 3과 충돌 → 실패!
    }
}
