package team.codingforest.moyeota.user.passwordreset.infrastructure;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import team.codingforest.moyeota.user.passwordreset.domain.ResetGrant;

@Entity @Table(name="password_reset_grant")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ResetGrantEntity {
 @Id private UUID id;
 @Column(nullable=false, unique=true, length=64) private String tokenHash;
 @Column(nullable=false) private Long userId;
 @Column(nullable=false, length=64) private String phoneHash;
 @Column(name="reset_generation", nullable=false) private long generation;
 @Column(nullable=false) private Instant expiresAt;
 private Instant consumedAt;
 @Column(nullable=false) private Instant createdAt;
 public static ResetGrantEntity from(ResetGrant g) {
   ResetGrantEntity e = new ResetGrantEntity();
   e.id=g.id(); e.tokenHash=g.tokenHash(); e.userId=g.userId(); e.phoneHash=g.phoneHash();
   e.generation=g.generation(); e.expiresAt=g.expiresAt(); e.consumedAt=g.consumedAt(); e.createdAt=g.createdAt();
   return e;
 }
 public ResetGrant toDomain() {
   return new ResetGrant(id,tokenHash,userId,phoneHash,generation,expiresAt,consumedAt,createdAt);
 }
}
