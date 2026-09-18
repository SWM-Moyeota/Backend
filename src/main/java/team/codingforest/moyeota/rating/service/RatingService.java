package team.codingforest.moyeota.rating.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.rating.dto.RatingCreateRequest;
import team.codingforest.moyeota.rating.dto.RatingResponse;
import team.codingforest.moyeota.rating.dto.UserRatingResponse;
import team.codingforest.moyeota.rating.entity.RatingLog;
import team.codingforest.moyeota.rating.entity.RatingType;
import team.codingforest.moyeota.rating.entity.UserRating;
import team.codingforest.moyeota.rating.entity.UserRatingId;
import team.codingforest.moyeota.rating.exception.RatingErrorCode;
import team.codingforest.moyeota.rating.repository.RatingLogRepository;
import team.codingforest.moyeota.rating.repository.UserRatingRepository;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingLogRepository ratingLogRepository;
    private final UserRatingRepository userRatingRepository;

    @Transactional
    public RatingResponse create(Long raterId, RatingCreateRequest request) {
        validateNotSelfRating(raterId, request.rateeId());
        validateRating(request.rating());

        if (ratingLogRepository.existsByMatchIdAndRaterIdAndRateeId(
                request.matchId(), raterId, request.rateeId())) {
            throw new BusinessException(RatingErrorCode.RATING_ALREADY_EXISTS);
        }

        byte ratingValue = request.rating().byteValue();
        RatingLog ratingLog = new RatingLog(
                request.matchId(), raterId, request.rateeId(), request.ratingType(), ratingValue
        );
        ratingLog = ratingLogRepository.saveAndFlush(ratingLog);

        UserRating userRating = userRatingRepository
                .findForUpdate(request.rateeId(), request.ratingType())
                .orElseGet(() -> new UserRating(request.rateeId(), request.ratingType()));
        userRating.add(ratingValue);
        userRatingRepository.save(userRating);

        return RatingResponse.from(ratingLog);
    }

    @Transactional(readOnly = true)
    public RatingResponse read(Long raterId, Long matchId, Long rateeId) {
        return ratingLogRepository.findByMatchIdAndRaterIdAndRateeId(matchId, raterId, rateeId)
                .map(RatingResponse::from)
                .orElseThrow(() -> new BusinessException(RatingErrorCode.RATING_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserRatingResponse readUserRating(Long userId, RatingType ratingType) {
        return userRatingRepository.findById(new UserRatingId(userId, ratingType))
                .map(UserRatingResponse::from)
                .orElseGet(() -> UserRatingResponse.empty(userId, ratingType));
    }

    @Transactional
    public RatingResponse update(Long raterId, Long matchId, Long rateeId, int newRating) {
        validateRating(newRating);
        RatingLog ratingLog = findRatingForUpdate(matchId, raterId, rateeId);
        byte previousRating = ratingLog.getRating();
        byte newRatingValue = (byte) newRating;

        UserRating userRating = findUserRatingForUpdate(rateeId, ratingLog.getRatingType());
        ratingLog.updateRating(newRatingValue);
        userRating.replace(previousRating, newRatingValue);
        ratingLogRepository.flush();

        return RatingResponse.from(ratingLog);
    }

    @Transactional
    public void delete(Long raterId, Long matchId, Long rateeId) {
        RatingLog ratingLog = findRatingForUpdate(matchId, raterId, rateeId);
        UserRating userRating = findUserRatingForUpdate(rateeId, ratingLog.getRatingType());

        userRating.remove(ratingLog.getRating());
        ratingLogRepository.delete(ratingLog);

        if (userRating.isEmpty()) {
            userRatingRepository.delete(userRating);
        }
    }

    private RatingLog findRatingForUpdate(Long matchId, Long raterId, Long rateeId) {
        return ratingLogRepository.findForUpdate(matchId, raterId, rateeId)
                .orElseThrow(() -> new BusinessException(RatingErrorCode.RATING_NOT_FOUND));
    }

    private UserRating findUserRatingForUpdate(Long rateeId, RatingType ratingType) {
        return userRatingRepository.findForUpdate(rateeId, ratingType)
                .orElseThrow(() -> new BusinessException(RatingErrorCode.RATING_SUMMARY_NOT_FOUND));
    }

    private void validateNotSelfRating(Long raterId, Long rateeId) {
        if (raterId.equals(rateeId)) {
            throw new BusinessException(RatingErrorCode.SELF_RATING_NOT_ALLOWED);
        }
    }

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException(RatingErrorCode.INVALID_RATING);
        }
    }
}
