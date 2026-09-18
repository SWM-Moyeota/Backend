package team.codingforest.moyeota.rating.entity;

import java.io.Serializable;
import java.util.Objects;

public class RatingLogId implements Serializable {

    private Long matchId;
    private Long raterId;
    private Long rateeId;

    protected RatingLogId() {
    }

    public RatingLogId(Long matchId, Long raterId, Long rateeId) {
        this.matchId = matchId;
        this.raterId = raterId;
        this.rateeId = rateeId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof RatingLogId that)) {
            return false;
        }
        return Objects.equals(matchId, that.matchId)
                && Objects.equals(raterId, that.raterId)
                && Objects.equals(rateeId, that.rateeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, raterId, rateeId);
    }
}
