package team.codingforest.moyeota.user.profile.domain;

import lombok.Getter;
import team.codingforest.moyeota.user.common.domain.PhoneNumber;
import team.codingforest.moyeota.user.common.domain.enums.Gender;

import java.time.Instant;

@Getter
public class UserProfile {
    private final Long userId;
    private final String name;
    private final Instant birthDate;
    private final String phoneNumber;
    private final Gender gender;
    private final String email;
    private final Instant createdAt;
    private Instant updatedAt;

    private UserProfile(Long userId, String name, Instant birthDate, String phoneNumber, Gender gender, String email, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.gender = gender;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserProfile of(Long userId, String name, Instant birthDate, String phoneNumber, Gender gender, String email) {
        return new UserProfile(userId, name, birthDate, new PhoneNumber(phoneNumber).value(), gender, email, Instant.now(), Instant.now());
    }

    public static UserProfile restore(Long userId, String name, Instant birthDate, String phoneNumber, Gender gender, String email, Instant createdAt, Instant updatedAt) {
        return new UserProfile(userId, name, birthDate, phoneNumber, gender, email, createdAt, updatedAt);
    }
}