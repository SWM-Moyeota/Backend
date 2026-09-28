package team.codingforest.moyeota.chat.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatRoomEntity;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    Optional<ChatRoomEntity> findByPartyId(Long partyId);
    boolean existsByPartyId(Long partyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoomEntity r where r.partyId = :partyId")
    Optional<ChatRoomEntity> findByPartyIdForUpdate(@Param("partyId") Long partyId);
}
