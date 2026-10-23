package team.codingforest.moyeota.matching.route.domain;

import java.util.List;

public interface WalkRouteFinder {
    List<WalkingEstimate> fromTo(Coordinate from, List<Coordinate> to);

    List<WalkingEstimate> toFrom(Coordinate to, List<Coordinate> from);
}
