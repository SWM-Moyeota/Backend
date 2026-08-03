package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import lombok.Getter;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.domain.*;

import java.time.Instant;
import java.util.List;

@Entity
@Getter
@Table(name = "match_room")
public class PartyEntity extends BaseTimeEntity {

    private Long hostId;
    private Double departureLocation;
    private Double destinationLocation;
    private String departure;
    private String destination;
    private Integer capacity;
    private Integer departureRadius;
    private Integer destinationRadius;
    private List<PartyMember> members;
    private PartyStatus status;
}
