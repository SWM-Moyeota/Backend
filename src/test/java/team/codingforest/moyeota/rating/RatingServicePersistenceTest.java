package team.codingforest.moyeota.rating;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.rating.dto.RatingCreateRequest;
import team.codingforest.moyeota.rating.entity.RatingLogId;
import team.codingforest.moyeota.rating.entity.RatingType;
import team.codingforest.moyeota.rating.entity.UserRating;
import team.codingforest.moyeota.rating.entity.UserRatingId;
import team.codingforest.moyeota.rating.exception.RatingErrorCode;
import team.codingforest.moyeota.rating.repository.RatingLogRepository;
import team.codingforest.moyeota.rating.repository.UserRatingRepository;
import team.codingforest.moyeota.rating.service.RatingService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class RatingServicePersistenceTest {

    @Autowired
    private RatingLogRepository ratingLogRepository;

    @Autowired
    private UserRatingRepository userRatingRepository;

    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(ratingLogRepository, userRatingRepository);
    }

    @Test
    void 평점_생성_수정_삭제에_따라_사용자_집계가_갱신된다() {
        ratingService.create(1L, request(10L, 3L, 5));
        ratingService.create(2L, request(11L, 3L, 4));

        assertSummary(3L, 9, 2, "4.50");

        ratingService.update(1L, 10L, 3L, 1);
        assertSummary(3L, 5, 2, "2.50");

        ratingService.delete(2L, 11L, 3L);
        assertSummary(3L, 1, 1, "1.00");

        ratingService.delete(1L, 10L, 3L);
        assertThat(userRatingRepository.findById(new UserRatingId(3L, RatingType.PASSENGER))).isEmpty();
    }

    @Test
    void 같은_매칭과_평가자와_대상으로는_중복_평점을_등록할_수_없다() {
        RatingCreateRequest request = request(10L, 3L, 5);
        ratingService.create(1L, request);

        assertThatThrownBy(() -> ratingService.create(1L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(RatingErrorCode.RATING_ALREADY_EXISTS);
    }

    @Test
    void 다른_사용자는_등록자의_평점을_수정하거나_삭제할_수_없다() {
        ratingService.create(1L, request(10L, 3L, 5));

        assertThatThrownBy(() -> ratingService.update(2L, 10L, 3L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(RatingErrorCode.RATING_NOT_FOUND);
        assertThatThrownBy(() -> ratingService.delete(2L, 10L, 3L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(RatingErrorCode.RATING_NOT_FOUND);

        assertThat(ratingLogRepository.findById(new RatingLogId(10L, 1L, 3L))).isPresent();
    }

    private RatingCreateRequest request(Long matchId, Long rateeId, int rating) {
        return new RatingCreateRequest(matchId, rateeId, RatingType.PASSENGER, rating);
    }

    private void assertSummary(Long userId, int sum, int count, String average) {
        UserRating summary = userRatingRepository
                .findById(new UserRatingId(userId, RatingType.PASSENGER))
                .orElseThrow();

        assertThat(summary.getRatingSum()).isEqualTo(sum);
        assertThat(summary.getTotalRatings()).isEqualTo(count);
        assertThat(summary.getAvgRating()).isEqualByComparingTo(new BigDecimal(average));
    }
}
