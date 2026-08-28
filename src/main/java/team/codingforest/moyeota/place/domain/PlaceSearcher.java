package team.codingforest.moyeota.place.domain;

import java.util.List;

public interface PlaceSearcher {
    List<Place> search(String query);
}
