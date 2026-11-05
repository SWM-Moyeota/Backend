package team.codingforest.moyeota.user.sanction.domain;

import java.util.Optional;

/**
 *  노쇼 제재 저장소 - users 행의 제재 컬럼(restricted_until)만 읽고 쓴다.
 *  프로필 편집(Users.save)과 컬럼이 겹치지 않아 서로의 변경을 덮어쓰지 않는다
 */
public interface NoShowSanctions {
    /** 노쇼를 기록·취소하기 전에 읽는다 - users 행을 잠가 같은 사용자의 노쇼 두 건이 동시에 기록돼도 서로를 세게 한다. 사용자가 없으면 empty */
    Optional<NoShowSanction> findForUpdate(Long userId);

    /** 사용자가 없으면 empty */
    Optional<NoShowSanction> find(Long userId);

    /** 사용자가 없으면 USER_NOT_FOUND */
    void save(Long userId, NoShowSanction sanction);
}
