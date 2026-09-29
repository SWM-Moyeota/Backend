package team.codingforest.moyeota.user.local.dto;

import team.codingforest.moyeota.user.common.domain.enums.Gender;

import java.time.Instant;

public record UserRegisterCommand(String loginId, String password, String nickname, String name,
                                  Instant birthDate, String phoneNumber, Gender gender, String email) {
}
