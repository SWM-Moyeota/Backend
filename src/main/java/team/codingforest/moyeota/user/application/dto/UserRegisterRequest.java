package team.codingforest.moyeota.user.application.dto;

import team.codingforest.moyeota.user.domain.enums.Gender;

import java.time.Instant;

public record UserRegisterRequest(String loginId, String password, String name, Instant birthDate, String phoneNumber, Gender gender, String email) {
}
