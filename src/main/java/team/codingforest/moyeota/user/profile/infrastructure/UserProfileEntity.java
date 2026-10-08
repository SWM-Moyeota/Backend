package team.codingforest.moyeota.user.profile.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.user.common.domain.enums.Gender;
import team.codingforest.moyeota.user.profile.domain.UserProfile;

import java.time.Instant;

@Entity
@Getter
@Table(name = "user_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileEntity {

    @Id
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant birthDate;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private UserProfileEntity(Long id, String name, Instant birthDate, String phoneNumber, Gender gender, String email, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.gender = gender;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserProfileEntity from(UserProfile user) {
        return new UserProfileEntity(user.getUserId(), user.getName(), user.getBirthDate(), user.getPhoneNumber(), user.getGender(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public UserProfile toDomain() {
        return UserProfile.restore(
                id,
                name,
                birthDate,
                phoneNumber,
                gender,
                email,
                createdAt,
                updatedAt
        );
    }
}
