package team.codingforest.moyeota.chat.member.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.member.domain.ReadPosition;
import team.codingforest.moyeota.chat.member.domain.ReadPositions;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Repository
@RequiredArgsConstructor
public class ChatReadPositionJpa implements ReadPositions {

    private static final int CHUNK = 1000;

    private final ChatRoomUserJpaRepository jpaRepository;

    @Override
    @Transactional
    public int advance(List<ReadPosition> positions) {
        List<ReadPosition> sorted = positions.stream()
                .sorted(Comparator.comparing(ReadPosition::chatRoomId).thenComparing(ReadPosition::userId))
                .toList();

        int updated = 0;

        for (int from = 0; from < sorted.size(); from += CHUNK) {
            List<ReadPosition> chunk = sorted.subList(from, Math.min(from + CHUNK, sorted.size()));
            updated += jpaRepository.advanceReadPositions(
                    ids(chunk, ReadPosition::chatRoomId),
                    ids(chunk, ReadPosition::userId),
                    ids(chunk, ReadPosition::messageId));
        }
        return updated;
    }

    private static Long[] ids(List<ReadPosition> chunk, Function<ReadPosition, Long> field) {
        return chunk.stream().map(field).toArray(Long[]::new);
    }
}