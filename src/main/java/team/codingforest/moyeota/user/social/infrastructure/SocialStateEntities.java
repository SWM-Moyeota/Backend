package team.codingforest.moyeota.user.social.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 로컬 H2 자동 DDL과 운영 Flyway 스키마 검증을 위한 매핑. 실제 일회성 상태 전이는 JDBC로 수행한다. */
@Entity @Table(name="social_proof")
class SocialProofEntity {
    @Id @Column(length=64) String tokenHash;
    @Column(nullable=false,length=30) String purpose;
    Long userId;
    @Column(nullable=false,name="proof_value") String value;
    @Column(nullable=false) Instant expiresAt;
}
@Entity @Table(name="apple_credential")
class AppleCredentialEntity {
    @Id UUID id;
    @Column(nullable=false) String subject;
    @Column(nullable=false) String clientId;
    @Column(nullable=false,columnDefinition="text") String tokenCipher;
    Long userId;
    String signupToken;
    Instant expiresAt;
    @Column(nullable=false) Instant verifiedAt;
    @Column(nullable=false,length=20) String state;
    @Column(nullable=false,columnDefinition="integer default 0") int attempts;
    @Column(nullable=false,columnDefinition="timestamp with time zone default CURRENT_TIMESTAMP") Instant retryAt;
}
@Entity @Table(name="apple_notification")
class AppleNotificationEntity {
    @Id String eventId;
    @Column(nullable=false) Instant receivedAt;
}
@Entity @Table(name="user_session_revocation")
class UserSessionRevocationEntity {
    @Id Long userId;
    @Column(nullable=false) Instant revokedAt;
}

@Entity @Table(name="apple_grant_lock")
class AppleGrantLockEntity { @Id String subject; Instant revokedAt; }
