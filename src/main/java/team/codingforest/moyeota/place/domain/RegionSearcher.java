package team.codingforest.moyeota.place.domain;

import java.util.Optional;

public interface RegionSearcher {
    Optional<Address> find(double latitude, double longitude);
}