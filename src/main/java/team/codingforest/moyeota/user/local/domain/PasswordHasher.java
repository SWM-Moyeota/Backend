package team.codingforest.moyeota.user.local.domain;

public interface PasswordHasher {
    String hash(String password);
    boolean matches(String rawPassword, String hashed);
}
