package team.codingforest.moyeota.place.favorite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;
import team.codingforest.moyeota.place.favorite.infrastructure.FavoritePlaceJpa;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(FavoritePlaceJpa.class)
class FavoritePlaceJpaTest {

    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;
    private static final Long 없는ID = 9_999L;

    private final FavoritePlaceJpa places;

    @Autowired
    FavoritePlaceJpaTest(FavoritePlaceJpa places) {
        this.places = places;
    }

    private FavoritePlace 장소(Long userId, String name, int sequence) {
        return FavoritePlace.from(userId, name, "서울 강남구 테헤란로 123", 37.5013, 127.0396, sequence);
    }

    /** 저장하고 DB 가 채번한 placeId 를 돌려준다 */
    private Long 저장(Long userId, String name, int sequence) {
        return places.save(장소(userId, name, sequence)).getPlaceId();
    }

    @Test
    void 저장하면_placeId_가_채번되고_다시_읽을_수_있다() {
        Long 집 = 저장(유저, "집", 1);
        Long 회사 = 저장(유저, "회사/본점", 2);

        assertThat(집).isNotNull();
        assertThat(회사).isNotNull().isNotEqualTo(집);
        assertThat(places.findByUserId(유저))
                .extracting(FavoritePlace::getPlaceName)
                .containsExactlyInAnyOrder("집", "회사/본점");
        assertThat(places.findByUserId(유저))
                .extracting(FavoritePlace::getPlaceId)
                .containsExactlyInAnyOrder(집, 회사);
        assertThat(places.existsByUserIdAndPlace(유저, 집)).isTrue();
    }

    @Test
    void 다른_유저가_같은_이름을_저장해도_각자_다른_행이_된다() {
        Long 내집 = 저장(유저, "집", 1);
        Long 남의집 = 저장(다른유저, "집", 1);

        assertThat(남의집).isNotEqualTo(내집);
        assertThat(places.findByUserId(유저)).hasSize(1);
        assertThat(places.findByUserId(다른유저)).hasSize(1);
    }

    @Test
    void 한_건_삭제는_해당_즐겨찾기를_지운다() {
        Long 집 = 저장(유저, "집", 1);

        places.deleteByUserIdAndPlaceName(유저, 집);
        places.deleteByUserIdAndPlaceName(유저, 집);
        assertThat(places.existsByUserIdAndPlace(유저, 집)).isFalse();
    }

    @Test
    void 다른_유저의_즐겨찾기는_지워지지_않는다() {
        Long 남의집 = 저장(다른유저, "집", 1);

        places.deleteByUserIdAndPlaceName(유저, 남의집);
        assertThat(places.existsByUserIdAndPlace(다른유저, 남의집)).isTrue();
    }

    @Test
    void 전체_삭제는_내_것만_지운다() {
        저장(유저, "집", 1);
        저장(유저, "회사", 2);
        저장(다른유저, "집", 1);

        places.deleteAllByUserId(유저);
        places.deleteAllByUserId(유저);
        assertThat(places.findByUserId(유저)).isEmpty();
        assertThat(places.findByUserId(다른유저)).hasSize(1);
    }

    @Test
    void 다건_삭제는_선택한_내_항목만_삭제하고_없는_항목은_무시한다() {
        Long 집 = 저장(유저, "집", 1);
        Long 회사 = 저장(유저, "회사", 2);
        Long 헬스장 = 저장(유저, "헬스장", 3);
        저장(다른유저, "집", 1);
        List<Long> 대상 = List.of(집, 회사, 없는ID);

        places.deleteByUserIdAndPlaceIdIn(유저, 대상);
        places.deleteByUserIdAndPlaceIdIn(유저, 대상);

        assertThat(places.findByUserId(유저)).extracting(FavoritePlace::getPlaceId)
                .containsExactly(헬스장);
        assertThat(places.findByUserId(다른유저)).hasSize(1);
    }
}
