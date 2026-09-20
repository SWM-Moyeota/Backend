package team.codingforest.moyeota.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerifiedIdentityRepository extends JpaRepository<VerifiedIdentityEntity, Long> {
    boolean existsByDiHash(String diHash);
}
