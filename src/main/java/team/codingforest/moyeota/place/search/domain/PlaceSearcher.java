package team.codingforest.moyeota.place.search.domain;

import java.util.List;

public interface PlaceSearcher {
    List<Place> search(String query);
}
