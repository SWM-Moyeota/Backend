package team.codingforest.moyeota.chat.room.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.chat.room.infrastructure.entity.ChatRoomEntity;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    Optional<ChatRoomEntity> findByPartyId(Long partyId);
    boolean existsByPartyId(Long partyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoomEntity r where r.partyId = :partyId")
    Optional<ChatRoomEntity> findByPartyIdForUpdate(@Param("partyId") Long partyId);

    @Modifying(flushAutomatically = true)
    @Query("update ChatRoomEntity r set r.lastSeq = r.lastSeq + 1 where r.id = :id")
    int incrementLastSeq(@Param("id") Long id);

    @Query("select r.lastSeq from ChatRoomEntity r where r.id = :id")
    long findLastSeq(@Param("id") Long id);
}
