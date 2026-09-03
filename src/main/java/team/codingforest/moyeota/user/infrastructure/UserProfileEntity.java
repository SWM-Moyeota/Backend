package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.user.domain.UserProfile;
import team.codingforest.moyeota.user.domain.enums.Gender;

import java.time.Instant;

@Entity
@NoArgsConstructor
public class UserProfileEntity {

    @Id
    @Column(nullable = false)
    private Long id;

    private String passCi;

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

    private UserProfileEntity(Long id, String passCi, String name, Instant birthDate, String phoneNumber, Gender gender, String email, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.passCi = passCi;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.gender = gender;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserProfileEntity from(UserProfile user) {
        return new UserProfileEntity(user.getUserId(), user.getPassCi(), user.getName(), user.getBirthDate(), user.getPhoneNumber(), user.getGender(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public UserProfile toDomain() {
        return UserProfile.restore(
                id,
                passCi,
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
