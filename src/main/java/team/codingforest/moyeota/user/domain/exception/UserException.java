package team.codingforest.moyeota.user.domain.exception;

import team.codingforest.moyeota.common.exception.BusinessException;

public class UserException extends BusinessException {
    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    @Override
    public UserErrorCode getErrorCode() {
        return (UserErrorCode) super.getErrorCode();
    }
}