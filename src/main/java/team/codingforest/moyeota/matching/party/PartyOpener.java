package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyConflictRule;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.OpenReservationCommand;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartyOpener {

	private final Parties parties;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public PartyResult open(OpenPartyCommand command, RouteEstimate estimate) {
		parties.lockMember(command.creatorId());
		PartyConflictRule.check(parties, command.creatorId(), Instant.now());

		Party party = Party.create(command.creatorId(),
				new Location(command.departureLat(), command.departureLng()),
				new Location(command.destinationLat(), command.destinationLng()),
				command.departure(), command.destination(), new Capacity(command.capacity()),
				Instant.now(), estimate.estimateFare(), estimate.estimateTime(), estimate.path());

		Party saved = parties.save(party);

		eventPublisher.publishEvent(new PartyMemberJoinedEvent(saved.getId(), command.creatorId()));
		log.info("매칭방 활성화. partyId={}, creatorId={}, capacity={}, status={}", saved.getId(), command.creatorId(), saved.getCapacity().value(), saved.getStatus());

		return PartyResult.from(saved);
	}

	@Transactional
	public PartyResult reserve(OpenReservationCommand command) {
		parties.lockMember(command.creatorId());
		PartyConflictRule.check(parties, command.creatorId(), command.departureAt());

		Party party = Party.reserve(command.creatorId(),
				new Location(command.departureLat(), command.departureLng()),
				new Location(command.destinationLat(), command.destinationLng()),
				command.departure(), command.destination(), new Capacity(command.capacity()),
				Instant.now(), command.departureAt());

		Party saved = parties.save(party);

		eventPublisher.publishEvent(new PartyMemberJoinedEvent(saved.getId(), command.creatorId()));
		log.info("예약 방 생성. partyId={}, creatorId={}, capacity={}, departureAt={}", saved.getId(), command.creatorId(), saved.getCapacity().value(), saved.getDepartureAt());

		return PartyResult.from(saved);
	}
}
