package team.codingforest.moyeota.place.favorite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;
import team.codingforest.moyeota.place.favorite.infrastructure.FavoritePlaceJpa;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(FavoritePlaceJpa.class)
class FavoritePlaceJpaTest {

    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;

    private final FavoritePlaceJpa places;

    @Autowired
    FavoritePlaceJpaTest(FavoritePlaceJpa places) {
        this.places = places;
    }

    private FavoritePlace 장소(Long userId, String name, int sequence) {
        return FavoritePlace.from(userId, name, "서울 강남구 테헤란로 123", 37.5013, 127.0396, sequence);
    }

    @Test
    void 복합키로_저장하고_다시_읽을_수_있다() {
        // #231: FavoritePlaceId 에 무인자 생성자가 없어 저장이 500 으로 떨어지던 문제
        places.save(장소(유저, "집", 1));
        places.save(장소(유저, "회사/본점", 2));

        assertThat(places.findByUserId(유저))
                .extracting(FavoritePlace::getPlaceName)
                .containsExactlyInAnyOrder("집", "회사/본점");
        assertThat(places.existsByUserIdAndPlace(유저, "집")).isTrue();
    }

    @Test
    void 한_건_삭제는_지운_건수를_돌려준다() {
        places.save(장소(유저, "집", 1));

        assertThat(places.deleteByUserIdAndPlaceName(유저, "집")).isEqualTo(1);
        assertThat(places.deleteByUserIdAndPlaceName(유저, "집")).isEqualTo(0);
        assertThat(places.existsByUserIdAndPlace(유저, "집")).isFalse();
    }

    @Test
    void 다른_유저의_같은_이름은_지워지지_않는다() {
        places.save(장소(다른유저, "집", 1));

        assertThat(places.deleteByUserIdAndPlaceName(유저, "집")).isEqualTo(0);
        assertThat(places.existsByUserIdAndPlace(다른유저, "집")).isTrue();
    }

    @Test
    void 전체_삭제는_내_것만_지우고_건수를_돌려준다() {
        places.save(장소(유저, "집", 1));
        places.save(장소(유저, "회사", 2));
        places.save(장소(다른유저, "집", 1));

        assertThat(places.deleteAllByUserId(유저)).isEqualTo(2);
        assertThat(places.deleteAllByUserId(유저)).isEqualTo(0);
        assertThat(places.findByUserId(유저)).isEmpty();
        assertThat(places.findByUserId(다른유저)).hasSize(1);
    }
}
