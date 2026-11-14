package team.codingforest.moyeota.user.social.dto;

import team.codingforest.moyeota.user.users.domain.enums.Gender;

import java.time.Instant;

public record SocialRegisterCommand(String signupToken, String verificationToken, String phoneNumber,
                                    String nickname, String name, Instant birthDate, Gender gender) {
}
