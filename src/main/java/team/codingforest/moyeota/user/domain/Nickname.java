package team.codingforest.moyeota.user.domain;

import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

/** 동승자에게 보이는 이름. 2~10자, 한글·영문·숫자만. 앞뒤 공백은 제거한다 */
public record Nickname(String value) {
    public Nickname {
        if(value == null) throw new UserException(UserErrorCode.INVALID_NICKNAME);
        value = value.strip();
        if(!value.matches("^[가-힣a-zA-Z0-9]{2,10}$")) throw new UserException(UserErrorCode.INVALID_NICKNAME);
    }
}
