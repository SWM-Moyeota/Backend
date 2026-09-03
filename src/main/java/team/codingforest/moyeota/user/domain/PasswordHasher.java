package team.codingforest.moyeota.user.domain;

public interface PasswordHasher {
    String hash(String password);
    boolean matches(String rawPassword, String hashed);
}
