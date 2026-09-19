package team.codingforest.moyeota.place.infrastructure;

import java.io.Serializable;
import java.util.Objects;

public class FavoritePlaceId implements Serializable {
    private Long userId;
    private String placeName;

    /** JPA(@IdClass) 가 리플렉션으로 만들 때 필요. 이 생성자가 없으면 save 시점에 InstantiationException 이 난다. */
    protected FavoritePlaceId() {
    }

    public FavoritePlaceId(Long userId, String placeName) {
        this.userId = userId;
        this.placeName = placeName;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(!(o instanceof FavoritePlaceId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(placeName, that.placeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, placeName);
    }
}
