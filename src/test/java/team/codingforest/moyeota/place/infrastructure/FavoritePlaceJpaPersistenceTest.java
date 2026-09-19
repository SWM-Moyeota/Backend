package team.codingforest.moyeota.place.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.place.domain.FavoritePlace;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(FavoritePlaceJpa.class)
class FavoritePlaceJpaPersistenceTest {
    private static final Long 유저 = 1L;

    private final FavoritePlaceJpa places;
    private final FavoritePlaceJpaRepository repository;
    private final EntityManager em;

    @Autowired
    FavoritePlaceJpaPersistenceTest(FavoritePlaceJpa places, FavoritePlaceJpaRepository repository, EntityManager em) {
        this.places = places;
        this.repository = repository;
        this.em = em;
    }

    private FavoritePlace 장소(String name, int sequence) {
        return FavoritePlace.from(유저, name, "서울 강남구 테헤란로 152", 37.4979, 127.0276, sequence);
    }

    @Test
    void 저장_후_다시_조회해도_모든_필드가_그대로다() {
        places.save(장소("집", 1));
        em.flush();
        em.clear();

        FavoritePlace reloaded = places.findByUserIdAndPlaceName(유저, "집").orElseThrow();

        assertThat(reloaded.getRoadName()).isEqualTo("서울 강남구 테헤란로 152");
        assertThat(reloaded.getLatitude()).isEqualTo(37.4979);
        assertThat(reloaded.getLongitude()).isEqualTo(127.0276);
        assertThat(reloaded.getPlaceSequence()).isEqualTo(1);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void 목록은_sequence_오름차순으로_나온다() {
        places.save(장소("헬스장", 3));
        places.save(장소("집", 1));
        places.save(장소("회사", 2));
        em.flush();
        em.clear();

        assertThat(places.findByUserId(유저))
                .extracting(FavoritePlace::getPlaceName)
                .containsExactly("집", "회사", "헬스장");
    }

    @Test
    void 같은_이름으로_다시_저장하면_행이_늘지_않고_갱신되며_createdAt은_유지된다() {
        FavoritePlace saved = places.save(장소("집", 1));
        em.flush();
        em.clear();
        LocalDateTime createdAt = saved.getCreatedAt();

        FavoritePlace loaded = places.findByUserIdAndPlaceName(유저, "집").orElseThrow();
        loaded.updateLocation("서울 마포구 월드컵로 1", 37.55, 126.9);
        places.save(loaded);
        em.flush();
        em.clear();

        assertThat(repository.count()).isEqualTo(1);
        FavoritePlace reloaded = places.findByUserIdAndPlaceName(유저, "집").orElseThrow();
        assertThat(reloaded.getRoadName()).isEqualTo("서울 마포구 월드컵로 1");
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    void 이름_변경은_옛_행을_지우고_새_행을_넣는_방식으로_한_트랜잭션에서_된다() {
        places.save(장소("집", 1));
        em.flush();

        FavoritePlace loaded = places.findByUserIdAndPlaceName(유저, "집").orElseThrow();
        places.deleteByUserIdAndPlaceName(유저, "집");
        places.save(loaded.rename("본가"));
        em.flush();
        em.clear();

        assertThat(repository.count()).isEqualTo(1);
        assertThat(places.findByUserIdAndPlaceName(유저, "집")).isEmpty();
        FavoritePlace renamed = places.findByUserIdAndPlaceName(유저, "본가").orElseThrow();
        assertThat(renamed.getPlaceSequence()).isEqualTo(1);
    }

    @Test
    void saveAll로_순서를_한꺼번에_바꿀_수_있다() {
        places.save(장소("집", 1));
        places.save(장소("회사", 2));
        em.flush();
        em.clear();

        List<FavoritePlace> list = places.findByUserId(유저);
        list.get(0).updateSequence(2);
        list.get(1).updateSequence(1);
        places.saveAll(list);
        em.flush();
        em.clear();

        assertThat(places.findByUserId(유저))
                .extracting(FavoritePlace::getPlaceName)
                .containsExactly("회사", "집");
    }

    @Test
    void 삭제하면_그_유저의_그_이름만_사라진다() {
        places.save(장소("집", 1));
        places.save(FavoritePlace.from(2L, "집", "주소", 37.5, 127.0, 1));
        em.flush();

        places.deleteByUserIdAndPlaceName(유저, "집");
        em.clear();

        assertThat(places.existsByUserIdAndPlace(유저, "집")).isFalse();
        assertThat(places.existsByUserIdAndPlace(2L, "집")).isTrue();
    }
}
