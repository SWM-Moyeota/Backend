package team.codingforest.moyeota.user.domain;

import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

public record PhoneNumber(String value) {
    public PhoneNumber {
        if(value == null) throw new UserException(UserErrorCode.INVALID_PHONE_NUMBER);

        value = value.replace("-", "").strip();

        if(!value.matches("^01[016-9]\\d{7,8}$")) throw new UserException(UserErrorCode.INVALID_PHONE_NUMBER);
    }
}