package team.codingforest.moyeota.user.local.domain;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import team.codingforest.moyeota.user.common.exception.*;
public final class PasswordPolicy {
    private static final Pattern COMPLEXITY = Pattern.compile(
        "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+$"
    );

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null
                || password.length() < 8
                || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !COMPLEXITY.matcher(password).matches()) {
            throw new UserException(UserErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }

    public static void validateConfirmation(String password, String confirm) {
        validate(password);
        if (!password.equals(confirm)) {
            throw new UserException(UserErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
    }
}
